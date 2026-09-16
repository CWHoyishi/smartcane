-- 迭代 5：告警闭环 —— 告警记录表
-- 目标库：smartcane（MySQL 8）
--
-- 建表原因：原先「是否告警」只体现在 t_crutch_sensor_data.fall_status 上，
-- 没有处置状态，无法回答「这条摔倒有没有人看过」。本表把告警抽成独立事件，
-- 一条采样最多产生 3 条告警（摔倒 / 心率 / 血氧），并记录处置结果。
--
-- 唯一键 (device_sn, alarm_type, report_time) 与采样表同款口径：
-- 双通道重复上报不会重复生成告警，应用侧命中冲突直接忽略。

-- ============ 第 1 步：建表 ============
CREATE TABLE IF NOT EXISTS t_alarm_record
(
    id          bigint unsigned auto_increment comment '主键ID'
        primary key,
    device_sn   varchar(64)                        not null comment '关联拐杖设备序列号',
    alarm_type  varchar(32)                        not null comment '告警类型：FALL摔倒 HEART_RATE心率异常 BLOOD_OXYGEN血氧异常',
    level       tinyint  default 2                 not null comment '告警级别：2重要 3紧急',
    alarm_value varchar(64) default ''             null comment '触发告警的指标值，用于回溯判定依据，如 heartRate=45',
    report_time datetime default CURRENT_TIMESTAMP not null comment '触发告警的采样时间',
    status      tinyint  default 0                 not null comment '处理状态：0待处理 1已确认 2误报',
    handled_by  varchar(32)                        null comment '处置人',
    handle_time datetime                           null comment '处置时间',
    handle_note varchar(255)                       null comment '处置备注',
    create_time datetime default CURRENT_TIMESTAMP not null comment '告警产生时间',
    constraint uk_device_alarm
        unique (device_sn, alarm_type, report_time),
    constraint fk_alarm_dev
        foreign key (device_sn) references t_crutch_device (device_sn)
            on delete cascade
)
    comment '智能拐杖告警记录表';

-- ============ 第 2 步：索引 ============
-- 待处理列表按 (status, report_time) 走索引，避免全表扫描后排序
CREATE INDEX idx_alarm_status_time
    ON t_alarm_record (status, report_time);

-- ============ 第 3 步：验证 ============
SHOW INDEX FROM t_alarm_record;

SELECT COUNT(*) AS pending_count FROM t_alarm_record WHERE status = 0;

-- 补充说明：
-- 1) 本表无历史数据，建唯一键不需要像迭代 3 那样先查重。
-- 2) 外键沿用 t_crutch_sensor_data 的口径（ON DELETE CASCADE）：
--    当前删除设备会连带清空该设备告警，属迭代 7「设备删除保护」要一起处理的问题。
-- 3) 告警判定阈值固化在 AlarmEvaluator（心率 50~120、血氧 >= 90、fall_status=1 紧急）。