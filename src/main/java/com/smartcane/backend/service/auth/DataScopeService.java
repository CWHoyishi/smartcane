package com.smartcane.backend.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcane.backend.exception.NoPermissionException;
import com.smartcane.backend.mapper.UserDeviceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据范围校验：监护人只能访问自己绑定的设备。
 *
 * 用法：
 *   - 单设备接口：assertDeviceAccess(deviceSn)
 *   - 不带 deviceSn 的列表接口：applyDeviceScope(wrapper, "device_sn")
 *   - 管理员专属操作：assertAdmin()
 *
 * 管理员一律放行；监护人未绑定任何设备时列表返回空集，而不是退化成查全部。
 */
@Service
public class DataScopeService {

    @Autowired
    private UserDeviceMapper userDeviceMapper;

    /**
     * 当前登录人绑定的设备；管理员返回 null 表示不加限制。
     *
     * 未登录时直接拒绝（失败关闭）：所有数据接口都应经过鉴权拦截器，
     * 走到这里还没有身份说明有路径漏配，此时放行等于把数据全暴露出去。
     */
    public List<String> boundDeviceSns() {
        LoginUser user = AuthContext.get();
        if (user == null) {
            throw new NoPermissionException("未登录或登录已过期");
        }
        if (user.isAdmin()) {
            return null;
        }
        return userDeviceMapper.selectDeviceSnsByUserId(user.getUserId());
    }

    /** 单设备接口的归属校验，越权直接抛 403 */
    public void assertDeviceAccess(String deviceSn) {
        List<String> bound = boundDeviceSns();
        if (bound == null) {
            return;
        }
        if (deviceSn == null || deviceSn.isEmpty() || !bound.contains(deviceSn)) {
            throw new NoPermissionException("无权访问该设备");
        }
    }

    /** 列表接口按绑定关系收窄；管理员不加条件 */
    public <T> void applyDeviceScope(QueryWrapper<T> wrapper, String column) {
        List<String> bound = boundDeviceSns();
        if (bound == null) {
            return;
        }
        if (bound.isEmpty()) {
            // 恒假条件：没绑定设备的人不应该看到任何数据
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in(column, bound);
    }

    /** 管理员专属操作（设备增删改、OneNet 密钥查看、统计重算等） */
    public void assertAdmin() {
        LoginUser user = AuthContext.get();
        if (user == null || !user.isAdmin()) {
            throw new NoPermissionException("该操作仅管理员可用");
        }
    }
}
