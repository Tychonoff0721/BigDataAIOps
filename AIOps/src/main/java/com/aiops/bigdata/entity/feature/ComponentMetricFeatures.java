package com.aiops.bigdata.entity.feature;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 组件指标特征
 * 从时间序列指标中提取的统计特征
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ComponentMetricFeatures extends MetricFeatures {
    
    /**
     * 集群名称
     */
    private String cluster;
    
    /**
     * 服务类型
     */
    private String service;
    
    /**
     * 组件类型
     */
    private String component;
    
    /**
     * 实例标识
     */
    private String instance;
    
    /**
     * 各指标的特征集合
     * Key: 指标名称
     * Value: 该指标的时间序列特征
     */
    private Map<String, TimeSeriesFeatures> metricFeatures = new HashMap<>();
    
    /**
     * 整体健康评分（0-100）
     */
    private Integer healthScore;
    
    /**
     * 检测到的主要问题
     */
    private String mainIssue;
    
    /**
     * 异常指标数量
     */
    private Integer anomalyMetricCount;
    
    @Override
    public String getFeatureType() {
        return "component_metrics";
    }
    
    @Override
    public String toSummaryText() {
        StringBuilder sb = new StringBuilder();
        sb.append("组件: ").append(service).append("/").append(component);
        if (instance != null) {
            sb.append(" (").append(instance).append(")");
        }
        sb.append("\n");
        
        if (healthScore != null) {
            sb.append("健康评分: ").append(healthScore).append("/100\n");
        }
        
        if (mainIssue != null) {
            sb.append("主要问题: ").append(mainIssue).append("\n");
        }
        
        sb.append("指标特征:\n");
        for (Map.Entry<String, TimeSeriesFeatures> entry : metricFeatures.entrySet()) {
            TimeSeriesFeatures f = entry.getValue();
            if (f.isAnomaly() || f.isSpike()) {
                sb.append(String.format("  - %s: 当前=%.2f, 均值=%.2f, 趋势=%s, 异常=%s\n",
                    entry.getKey(),
                    f.getCurrent() != null ? f.getCurrent() : 0,
                    f.getMean() != null ? f.getMean() : 0,
                    f.getTrendDirection() != null ? f.getTrendDirection() : "stable",
                    f.isAnomaly() ? "是" : "否"));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 添加指标特征
     */
    public ComponentMetricFeatures addMetricFeatures(String metricName, TimeSeriesFeatures features) {
        this.metricFeatures.put(metricName, features);
        return this;
    }
    
    /**
     * 获取指定指标的特征
     */
    public TimeSeriesFeatures getMetricFeatures(String metricName) {
        return this.metricFeatures.get(metricName);
    }
    
    /**
     * 获取异常指标
     */
    public Map<String, TimeSeriesFeatures> getAnomalyMetrics() {
        Map<String, TimeSeriesFeatures> anomalies = new HashMap<>();
        for (Map.Entry<String, TimeSeriesFeatures> entry : metricFeatures.entrySet()) {
            if (entry.getValue().isAnomaly()) {
                anomalies.put(entry.getKey(), entry.getValue());
            }
        }
        return anomalies;
    }
}
