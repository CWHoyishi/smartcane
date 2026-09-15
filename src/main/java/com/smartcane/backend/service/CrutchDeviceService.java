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

    Result<Void> delete(Long id);
}
