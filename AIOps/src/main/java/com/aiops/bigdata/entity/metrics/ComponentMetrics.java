package com.aiops.bigdata.entity.metrics;

import com.aiops.bigdata.entity.base.Metrics;
import com.aiops.bigdata.entity.common.enums.ComponentType;
import com.aiops.bigdata.entity.common.enums.ServiceType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

/**
 * 组件指标实体类
 * 用于接收大数据集群组件（HDFS、YARN、Spark等）的监控指标
 * 
 * 设计特点：使用Map存储动态指标，支持灵活扩展
 * 可以自由增减metrics数量，无需修改代码
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ComponentMetrics extends Metrics {
    
    /**
     * 服务类型（如 hdfs, yarn, spark, hive, kafka, flink）
     */
    private String service;
    
    /**
     * 组件类型（如 namenode, datanode, resourcemanager等）
     */
    private String component;
    
    /**
     * 命名服务，用于HDFS联邦等场景
     */
    private String nameservice;
    
    /**
     * 实例所在节点
     */
    private String instance;
    
    /**
     * CPU使用率 (0-1)
     */
    private Double cpuUsage;
    
    /**
     * 内存使用率 (0-1)
     */
    private Double memoryUsage;
    
    /**
     * GC时间 (ms)
     */
    private Long gcTime;
    
    /**
     * 连接数
     */
    private Integer connectionCount;
    
    /**
     * 动态指标集合
     * Key: 指标名称（如 memory_usage, gc_time, fswriteopsavgtime等）
     * Value: 指标值（支持多种类型：Double, Long, String等）
     */
    private Map<String, Object> metrics = new HashMap<>();
    
    public ComponentMetrics addMetric(String name, Object value) {
        this.metrics.put(name, value);
        return this;
    }
    
    public Object getMetric(String name) {
        return this.metrics.get(name);
    }
    
    public Double getMetricAsDouble(String name) {
        Object value = this.metrics.get(name);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public Long getMetricAsLong(String name) {
        Object value = this.metrics.get(name);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    @Override
    public String getMetricsType() {
        return "component";
    }
    
    public ServiceType getServiceTypeEnum() {
        return ServiceType.fromCode(this.service);
    }
    
    public ComponentType getComponentTypeEnum() {
        return ComponentType.fromCode(this.component);
    }
    
    public String getUniqueId() {
        StringBuilder sb = new StringBuilder();
        sb.append(getCluster() != null ? getCluster() : "default");
        sb.append(":").append(service);
        sb.append(":").append(component);
        if (nameservice != null) sb.append(":").append(nameservice);
        if (instance != null) sb.append(":").append(instance);
        return sb.toString();
    }
}
