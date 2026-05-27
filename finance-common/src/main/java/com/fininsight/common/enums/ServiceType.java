package com.fininsight.common.enums;

public enum ServiceType {
    INSTALL("安装"),
    REPAIR("维修"),
    INSPECTION("巡检"),
    MAINTENANCE("保养"),
    CUSTOM("定制");

    private final String desc;

    ServiceType(String desc) { this.desc = desc; }
    public String getDesc() { return desc; }
}
