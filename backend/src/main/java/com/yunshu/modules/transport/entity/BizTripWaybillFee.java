package com.yunshu.modules.transport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用分摊明细，对应 biz_trip_waybill_fee（V29）。
 * 每个配载单元 × 每个成本项一行，是逐票毛利与车次对账的唯一取数来源。
 */
@Data
@TableName("biz_trip_waybill_fee")
public class BizTripWaybillFee {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tripId;

    private Long tripWaybillId;

    private Long waybillId;

    private String feeCode;

    private String feeName;

    /** 1=重量 2=体积 3=重泡比计费重量 */
    private Integer allocMethod;

    private BigDecimal basisValue;

    private BigDecimal basisTotal;

    private BigDecimal allocRatio;

    private BigDecimal amount;

    private Long deptId;

    private LocalDateTime createTime;
}
