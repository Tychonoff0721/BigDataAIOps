package com.aiops.bigdata.service.feature.impl;

import com.aiops.bigdata.entity.config.ThresholdConfig;
import com.aiops.bigdata.entity.feature.ComponentMetricFeatures;
import com.aiops.bigdata.entity.feature.SparkAppFeatures;
import com.aiops.bigdata.entity.feature.TimeSeriesFeatures;
import com.aiops.bigdata.entity.metrics.ComponentMetrics;
import com.aiops.bigdata.entity.metrics.SparkAppMetrics;
import com.aiops.bigdata.service.feature.FeatureExtractor;
import com.aiops.bigdata.service.feature.TimeSeriesFeatureExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 特征提取器实现类
 * 负责从原始指标数据中提取特征
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureExtractorImpl implements FeatureExtractor {
    
    private final TimeSeriesFeatureExtractor timeSeriesFeatureExtractor;
    private final ThresholdConfig thresholdConfig;
    
    @Override
    public ComponentMetricFeatures extract(ComponentMetrics metrics) {
        ComponentMetricFeatures features = new ComponentMetricFeatures();
        features.setExtractTime(System.currentTimeMillis());
        features.setDataPoints(1);
        features.setCluster(metrics.getCluster());
        features.setService(metrics.getService());
        features.setComponent(metrics.getComponent());
        features.setInstance(metrics.getInstance());
        
        // 处理直接字段
        if (metrics.getCpuUsage() != null) {
            addMetricFeature(features, metrics.getService(), metrics.getComponent(), 
                "cpu_usage", metrics.getCpuUsage());
        }
        if (metrics.getMemoryUsage() != null) {
            addMetricFeature(features, metrics.getService(), metrics.getComponent(), 
                "memory_usage", metrics.getMemoryUsage());
        }
        if (metrics.getGcTime() != null) {
            addMetricFeature(features, metrics.getService(), metrics.getComponent(), 
                "gc_time", metrics.getGcTime().doubleValue());
        }
        if (metrics.getConnectionCount() != null) {
            addMetricFeature(features, metrics.getService(), metrics.getComponent(), 
                "connection_count", metrics.getConnectionCount().doubleValue());
        }
        
        // 从metrics Map中提取特征
        if (metrics.getMetrics() != null) {
            for (Map.Entry<String, Object> entry : metrics.getMetrics().entrySet()) {
                String metricName = entry.getKey();
                Double value = toDouble(entry.getValue());
                
                if (value != null) {
                    addMetricFeature(features, metrics.getService(), metrics.getComponent(), 
                        metricName, value);
                }
            }
        }
        
        // 计算整体健康评分
        calculateHealthScore(features);
        
        return features;
    }
    
    /**
     * 添加指标特征
     */
    private void addMetricFeature(ComponentMetricFeatures features, String service, 
            String component, String metricName, Double value) {
        
        TimeSeriesFeatures tsFeatures = new TimeSeriesFeatures();
        tsFeatures.setCurrent(value);
        tsFeatures.setMean(value);
        tsFeatures.setMin(value);
        tsFeatures.setMax(value);
        tsFeatures.setMedian(value);
        tsFeatures.setStddev(0.0);
        tsFeatures.setVariance(0.0);
        tsFeatures.setRange(0.0);
        tsFeatures.setTrendDirection("stable");
        tsFeatures.setStabilityScore(100.0);
        
        // 检查是否超过阈值
        ThresholdConfig.ThresholdValue threshold = thresholdConfig.getThreshold(
            service, component, convertToConfigKey(metricName));
        
        if (threshold != null) {
            boolean isWarning = threshold.isExceeded(value, false);
            boolean isCritical = threshold.isExceeded(value, true);
            tsFeatures.setAnomaly(isCritical || isWarning);
            tsFeatures.setAnomalyScore(isCritical ? 1.0 : (isWarning ? 0.6 : 0.0));
            tsFeatures.setAnomalyType(isCritical ? "critical" : (isWarning ? "warning" : null));
        }
        
        features.addMetricFeatures(metricName, tsFeatures);
    }
    
    @Override
    public ComponentMetricFeatures extractFromTimeSeries(List<ComponentMetrics> metricsHistory) {
        if (metricsHistory == null || metricsHistory.isEmpty()) {
            return new ComponentMetricFeatures();
        }
        
        // 按时间排序
        List<ComponentMetrics> sorted = metricsHistory.stream()
            .filter(m -> m.getTimestamp() != null)
            .sorted(Comparator.comparingLong(ComponentMetrics::getTimestamp))
            .collect(Collectors.toList());
        
        if (sorted.isEmpty()) {
            return extract(metricsHistory.get(0));
        }
        
        ComponentMetricFeatures features = new ComponentMetricFeatures();
        features.setExtractTime(System.currentTimeMillis());
        features.setDataPoints(sorted.size());
        
        ComponentMetrics latest = sorted.get(sorted.size() - 1);
        features.setCluster(latest.getCluster());
        features.setService(latest.getService());
        features.setComponent(latest.getComponent());
        features.setInstance(latest.getInstance());
        
        // 计算时间窗口
        if (sorted.size() >= 2) {
            long windowSeconds = sorted.get(sorted.size() - 1).getTimestamp() 
                               - sorted.get(0).getTimestamp();
            features.setWindowSizeSeconds((int) windowSeconds);
        }
        
        // 收集所有指标名称
        Set<String> allMetricNames = new HashSet<>();
        for (ComponentMetrics m : sorted) {
            allMetricNames.addAll(m.getMetrics().keySet());
        }
        
        // 对每个指标提取时间序列特征
        for (String metricName : allMetricNames) {
            List<Double> values = new ArrayList<>();
            for (ComponentMetrics m : sorted) {
                Double value = m.getMetricAsDouble(metricName);
                if (value != null) {
                    values.add(value);
                }
            }
            
            if (!values.isEmpty()) {
                TimeSeriesFeatures tsFeatures = timeSeriesFeatureExtractor.extract(values);
                features.addMetricFeatures(metricName, tsFeatures);
            }
        }
        
        // 计算整体健康评分
        calculateHealthScore(features);
        
        // 识别主要问题
        identifyMainIssue(features);
        
        return features;
    }
    
    @Override
    public SparkAppFeatures extract(SparkAppMetrics metrics) {
        SparkAppFeatures features = new SparkAppFeatures();
        features.setExtractTime(System.currentTimeMillis());
        features.setDataPoints(1);
        features.setJobId(metrics.getJobId());
        features.setAppName(metrics.getAppName());
        
        // ============ 执行特征 ============
        
        features.setDuration(metrics.getDuration());
        features.setDurationLevel(categorizeDuration(metrics.getDuration()));
        features.setStatus(metrics.getStatus());
        features.setSuccess("SUCCEEDED".equalsIgnoreCase(metrics.getStatus()));
        
        // ============ 资源特征 ============
        
        features.setExecutorCount(metrics.getExecutorCount());
        features.setExecutorCores(metrics.getExecutorCores());
        
        if (metrics.getExecutorMemory() != null) {
            features.setExecutorMemoryGB(parseMemoryGB(metrics.getExecutorMemory()));
        }
        
        // ============ 数据特征 ============
        
        features.setInputSizeGB(bytesToGB(metrics.getInputSize()));
        features.setOutputSizeGB(bytesToGB(metrics.getOutputSize()));
        features.setShuffleReadGB(bytesToGB(metrics.getShuffleRead()));
        features.setShuffleWriteGB(bytesToGB(metrics.getShuffleWrite()));
        
        double totalData = (metrics.getInputSize() != null ? metrics.getInputSize() : 0)
                         + (metrics.getOutputSize() != null ? metrics.getOutputSize() : 0)
                         + (metrics.getShuffleRead() != null ? metrics.getShuffleRead() : 0)
                         + (metrics.getShuffleWrite() != null ? metrics.getShuffleWrite() : 0);
        features.setTotalDataSizeGB(bytesToGB((long) totalData));
        
        double shuffleRatio = metrics.getShuffleRatio();
        features.setShuffleRatio(shuffleRatio);
        features.setShuffleRatioLevel(categorizeShuffleRatio(shuffleRatio));
        
        // ============ 性能特征 ============
        
        if (metrics.getStages() != null) {
            features.setStageCount(metrics.getStages().size());
            
            int totalTasks = 0;
            int failedTasks = 0;
            long maxDuration = 0;
            double totalDuration = 0;
            
            for (SparkAppMetrics.StageInfo stage : metrics.getStages()) {
                if (stage.getNumTasks() != null) totalTasks += stage.getNumTasks();
                if (stage.getFailedTasks() != null) failedTasks += stage.getFailedTasks();
                if (stage.getDuration() != null) {
                    totalDuration += stage.getDuration();
                    maxDuration = Math.max(maxDuration, stage.getDuration());
                }
            }
            
            features.setTotalTasks(totalTasks);
            features.setFailedTasks(failedTasks);
            features.setFailureRate(totalTasks > 0 ? (double) failedTasks / totalTasks : 0);
            features.setAvgStageDuration(metrics.getStages().size() > 0 
                ? totalDuration / metrics.getStages().size() : 0);
            features.setMaxStageDuration(maxDuration);
            
            // 计算Stage时长分布不均匀度
            features.setStageDurationSkewness(calculateStageDurationSkewness(metrics.getStages()));
        }
        
        // ============ 数据倾斜特征 ============
        
        extractDataSkewFeatures(metrics, features);
        
        // ============ GC特征 ============
        
        extractGCFeatures(metrics, features);
        
        // ============ 内存特征 ============
        
        extractMemoryFeatures(metrics, features);
        
        // ============ 瓶颈检测 ============
        
        detectBottlenecks(features);
        
        // ============ Stage特征 ============
        
        extractStageFeatures(metrics, features);
        
        // ============ 健康评分 ============
        
        calculateSparkHealthScore(features);
        
        return features;
    }
    
    /**
     * 提取数据倾斜特征
     */
    private void extractDataSkewFeatures(SparkAppMetrics metrics, SparkAppFeatures features) {
        if (metrics.getStages() == null || metrics.getStages().isEmpty()) {
            return;
        }
        
        double maxSkewRatio = 0;
        int skewedCount = 0;
        List<String> skewedStages = new ArrayList<>();
        
        ThresholdConfig.ThresholdValue skewThreshold = thresholdConfig.getSparkAppThreshold("skew-ratio");
        double warningThreshold = skewThreshold != null ? skewThreshold.getWarning() : 3.0;
        
        for (SparkAppMetrics.StageInfo stage : metrics.getStages()) {
            double skewRatio = stage.getSkewRatio();
            if (skewRatio > warningThreshold) {
                skewedCount++;
                skewedStages.add(stage.getStageId());
                maxSkewRatio = Math.max(maxSkewRatio, skewRatio);
            }
        }
        
        features.setHasDataSkew(skewedCount > 0);
        features.setMaxSkewRatio(maxSkewRatio > 0 ? maxSkewRatio : null);
        features.setSkewedStageCount(skewedCount);
        features.setSkewedStages(skewedStages);
    }
    
    /**
     * 提取GC特征
     */
    private void extractGCFeatures(SparkAppMetrics metrics, SparkAppFeatures features) {
        long totalGcTime = 0;
        
        if (metrics.getStages() != null) {
            for (SparkAppMetrics.StageInfo stage : metrics.getStages()) {
                if (stage.getGcTime() != null) {
                    totalGcTime += stage.getGcTime();
                }
            }
        }
        
        features.setTotalGcTime(totalGcTime);
        
        if (metrics.getDuration() != null && metrics.getDuration() > 0) {
            double gcRatio = (double) totalGcTime / metrics.getDuration();
            features.setGcTimeRatio(gcRatio);
            features.setGcTimeLevel(categorizeGCRatio(gcRatio));
        }
    }
    
    /**
     * 提取内存特征
     */
    private void extractMemoryFeatures(SparkAppMetrics metrics, SparkAppFeatures features) {
        if (metrics.getExecutors() == null || metrics.getExecutors().isEmpty()) {
            return;
        }
        
        double totalMemUsage = 0;
        double maxMemUsage = 0;
        long totalMemorySpilled = 0;
        long totalDiskSpilled = 0;
        
        for (SparkAppMetrics.ExecutorInfo executor : metrics.getExecutors()) {
            double memUsage = executor.getMemoryUsageRate();
            totalMemUsage += memUsage;
            maxMemUsage = Math.max(maxMemUsage, memUsage);
            
            if (executor.getMemoryUsed() != null) {
                // 这里可能需要其他字段来计算溢写
            }
        }
        
        int executorCount = metrics.getExecutors().size();
        features.setAvgExecutorMemoryUsage(totalMemUsage / executorCount);
        features.setMaxExecutorMemoryUsage(maxMemUsage);
    }
    
    /**
     * 提取Stage特征
     */
    private void extractStageFeatures(SparkAppMetrics metrics, SparkAppFeatures features) {
        if (metrics.getStages() == null) {
            return;
        }
        
        List<SparkAppFeatures.StageFeatures> stageFeatures = new ArrayList<>();
        
        for (SparkAppMetrics.StageInfo stage : metrics.getStages()) {
            SparkAppFeatures.StageFeatures sf = new SparkAppFeatures.StageFeatures();
            sf.setStageId(stage.getStageId());
            sf.setName(stage.getName());
            sf.setDuration(stage.getDuration());
            sf.setTaskCount(stage.getNumTasks());
            sf.setFailedTasks(stage.getFailedTasks());
            sf.setShuffleReadBytes(stage.getShuffleReadBytes());
            sf.setShuffleWriteBytes(stage.getShuffleWriteBytes());
            sf.setGcTime(stage.getGcTime());
            
            if (stage.getDuration() != null && stage.getDuration() > 0 && stage.getGcTime() != null) {
                sf.setGcRatio((double) stage.getGcTime() / stage.getDuration());
            }
            
            sf.setHasSkew(stage.getHasSkew() != null && stage.getHasSkew());
            sf.setSkewRatio(stage.getSkewRatio());
            
            // 判断是否为瓶颈Stage
            sf.setBottleneck(isBottleneckStage(stage, features));
            if (sf.isBottleneck()) {
                sf.setBottleneckType(determineBottleneckType(stage));
            }
            
            stageFeatures.add(sf);
        }
        
        features.setStageFeatures(stageFeatures);
    }
    
    /**
     * 检测瓶颈
     */
    private void detectBottlenecks(SparkAppFeatures features) {
        List<String> bottlenecks = new ArrayList<>();
        double maxSeverity = 0;
        String mainBottleneck = "none";
        
        // 检查数据倾斜
        if (features.isHasDataSkew()) {
            double severity = Math.min(100, (features.getMaxSkewRatio() != null ? features.getMaxSkewRatio() : 0) * 10);
            bottlenecks.add("skew");
            if (severity > maxSeverity) {
                maxSeverity = severity;
                mainBottleneck = "skew";
            }
        }
        
        // 检查Shuffle
        if (features.getShuffleRatio() != null && features.getShuffleRatio() > 0.5) {
            double severity = features.getShuffleRatio() * 100;
            bottlenecks.add("shuffle");
            if (severity > maxSeverity) {
                maxSeverity = severity;
                mainBottleneck = "shuffle";
            }
        }
        
        // 检查GC
        if (features.getGcTimeRatio() != null && features.getGcTimeRatio() > 0.1) {
            double severity = features.getGcTimeRatio() * 100;
            bottlenecks.add("gc");
            if (severity > maxSeverity) {
                maxSeverity = severity;
                mainBottleneck = "gc";
            }
        }
        
        // 检查内存
        if (features.getMaxExecutorMemoryUsage() != null && features.getMaxExecutorMemoryUsage() > 0.9) {
            double severity = features.getMaxExecutorMemoryUsage() * 100;
            bottlenecks.add("memory");
            if (severity > maxSeverity) {
                maxSeverity = severity;
                mainBottleneck = "memory";
            }
        }
        
        // 检查失败任务
        if (features.getFailureRate() != null && features.getFailureRate() > 0) {
            double severity = features.getFailureRate() * 100;
            bottlenecks.add("failure");
            if (severity > maxSeverity) {
                maxSeverity = severity;
                mainBottleneck = "failure";
            }
        }
        
        features.setDetectedBottlenecks(bottlenecks);
        features.setMainBottleneck(mainBottleneck);
        features.setBottleneckSeverity(maxSeverity);
    }
    
    /**
     * 计算组件指标健康评分
     */
    private void calculateHealthScore(ComponentMetricFeatures features) {
        int score = 100;
        int anomalyCount = 0;
        
        for (TimeSeriesFeatures tsf : features.getMetricFeatures().values()) {
            if (tsf.isAnomaly()) {
                anomalyCount++;
                score -= tsf.getAnomalyScore() != null ? (int)(tsf.getAnomalyScore() * 20) : 10;
            }
            if ("up".equals(tsf.getTrendDirection()) && tsf.getTrendStrength() != null) {
                // 资源使用上升趋势，可能需要注意
                if (tsf.getTrendStrength() > 0.7) {
                    score -= 5;
                }
            }
        }
        
        features.setHealthScore(Math.max(0, Math.min(100, score)));
        features.setAnomalyMetricCount(anomalyCount);
    }
    
    /**
     * 识别主要问题
     */
    private void identifyMainIssue(ComponentMetricFeatures features) {
        Map<String, TimeSeriesFeatures> anomalies = features.getAnomalyMetrics();
        
        if (anomalies.isEmpty()) {
            features.setMainIssue(null);
            return;
        }
        
        // 找出异常分数最高的指标
        String mainMetric = null;
        double maxScore = 0;
        
        for (Map.Entry<String, TimeSeriesFeatures> entry : anomalies.entrySet()) {
            double score = entry.getValue().getAnomalyScore() != null 
                ? entry.getValue().getAnomalyScore() : 0.5;
            if (score > maxScore) {
                maxScore = score;
                mainMetric = entry.getKey();
            }
        }
        
        if (mainMetric != null) {
            TimeSeriesFeatures tsf = anomalies.get(mainMetric);
            features.setMainIssue(String.format("%s异常: 当前值%.2f", mainMetric, 
                tsf.getCurrent() != null ? tsf.getCurrent() : 0));
        }
    }
    
    /**
     * 计算Spark作业健康评分
     */
    private void calculateSparkHealthScore(SparkAppFeatures features) {
        int healthScore = 100;
        int perfScore = 100;
        
        // 失败任务扣分
        if (features.getFailureRate() != null && features.getFailureRate() > 0) {
            healthScore -= features.getFailureRate() * 50;
        }
        
        // 数据倾斜扣分
        if (features.isHasDataSkew()) {
            healthScore -= 15;
            perfScore -= 20;
        }
        
        // Shuffle比例扣分
        if (features.getShuffleRatio() != null && features.getShuffleRatio() > 0.5) {
            perfScore -= (int)(features.getShuffleRatio() * 30);
        }
        
        // GC比例扣分
        if (features.getGcTimeRatio() != null && features.getGcTimeRatio() > 0.1) {
            perfScore -= (int)(features.getGcTimeRatio() * 50);
        }
        
        // 内存使用扣分
        if (features.getMaxExecutorMemoryUsage() != null && features.getMaxExecutorMemoryUsage() > 0.9) {
            healthScore -= 10;
            perfScore -= 15;
        }
        
        features.setHealthScore(Math.max(0, Math.min(100, healthScore)));
        features.setPerformanceScore(Math.max(0, Math.min(100, perfScore)));
        
        // 设置主要问题
        if (!"none".equals(features.getMainBottleneck())) {
            features.setMainIssue("检测到" + features.getMainBottleneck() + "瓶颈");
        }
    }
    
    // ============ 辅助方法 ============
    
    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private String convertToConfigKey(String metricName) {
        return metricName.replace("_", "-");
    }
    
    private double bytesToGB(Long bytes) {
        if (bytes == null) return 0;
        return bytes / (1024.0 * 1024 * 1024);
    }
    
    private Double parseMemoryGB(String memory) {
        if (memory == null) return null;
        memory = memory.toLowerCase().trim();
        try {
            if (memory.endsWith("g")) {
                return Double.parseDouble(memory.substring(0, memory.length() - 1));
            } else if (memory.endsWith("m")) {
                return Double.parseDouble(memory.substring(0, memory.length() - 1)) / 1024;
            } else if (memory.endsWith("k")) {
                return Double.parseDouble(memory.substring(0, memory.length() - 1)) / (1024 * 1024);
            }
            return Double.parseDouble(memory) / (1024 * 1024 * 1024);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private String categorizeDuration(Long durationMs) {
        if (durationMs == null) return "unknown";
        if (durationMs < 60000) return "short";        // < 1分钟
        if (durationMs < 300000) return "medium";      // < 5分钟
        if (durationMs < 1800000) return "long";       // < 30分钟
        return "very_long";
    }
    
    private String categorizeShuffleRatio(double ratio) {
        if (ratio < 0.3) return "low";
        if (ratio < 0.5) return "medium";
        if (ratio < 0.7) return "high";
        return "very_high";
    }
    
    private String categorizeGCRatio(double ratio) {
        if (ratio < 0.05) return "low";
        if (ratio < 0.1) return "medium";
        return "high";
    }
    
    private Double calculateStageDurationSkewness(List<SparkAppMetrics.StageInfo> stages) {
        if (stages == null || stages.size() < 2) return 0.0;
        
        List<Long> durations = stages.stream()
            .map(SparkAppMetrics.StageInfo::getDuration)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (durations.size() < 2) return 0.0;
        
        double mean = durations.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = durations.stream()
            .mapToDouble(d -> (d - mean) * (d - mean))
            .average().orElse(0);
        double stddev = Math.sqrt(variance);
        
        return mean > 0 ? stddev / mean : 0;
    }
    
    private boolean isBottleneckStage(SparkAppMetrics.StageInfo stage, SparkAppFeatures features) {
        if (stage.getDuration() == null) return false;
        
        // 执行时间超过平均值的2倍
        if (features.getAvgStageDuration() != null && 
            stage.getDuration() > features.getAvgStageDuration() * 2) {
            return true;
        }
        
        // 存在数据倾斜
        if (stage.getHasSkew() != null && stage.getHasSkew()) {
            return true;
        }
        
        // GC时间占比过高
        if (stage.getGcTime() != null && stage.getDuration() != null &&
            stage.getGcTime() > stage.getDuration() * 0.1) {
            return true;
        }
        
        // 有失败任务
        if (stage.getFailedTasks() != null && stage.getFailedTasks() > 0) {
            return true;
        }
        
        return false;
    }
    
    private String determineBottleneckType(SparkAppMetrics.StageInfo stage) {
        if (stage.getFailedTasks() != null && stage.getFailedTasks() > 0) {
            return "failure";
        }
        if (stage.getHasSkew() != null && stage.getHasSkew()) {
            return "skew";
        }
        if (stage.getGcTime() != null && stage.getDuration() != null &&
            stage.getGcTime() > stage.getDuration() * 0.1) {
            return "gc";
        }
        if (stage.getShuffleReadBytes() != null && stage.getShuffleReadBytes() > 1_000_000_000L) {
            return "shuffle";
        }
        return "duration";
    }
}
