package com.aiops.bigdata.service.feature;

import com.aiops.bigdata.entity.feature.TimeSeriesFeatures;

import java.util.List;

/**
 * 时间序列特征提取器接口
 * 负责从单个指标的时间序列数据中提取统计特征
 */
public interface TimeSeriesFeatureExtractor {
    
    /**
     * 从数值列表提取时间序列特征
     * 
     * @param values 数值列表（按时间顺序）
     * @return 时间序列特征
     */
    TimeSeriesFeatures extract(List<Double> values);
    
    /**
     * 从数值数组提取时间序列特征
     * 
     * @param values 数值数组（按时间顺序）
     * @return 时间序列特征
     */
    TimeSeriesFeatures extract(double[] values);
    
    /**
     * 检测异常
     * 
     * @param values 数值列表
     * @param sensitivity 敏感度（标准差倍数）
     * @return 异常检测结果
     */
    AnomalyDetectionResult detectAnomaly(List<Double> values, double sensitivity);
    
    /**
     * 检测趋势
     * 
     * @param values 数值列表
     * @return 趋势检测结果
     */
    TrendDetectionResult detectTrend(List<Double> values);
    
    /**
     * 检测尖峰
     * 
     * @param values 数值列表
     * @param threshold 尖峰阈值（变化率）
     * @return 尖峰检测结果
     */
    SpikeDetectionResult detectSpikes(List<Double> values, double threshold);
    
    /**
     * 异常检测结果
     */
    record AnomalyDetectionResult(
        boolean hasAnomaly,
        double anomalyScore,
        String anomalyType,
        List<Integer> anomalyIndices
    ) {}
    
    /**
     * 趋势检测结果
     */
    record TrendDetectionResult(
        String direction,      // up, down, stable
        double slope,          // 线性回归斜率
        double strength,       // R²值
        boolean significant    // 是否显著
    ) {}
    
    /**
     * 尖峰检测结果
     */
    record SpikeDetectionResult(
        boolean hasSpike,
        int spikeCount,
        double maxMagnitude,
        List<Integer> spikeIndices
    ) {}
}
