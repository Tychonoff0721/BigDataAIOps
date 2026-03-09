package com.aiops.bigdata.entity.feature;

import lombok.Data;

/**
 * 时间序列特征
 * 从单个指标的时间序列数据中提取的特征
 */
@Data
public class TimeSeriesFeatures {
    
    // ============ 基础统计特征 ============
    
    /**
     * 最大值
     */
    private Double max;
    
    /**
     * 最小值
     */
    private Double min;
    
    /**
     * 平均值
     */
    private Double mean;
    
    /**
     * 中位数
     */
    private Double median;
    
    /**
     * 标准差
     */
    private Double stddev;
    
    /**
     * 方差
     */
    private Double variance;
    
    /**
     * 极差（max - min）
     */
    private Double range;
    
    // ============ 分位数特征 ============
    
    /**
     * 25分位数
     */
    private Double p25;
    
    /**
     * 50分位数（同median）
     */
    private Double p50;
    
    /**
     * 75分位数
     */
    private Double p75;
    
    /**
     * 90分位数
     */
    private Double p90;
    
    /**
     * 95分位数
     */
    private Double p95;
    
    /**
     * 99分位数
     */
    private Double p99;
    
    // ============ 当前值与变化 ============
    
    /**
     * 当前值（最新值）
     */
    private Double current;
    
    /**
     * 前一个值
     */
    private Double previous;
    
    /**
     * 变化量（current - previous）
     */
    private Double change;
    
    /**
     * 变化率（change / previous）
     */
    private Double changeRate;
    
    // ============ 趋势特征 ============
    
    /**
     * 趋势方向：up, down, stable
     */
    private String trendDirection;
    
    /**
     * 趋势斜率（线性回归）
     */
    private Double trendSlope;
    
    /**
     * 趋势强度（R²值）
     */
    private Double trendStrength;
    
    // ============ 异常特征 ============
    
    /**
     * 是否异常
     */
    private boolean anomaly;
    
    /**
     * 异常分数（0-1，越高越异常）
     */
    private Double anomalyScore;
    
    /**
     * 异常类型：spike, drop, level_shift, trend_change
     */
    private String anomalyType;
    
    /**
     * 是否存在尖峰
     */
    private boolean spike;
    
    /**
     * 尖峰数量
     */
    private Integer spikeCount;
    
    /**
     * 最大尖峰幅度
     */
    private Double maxSpikeMagnitude;
    
    // ============ 稳定性特征 ============
    
    /**
     * 波动系数（CV = stddev / mean）
     */
    private Double coefficientOfVariation;
    
    /**
     * 稳定性评分（0-100，越高越稳定）
     */
    private Double stabilityScore;
    
    /**
     * 是否在增长
     */
    private boolean increasing;
    
    /**
     * 是否在下降
     */
    private boolean decreasing;
    
    // ============ 辅助方法 ============
    
    /**
     * 判断是否高于阈值
     */
    public boolean isAboveThreshold(double threshold) {
        return current != null && current > threshold;
    }
    
    /**
     * 判断是否低于阈值
     */
    public boolean isBelowThreshold(double threshold) {
        return current != null && current < threshold;
    }
    
    /**
     * 判断当前值是否异常（基于标准差）
     */
    public boolean isCurrentAnomaly(double sensitivity) {
        if (current == null || mean == null || stddev == null) {
            return false;
        }
        return Math.abs(current - mean) > sensitivity * stddev;
    }
    
    /**
     * 获取简洁的特征摘要
     */
    public String toBriefString() {
        return String.format("current=%.2f, mean=%.2f, stddev=%.2f, trend=%s, anomaly=%s",
            current != null ? current : 0,
            mean != null ? mean : 0,
            stddev != null ? stddev : 0,
            trendDirection != null ? trendDirection : "stable",
            anomaly ? "yes" : "no");
    }
}
