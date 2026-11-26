package com.yunshu.modules.transport.constant;

/**
 * 装车配载枚举：车次状态、子车次状态、配载状态、分摊方式。
 * 落库为数值，前端字典与此保持一致；运单状态统一使用 WaybillEnums.Status，禁止另写数字。
 */
public final class TripEnums {

    private TripEnums() {
    }

    /** 车次状态 */
    public static final class TripStatus {
        public static final int PLAN = 10;
        public static final int LOADING = 20;
        public static final int IN_TRANSIT = 30;
        public static final int PART_ARRIVED = 40;
        public static final int ARRIVED = 50;
        public static final int UNLOADED = 60;
        public static final int CLOSED = 90;

        private TripStatus() {
        }

        public static String name(Integer status) {
            if (status == null) {
                return "未知";
            }
            return switch (status) {
                case PLAN -> "车次计划";
                case LOADING -> "装车配载";
                case IN_TRANSIT -> "车辆在途";
                case PART_ARRIVED -> "车辆部分到站";
                case ARRIVED -> "车辆到站";
                case UNLOADED -> "完成卸车";
                case CLOSED -> "已关闭";
                default -> "未知";
            };
        }
    }

    /** 子车次状态 */
    public static final class SubStatus {
        public static final int PENDING = 10;
        public static final int IN_TRANSIT = 20;
        public static final int ARRIVED = 30;
        public static final int UNLOADED = 40;

        private SubStatus() {
        }
    }

    /** 配载状态 */
    public static final class LoadStatus {
        public static final int PENDING = 0;
        public static final int LOADED = 1;
        public static final int DEPARTED = 2;
        public static final int ARRIVED = 3;
        public static final int UNLOADED = 4;

        private LoadStatus() {
        }
    }

    /** 全车统一分摊方式 */
    public static final class AllocMethod {
        public static final int WEIGHT = 1;
        public static final int VOLUME = 2;
        public static final int CHARGE_WEIGHT = 3;

        private AllocMethod() {
        }
    }

    /** 车次日志动作类型 */
    public static final class LogAction {
        public static final int CREATE = 1;
        public static final int UPDATE = 2;
        public static final int DELETE = 10;

        private LogAction() {
        }
    }
}
