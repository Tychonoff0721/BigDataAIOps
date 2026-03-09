package com.aiops.bigdata.entity.base;

import lombok.Data;

import java.time.Instant;

/**
 * 指标基类 - 所有指标类型的抽象父类
 * 包含所有指标共有的基础字段
 */
@Data
public abstract class Metrics {
    
    /**
     * 集群名称，支持多集群场景
     */
    private String cluster;
    
    /**
     * 指标采集时间戳（Unix时间戳，秒）
     */
    private Long timestamp;
    
    /**
     * 获取指标类型标识
     */
    public abstract String getMetricsType();
    
    /**
     * 获取可读的时间字符串
     */
    public String getReadableTime() {
        if (timestamp == null) {
            return null;
        }
        return Instant.ofEpochSecond(timestamp).toString();
    }
}
