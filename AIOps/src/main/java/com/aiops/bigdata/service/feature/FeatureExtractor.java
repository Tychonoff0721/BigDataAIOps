package com.aiops.bigdata.service.feature;

import com.aiops.bigdata.entity.feature.ComponentMetricFeatures;
import com.aiops.bigdata.entity.feature.SparkAppFeatures;
import com.aiops.bigdata.entity.metrics.ComponentMetrics;
import com.aiops.bigdata.entity.metrics.SparkAppMetrics;

import java.util.List;

/**
 * 特征提取器接口
 * 负责从原始指标数据中提取特征
 */
public interface FeatureExtractor {
    
    /**
     * 从单个组件指标提取特征
     * 适用于单点数据，提取基本特征
     * 
     * @param metrics 组件指标
     * @return 组件指标特征
     */
    ComponentMetricFeatures extract(ComponentMetrics metrics);
    
    /**
     * 从时间序列组件指标提取特征
     * 适用于历史数据，提取完整的时序特征
     * 
     * @param metricsHistory 按时间排序的指标列表
     * @return 组件指标特征
     */
    ComponentMetricFeatures extractFromTimeSeries(List<ComponentMetrics> metricsHistory);
    
    /**
     * 从Spark作业指标提取特征
     * 
     * @param metrics Spark作业指标
     * @return Spark作业特征
     */
    SparkAppFeatures extract(SparkAppMetrics metrics);
}
