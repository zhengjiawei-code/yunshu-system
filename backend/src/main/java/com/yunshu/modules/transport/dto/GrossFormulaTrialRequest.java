package com.yunshu.modules.transport.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

/** 单票试算（#40）：可传公式草稿，不落库；样例为已配载票。 */
@Data
public class GrossFormulaTrialRequest {

    @Valid
    private GrossFormulaSaveRequest formula;

    private Long tripWaybillId;

    private Long waybillId;

    private Long tripId;
}
