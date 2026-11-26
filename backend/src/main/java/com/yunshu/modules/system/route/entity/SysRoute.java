package com.yunshu.modules.system.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 短驳/干线路由，对应 sys_route；豁免数据权限，可见性由服务层显式计算 */
@Data
@TableName("sys_route")
public class SysRoute {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String routeCode;

    /** 1=短驳 2=干线 */
    private Integer routeCategory;

    private Long originDeptId;

    private Long destDeptId;

    private String routeName;

    private String warehouseArea;

    private BigDecimal mileage;

    private BigDecimal transitHours;

    private String contactName;

    private String contactPhone;

    private String serviceName;

    private String servicePhone;

    /** 线路重泡比(kg/m³)：为空或0时回落 sys_config.fee.volume_weight_ratio 全局值 */
    private BigDecimal volumeWeightRatio;

    /** 创建公司：总部或分公司 */
    private Long ownerCompanyId;

    private Boolean status;

    private Integer sortOrder;

    private String remark;

    /** 审计锚点：创建人主机构 */
    private Long deptId;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
