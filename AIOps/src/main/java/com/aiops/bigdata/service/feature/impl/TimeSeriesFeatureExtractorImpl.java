package com.aiops.bigdata.service.feature.impl;

import com.aiops.bigdata.entity.config.ThresholdConfig;
import com.aiops.bigdata.entity.feature.TimeSeriesFeatures;
import com.aiops.bigdata.service.feature.TimeSeriesFeatureExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 时间序列特征提取器实现类
 * 实现统计特征、趋势检测、异常检测、尖峰检测等
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeSeriesFeatureExtractorImpl implements TimeSeriesFeatureExtractor {
    
    private final ThresholdConfig thresholdConfig;
    
    @Override
    public TimeSeriesFeatures extract(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return new TimeSeriesFeatures();
        }
        
        // 过滤null值
        List<Double> validValues = values.stream()
            .filter(v -> v != null && !Double.isNaN(v) && !Double.isInfinite(v))
            .toList();
        
        if (validValues.isEmpty()) {
            return new TimeSeriesFeatures();
        }
        
        double[] arr = validValues.stream().mapToDouble(Double::doubleValue).toArray();
        return extract(arr);
    }
    
    @Override
    public TimeSeriesFeatures extract(double[] values) {
        TimeSeriesFeatures features = new TimeSeriesFeatures();
        
        if (values == null || values.length == 0) {
            return features;
        }
        
        int n = values.length;
        
        // ============ 基础统计特征 ============
        
        // 排序用于计算分位数
        double[] sorted = Arrays.copyOf(values, n);
        Arrays.sort(sorted);
        
        // 最小值、最大值
        features.setMin(sorted[0]);
        features.setMax(sorted[n - 1]);
        features.setRange(sorted[n - 1] - sorted[0]);
        
        // 平均值
        double sum = Arrays.stream(values).sum();
        double mean = sum / n;
        features.setMean(mean);
        
        // 中位数
        features.setMedian(calculatePercentile(sorted, 50));
        
        // 方差和标准差
        double variance = 0;
        for (double v : values) {
            variance += (v - mean) * (v - mean);
        }
        variance /= n;
        features.setVariance(variance);
        features.setStddev(Math.sqrt(variance));
        
        // ============ 分位数特征 ============
        
        features.setP25(calculatePercentile(sorted, 25));
        features.setP50(calculatePercentile(sorted, 50));
        features.setP75(calculatePercentile(sorted, 75));
        features.setP90(calculatePercentile(sorted, 90));
        features.setP95(calculatePercentile(sorted, 95));
        features.setP99(calculatePercentile(sorted, 99));
        
        // ============ 当前值与变化 ============
        
        features.setCurrent(values[n - 1]);
        if (n >= 2) {
            features.setPrevious(values[n - 2]);
            double change = values[n - 1] - values[n - 2];
            features.setChange(change);
            if (values[n - 2] != 0) {
                features.setChangeRate(change / Math.abs(values[n - 2]));
            }
        }
        
        // ============ 趋势特征 ============
        
        TrendDetectionResult trend = detectTrend(Arrays.stream(values).boxed().toList());
        features.setTrendDirection(trend.direction());
        features.setTrendSlope(trend.slope());
        features.setTrendStrength(trend.strength());
        features.setIncreasing("up".equals(trend.direction()));
        features.setDecreasing("down".equals(trend.direction()));
        
        // ============ 异常特征 ============
        
        double sensitivity = getTimeSeriesConfig("anomaly-sensitivity", 2.0);
        AnomalyDetectionResult anomaly = detectAnomaly(Arrays.stream(values).boxed().toList(), sensitivity);
        features.setAnomaly(anomaly.hasAnomaly());
        features.setAnomalyScore(anomaly.anomalyScore());
        features.setAnomalyType(anomaly.anomalyType());
        
        // ============ 尖峰特征 ============
        
        double spikeThreshold = getTimeSeriesConfig("spike-threshold", 0.5);
        SpikeDetectionResult spike = detectSpikes(Arrays.stream(values).boxed().toList(), spikeThreshold);
        features.setSpike(spike.hasSpike());
        features.setSpikeCount(spike.spikeCount());
        features.setMaxSpikeMagnitude(spike.maxMagnitude());
        
        // ============ 稳定性特征 ============
        
        if (mean != 0) {
            features.setCoefficientOfVariation(features.getStddev() / Math.abs(mean));
        }
        
        // 稳定性评分：基于变异系数和趋势强度
        double stability = 100;
        if (features.getCoefficientOfVariation() != null) {
            stability -= Math.min(50, features.getCoefficientOfVariation() * 100);
        }
        if (features.getTrendStrength() != null && !"stable".equals(features.getTrendDirection())) {
            stability -= features.getTrendStrength() * 30;
        }
        if (features.isAnomaly()) {
            stability -= 20;
        }
        features.setStabilityScore(Math.max(0, stability));
        
        return features;
    }
    
    @Override
    public AnomalyDetectionResult detectAnomaly(List<Double> values, double sensitivity) {
        if (values == null || values.size() < 3) {
            return new AnomalyDetectionResult(false, 0, "none", Collections.emptyList());
        }
        
        double[] arr = values.stream().mapToDouble(Double::doubleValue).toArray();
        int n = arr.length;
        
        // 计算均值和标准差
        double mean = Arrays.stream(arr).average().orElse(0);
        double variance = Arrays.stream(arr).map(v -> (v - mean) * (v - mean)).sum() / n;
        double stddev = Math.sqrt(variance);
        
        if (stddev == 0) {
            return new AnomalyDetectionResult(false, 0, "none", Collections.emptyList());
        }
        
        // 检测异常点
        List<Integer> anomalyIndices = new ArrayList<>();
        double maxZScore = 0;
        String anomalyType = "none";
        
        for (int i = 0; i < n; i++) {
            double zScore = Math.abs(arr[i] - mean) / stddev;
            if (zScore > maxZScore) {
                maxZScore = zScore;
            }
            
            if (zScore > sensitivity) {
                anomalyIndices.add(i);
                
                // 判断异常类型
                if (arr[i] > mean + sensitivity * stddev) {
                    anomalyType = "spike";
                } else if (arr[i] < mean - sensitivity * stddev) {
                    anomalyType = "drop";
                }
            }
        }
        
        // 检测水平偏移（后半段均值与前半段差异显著）
        if (n >= 6) {
            double firstHalfMean = Arrays.stream(arr, 0, n / 2).average().orElse(0);
            double secondHalfMean = Arrays.stream(arr, n / 2, n).average().orElse(0);
            double shiftRatio = Math.abs(secondHalfMean - firstHalfMean) / (stddev + 1e-10);
            
            if (shiftRatio > 2 * sensitivity && anomalyIndices.isEmpty()) {
                anomalyType = "level_shift";
                // 标记偏移点附近
                for (int i = n / 2 - 1; i <= n / 2 + 1 && i < n; i++) {
                    if (i >= 0) anomalyIndices.add(i);
                }
            }
        }
        
        boolean hasAnomaly = !anomalyIndices.isEmpty();
        double anomalyScore = Math.min(1.0, maxZScore / (sensitivity * 2));
        
        return new AnomalyDetectionResult(hasAnomaly, anomalyScore, anomalyType, anomalyIndices);
    }
    
    @Override
    public TrendDetectionResult detectTrend(List<Double> values) {
        if (values == null || values.size() < 3) {
            return new TrendDetectionResult("stable", 0, 0, false);
        }
        
        double[] arr = values.stream().mapToDouble(Double::doubleValue).toArray();
        int n = arr.length;
        
        // 线性回归: y = a + bx
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += arr[i];
            sumXY += i * arr[i];
            sumX2 += i * i;
        }
        
        double meanX = sumX / n;
        double meanY = sumY / n;
        
        // 斜率 b = (n*sumXY - sumX*sumY) / (n*sumX2 - sumX*sumX)
        double denominator = n * sumX2 - sumX * sumX;
        double slope = denominator != 0 ? (n * sumXY - sumX * sumY) / denominator : 0;
        
        // 截距 a = meanY - b * meanX
        double intercept = meanY - slope * meanX;
        
        // 计算R²值
        double ssTotal = 0, ssResidual = 0;
        for (int i = 0; i < n; i++) {
            double predicted = intercept + slope * i;
            ssTotal += (arr[i] - meanY) * (arr[i] - meanY);
            ssResidual += (arr[i] - predicted) * (arr[i] - predicted);
        }
        double rSquared = ssTotal != 0 ? 1 - ssResidual / ssTotal : 0;
        
        // 判断趋势方向
        String direction;
        boolean significant = rSquared > 0.5;
        
        // 计算均值用于判断斜率是否显著
        double absMeanY = Math.abs(meanY);
        double relativeSlope = absMeanY > 0 ? Math.abs(slope) / absMeanY : Math.abs(slope);
        
        if (relativeSlope < 0.001 || rSquared < 0.3) {
            direction = "stable";
            significant = false;
        } else if (slope > 0) {
            direction = "up";
        } else {
            direction = "down";
        }
        
        return new TrendDetectionResult(direction, slope, rSquared, significant);
    }
    
    @Override
    public SpikeDetectionResult detectSpikes(List<Double> values, double threshold) {
        if (values == null || values.size() < 3) {
            return new SpikeDetectionResult(false, 0, 0, Collections.emptyList());
        }
        
        double[] arr = values.stream().mapToDouble(Double::doubleValue).toArray();
        int n = arr.length;
        
        List<Integer> spikeIndices = new ArrayList<>();
        double maxMagnitude = 0;
        
        // 计算相邻点的变化率
        for (int i = 1; i < n; i++) {
            double prev = arr[i - 1];
            double curr = arr[i];
            
            if (prev != 0) {
                double changeRate = Math.abs(curr - prev) / Math.abs(prev);
                
                if (changeRate > threshold) {
                    spikeIndices.add(i);
                    maxMagnitude = Math.max(maxMagnitude, changeRate);
                }
            } else if (curr != 0) {
                // 前一个值为0，当前值非0，视为尖峰
                spikeIndices.add(i);
                maxMagnitude = Math.max(maxMagnitude, 1.0);
            }
        }
        
        boolean hasSpike = !spikeIndices.isEmpty();
        
        return new SpikeDetectionResult(hasSpike, spikeIndices.size(), maxMagnitude, spikeIndices);
    }
    
    /**
     * 计算分位数
     */
    private double calculatePercentile(double[] sortedValues, double percentile) {
        if (sortedValues.length == 0) return 0;
        if (sortedValues.length == 1) return sortedValues[0];
        
        double position = (percentile / 100.0) * (sortedValues.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        
        if (lower == upper) {
            return sortedValues[lower];
        }
        
        // 线性插值
        double fraction = position - lower;
        return sortedValues[lower] + fraction * (sortedValues[upper] - sortedValues[lower]);
    }
    
    /**
     * 获取时间序列配置
     */
    private double getTimeSeriesConfig(String key, double defaultValue) {
        if (thresholdConfig == null || thresholdConfig.getTimeSeries() == null) {
            return defaultValue;
        }
        
        return switch (key) {
            case "anomaly-sensitivity" -> thresholdConfig.getTimeSeries().getAnomalySensitivity() != null 
                ? thresholdConfig.getTimeSeries().getAnomalySensitivity() : defaultValue;
            case "spike-threshold" -> thresholdConfig.getTimeSeries().getSpikeThreshold() != null 
                ? thresholdConfig.getTimeSeries().getSpikeThreshold() : defaultValue;
            default -> defaultValue;
        };
    }
}
