package com.smartcane.backend.service;

import com.smartcane.backend.entity.dto.CrutchDeviceDTO;
import com.smartcane.backend.entity.vo.CrutchDeviceVO;
import com.smartcane.backend.entity.vo.Result;

import java.util.List;

public interface CrutchDeviceService {

    Result<List<CrutchDeviceVO>> list();

    Result<CrutchDeviceVO> getById(Long id);

    Result<CrutchDeviceVO> getByDeviceSn(String deviceSn);

    Result<Void> add(CrutchDeviceDTO dto);

    Result<Void> update(CrutchDeviceDTO dto);

    /**
     * 删除设备。
     *
     * @param force 设备存在历史采样/告警时必须显式传 true：外键是 ON DELETE CASCADE，
     *              删除设备会一并清空该设备的全部历史数据
     */
    Result<Void> delete(Long id, boolean force);
}
