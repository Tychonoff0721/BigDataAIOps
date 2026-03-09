package com.aiops.bigdata.entity.feature;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 指标特征基类
 * 所有特征类的抽象父类
 */
@Data
public abstract class MetricFeatures {
    
    /**
     * 特征提取时间
     */
    private Long extractTime;
    
    /**
     * 数据点数量
     */
    private Integer dataPoints;
    
    /**
     * 时间窗口大小（秒）
     */
    private Integer windowSizeSeconds;
    
    /**
     * 扩展特征（支持动态添加）
     */
    private Map<String, Object> extraFeatures = new HashMap<>();
    
    /**
     * 添加扩展特征
     */
    public MetricFeatures addFeature(String key, Object value) {
        this.extraFeatures.put(key, value);
        return this;
    }
    
    /**
     * 获取特征类型
     */
    public abstract String getFeatureType();
    
    /**
     * 转换为LLM可读的文本摘要
     * 用于控制Token数量
     */
    public abstract String toSummaryText();
}
