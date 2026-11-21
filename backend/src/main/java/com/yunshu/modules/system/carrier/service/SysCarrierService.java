package com.yunshu.modules.system.carrier.service;

import com.yunshu.modules.system.carrier.dto.CarrierQuery;
import com.yunshu.modules.system.carrier.dto.CarrierSaveRequest;
import com.yunshu.modules.system.carrier.vo.CarrierPageVO;
import com.yunshu.modules.system.carrier.vo.CarrierVO;

import java.util.List;

public interface SysCarrierService {

    CarrierPageVO page(CarrierQuery query);

    List<CarrierVO> options();

    CarrierVO detail(Long id);

    Long create(CarrierSaveRequest request);

    void update(Long id, CarrierSaveRequest request);

    void delete(Long id);
}
