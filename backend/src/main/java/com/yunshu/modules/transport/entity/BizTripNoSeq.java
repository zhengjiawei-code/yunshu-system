package com.yunshu.modules.transport.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 车次号日流水，对应 biz_trip_no_seq（V28）；按自然日一行，原子自增，数据权限豁免。
 */
@Data
@TableName("biz_trip_no_seq")
public class BizTripNoSeq {

    @TableId
    private LocalDate seqDate;

    private Integer currentVal;

    private LocalDateTime updateTime;
}
