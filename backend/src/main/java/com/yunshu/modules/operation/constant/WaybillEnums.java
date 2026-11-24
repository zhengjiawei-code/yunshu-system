package com.yunshu.modules.operation.constant;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 运单相关枚举的唯一来源（方案第 10 节）。
 * 状态码值、中文文案只允许从这里取，禁止在各 service 内重复写死。
 */
public final class WaybillEnums {

    private WaybillEnums() {
    }

    /** 运单状态：10–130 按 10 递增，作废为 900，共 14 个码值 */
    public enum Status {
        PENDING_PICKUP(10, "待揽收"),
        PICKED_UP(20, "已揽收"),
        HUB_LOADED(30, "网点装车"),
        HUB_DEPARTED(40, "网点发车"),
        TRANSIT_ARRIVED(50, "分拨到车"),
        TRANSIT_UNLOADED(60, "分拨卸车"),
        TRANSIT_LOADED(70, "分拨装车"),
        TRANSIT_DEPARTED(80, "分拨发车"),
        TERMINAL_ARRIVED(90, "末端到车"),
        ARRIVED_WAIT_PICKUP(100, "到站待提"),
        ARRIVED_WAIT_DELIVERY(110, "到站待送"),
        ARRIVED_WAIT_TRANSFER(120, "到站待转"),
        SIGNED(130, "已签收"),
        VOIDED(900, "已作废");

        private final int code;
        private final String label;

        Status(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        /** 码值 -> 中文；未知码值返回 null，由调用方决定如何兜底 */
        public static String labelOf(Integer code) {
            if (code == null) {
                return null;
            }
            return Arrays.stream(values())
                    .filter(item -> item.code == code)
                    .map(Status::getLabel)
                    .findFirst()
                    .orElse(null);
        }

        public static boolean isValid(Integer code) {
            return code != null && Arrays.stream(values()).anyMatch(item -> item.code == code);
        }

        /** 改单允许状态：20 已揽收 ~ 90 末端到车（含端点） */
        public static boolean amendable(Integer code) {
            return code != null && code >= PICKED_UP.code && code <= TERMINAL_ARRIVED.code;
        }

        public static boolean isVoided(Integer code) {
            return code != null && code == VOIDED.code;
        }
    }

    /** 支付方式 */
    public enum PayMethod {
        PREPAID(1, "现付"),
        COLLECT(2, "到付"),
        MONTHLY(3, "月结"),
        RETURN(4, "回付"),
        MULTI(5, "多笔付");

        private final int code;
        private final String label;

        PayMethod(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        public static boolean isValid(Integer code) {
            return code != null && Arrays.stream(values()).anyMatch(item -> item.code == code);
        }

        public static String labelOf(Integer code) {
            if (code == null) {
                return null;
            }
            return Arrays.stream(values())
                    .filter(item -> item.code == code)
                    .map(PayMethod::getLabel)
                    .findFirst()
                    .orElse(null);
        }
    }

    /** 送货方式 */
    public enum DeliveryMethod {
        DELIVERY(1, "送货"),
        SELF_PICKUP(2, "自提"),
        TRUCK_DIRECT(3, "大车直送");

        private final int code;
        private final String label;

        DeliveryMethod(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        public static boolean isValid(Integer code) {
            return code != null && Arrays.stream(values()).anyMatch(item -> item.code == code);
        }

        public static String labelOf(Integer code) {
            if (code == null) {
                return null;
            }
            return Arrays.stream(values())
                    .filter(item -> item.code == code)
                    .map(DeliveryMethod::getLabel)
                    .findFirst()
                    .orElse(null);
        }
    }

    /** 业务类型 */
    public enum BizType {
        FULL_TRUCK(1, "整车"),
        LESS_TRUCK(2, "零担");

        private final int code;
        private final String label;

        BizType(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 末段类型 */
    public enum RouteEndType {
        DIRECT(1, "直达"),
        TRANSFER(2, "外转");

        private final int code;
        private final String label;

        RouteEndType(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 是否类字段（回单、等通知） */
    public enum YesNo {
        NO(0, "否"),
        YES(1, "是");

        private final int code;
        private final String label;

        YesNo(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 母/子单标识 */
    public enum ChildFlag {
        PARENT(0, "母单"),
        CHILD(1, "子单");

        private final int code;
        private final String label;

        ChildFlag(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 运单日志动作类型（biz_waybill_log.action_type） */
    public enum LogAction {
        SPLIT(1, "拆单"),
        MERGE(2, "合单"),
        CANCEL_SPLIT(3, "撤销拆单"),
        VOID(4, "作废"),
        FORCE_SIGN(5, "强制签收"),
        AMEND(6, "改单");

        private final int code;
        private final String label;

        LogAction(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 运单状态下拉选项（码值 + 中文），供前端筛选与详情标签统一取值 */
    public static Map<Integer, String> statusOptions() {
        return Arrays.stream(Status.values())
                .collect(Collectors.toMap(Status::getCode, Status::getLabel,
                        (left, right) -> left, LinkedHashMap::new));
    }
}
