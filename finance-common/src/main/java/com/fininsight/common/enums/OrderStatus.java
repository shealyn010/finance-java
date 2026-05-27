package com.fininsight.common.enums;

public enum OrderStatus {
    PENDING("待处理"),
    PROCESSING("处理中"),
    COMPLETED("已完成"),
    CLOSED("已关闭");

    private final String desc;

    OrderStatus(String desc) { this.desc = desc; }
    public String getDesc() { return desc; }
}
