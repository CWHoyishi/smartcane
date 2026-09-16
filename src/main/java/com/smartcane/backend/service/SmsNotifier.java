package com.smartcane.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcane.backend.entity.po.AlarmRecord;
import com.smartcane.backend.entity.po.CrutchDevice;
import com.smartcane.backend.mapper.CrutchDeviceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警短信通知：把告警记录翻成短信发给监护人。
 *
 * 两道兜底，避免反复打扰家属：
 * 1) 同设备同类型告警有冷却窗口，窗口内只发第一条（摔倒状态持续时，每条新采样都会产生新告警）；
 * 2) 单条短信最多重试若干次，全部失败只记错误日志。
 *
 * 通知是同步动作：MockSmsSender 不耗时，接入真实厂商（HTTP 调用数百毫秒）后
 * 应改由迭代 2 的消息队列异步消费，不要再占用采集线程。
 */
@Component
public class SmsNotifier {

    private static final Logger log = LoggerFactory.getLogger(SmsNotifier.class);

    /** 同设备同类型告警的短信冷却时间（毫秒）：5 分钟 */
    private static final long COOLDOWN_MS = 5 * 60 * 1000L;

    /** 单条短信最大发送尝试次数 */
    private static final int MAX_ATTEMPTS = 3;

    /** 重试间隔（毫秒） */
    private static final long RETRY_INTERVAL_MS = 1000L;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 冷却窗口状态：key = 设备号:告警类型，value = 上次真正发出的时间戳。
     * 只放内存即可——重启后窗口重置，代价是多发一条短信，不值得为此引入 Redis。
     */
    private final Map<String, Long> lastSendTime = new ConcurrentHashMap<>();

    @Autowired
    private CrutchDeviceMapper deviceMapper;

    @Autowired
    private SmsSender smsSender;

    /**
     * 发送告警短信，接收方取设备绑定的监护人电话。
     * 缺少电话、设备不存在、处于冷却期都不发，只记日志。
     */
    public void notify(AlarmRecord record) {
        if (record == null || !StringUtils.hasText(record.getDeviceSn())) {
            return;
        }
        CrutchDevice device = selectDevice(record.getDeviceSn());
        if (device == null) {
            log.warn("[短信] 设备 {} 不存在，跳过通知", record.getDeviceSn());
            return;
        }
        String phone = device.getGuardianPhone();
        if (!StringUtils.hasText(phone)) {
            log.warn("[短信] 设备 {} 未绑定监护人电话，跳过通知", record.getDeviceSn());
            return;
        }
        if (isCoolingDown(record)) {
            log.info("[短信] 设备 {} 的 {} 告警处于冷却期（{} 分钟内已通知过），本次不发",
                    record.getDeviceSn(), record.getAlarmType(), COOLDOWN_MS / 60000);
            return;
        }

        String content = buildContent(device, record);
        if (sendWithRetry(phone, content)) {
            lastSendTime.put(cooldownKey(record), System.currentTimeMillis());
        }
    }

    private CrutchDevice selectDevice(String deviceSn) {
        QueryWrapper<CrutchDevice> wrapper = new QueryWrapper<>();
        wrapper.eq("device_sn", deviceSn);
        return deviceMapper.selectOne(wrapper);
    }

    private String buildContent(CrutchDevice device, AlarmRecord record) {
        String elder = StringUtils.hasText(device.getElderName()) ? device.getElderName() : "未命名老人";
        String time = record.getReportTime() == null ? "" : TIME_FORMAT.format(record.getReportTime());
        return String.format("【智能手杖告警】%s（设备 %s）于 %s %s，请及时联系确认。",
                elder, record.getDeviceSn(), time, describeAlarm(record));
    }

    private String describeAlarm(AlarmRecord record) {
        if (AlarmEvaluator.TYPE_FALL.equals(record.getAlarmType())) {
            return "检测到跌倒";
        }
        // 现在只有摔倒会告警，这里是历史遗留类型（心率/血氧）的兜底
        return "触发告警（" + record.getAlarmType() + "）";
    }

    private boolean isCoolingDown(AlarmRecord record) {
        Long last = lastSendTime.get(cooldownKey(record));
        return last != null && System.currentTimeMillis() - last < COOLDOWN_MS;
    }

    private String cooldownKey(AlarmRecord record) {
        return record.getDeviceSn() + ":" + record.getAlarmType();
    }

    private boolean sendWithRetry(String phone, String content) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                smsSender.send(phone, content);
                return true;
            } catch (Exception e) {
                log.warn("[短信] 第 {}/{} 次发送失败（{}）: {}", attempt, MAX_ATTEMPTS, phone, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("[短信] 重试等待被中断，放弃发送（{}）", phone);
                        return false;
                    }
                }
            }
        }
        log.error("[短信] 发送失败，已重试 {} 次（{}）", MAX_ATTEMPTS, phone);
        return false;
    }
}