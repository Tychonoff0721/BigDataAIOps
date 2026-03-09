package com.aiops.bigdata.entity.common.enums;

/**
 * 健康状态枚举
 */
public enum HealthStatus {
    
    /**
     * 健康 - 组件/作业运行正常
     */
    HEALTHY("healthy", "健康"),
    
    /**
     * 警告 - 存在潜在问题，需要关注
     */
    WARNING("warning", "警告"),
    
    /**
     * 严重 - 存在严重问题，需要立即处理
     */
    CRITICAL("critical", "严重"),
    
    /**
     * 未知 - 无法判断状态
     */
    UNKNOWN("unknown", "未知");
    
    private final String code;
    private final String description;
    
    HealthStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据code获取枚举
     */
    public static HealthStatus fromCode(String code) {
        for (HealthStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
