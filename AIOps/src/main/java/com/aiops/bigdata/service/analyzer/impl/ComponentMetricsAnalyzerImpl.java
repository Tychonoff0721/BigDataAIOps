package com.aiops.bigdata.service.analyzer.impl;

import com.aiops.bigdata.entity.analysis.AnalysisResult;
import com.aiops.bigdata.entity.common.enums.HealthStatus;
import com.aiops.bigdata.entity.config.ThresholdConfig;
import com.aiops.bigdata.entity.metrics.ComponentMetrics;
import com.aiops.bigdata.service.analyzer.ComponentMetricsAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 组件指标分析服务实现类
 * 负责分析大数据集群组件的健康状态
 * 
 * TODO: 后续集成特征提取和LLM分析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentMetricsAnalyzerImpl implements ComponentMetricsAnalyzer {
    
    private final ThresholdConfig thresholdConfig;
    
    @Override
    public AnalysisResult analyze(ComponentMetrics metrics) {
        return analyze(metrics, metrics.getCluster());
    }
    
    @Override
    public AnalysisResult analyze(ComponentMetrics metrics, String cluster) {
        log.info("开始分析组件指标: cluster={}, service={}, component={}", 
                metrics.getCluster(), metrics.getService(), metrics.getComponent());
        
        long startTime = System.currentTimeMillis();
        
        AnalysisResult result = new AnalysisResult();
        result.setResultId(UUID.randomUUID().toString());
        result.setCluster(metrics.getCluster());
        result.setAnalysisTime(LocalDateTime.now());
        result.setAnalysisType("component");
        result.setTargetId(metrics.getUniqueId());
        result.setTargetName(metrics.getService() + "-" + metrics.getComponent());
        
        // TODO: 调用特征提取服务提取指标特征
        // MetricFeatures features = featureExtractor.extract(metrics);
        
        // TODO: 调用LLM服务进行分析
        // LLM会通过Tool按需查询:
        // - RealtimeMetricsTool: 查询实时指标
        // - LongTermStatusTool: 查询长期状态
        // - RecentEventsTool: 查询近期事件
        // result = llmService.analyze(features, cluster);
        
        // 临时：基于规则的简单分析（后续替换为LLM分析）
        performRuleBasedAnalysis(metrics, result);
        
        result.setAnalysisDuration(System.currentTimeMillis() - startTime);
        result.calculateHealthScore();
        
        log.info("组件指标分析完成: targetId={}, healthStatus={}, duration={}ms", 
                result.getTargetId(), result.getHealthStatus(), result.getAnalysisDuration());
        
        return result;
    }
    
    @Override
    public List<AnalysisResult> analyzeBatch(List<ComponentMetrics> metricsList) {
        List<AnalysisResult> results = new ArrayList<>();
        for (ComponentMetrics metrics : metricsList) {
            results.add(analyze(metrics));
        }
        return results;
    }
    
    @Override
    public AnalysisResult analyzeTimeSeries(List<ComponentMetrics> metricsHistory) {
        if (metricsHistory == null || metricsHistory.isEmpty()) {
            return createEmptyResult("time_series");
        }
        
        ComponentMetrics latest = metricsHistory.get(metricsHistory.size() - 1);
        AnalysisResult result = analyze(latest);
        result.setAnalysisType("time_series");
        
        // TODO: 时间序列特征分析
        // - 趋势分析
        // - 异常检测
        // - 周期性检测
        
        return result;
    }
    
    @Override
    public String quickHealthCheck(ComponentMetrics metrics) {
        // 快速健康检查：基于配置的阈值规则
        Double memoryUsage = metrics.getMetricAsDouble("memory_usage");
        Double cpuUsage = metrics.getMetricAsDouble("cpu_usage");
        Long gcTime = metrics.getMetricAsLong("gc_time");
        
        // 从配置获取阈值
        ThresholdConfig.ThresholdValue memoryThreshold = thresholdConfig.getThreshold(
            metrics.getService(), metrics.getComponent(), "memory-usage");
        ThresholdConfig.ThresholdValue cpuThreshold = thresholdConfig.getThreshold(
            metrics.getService(), metrics.getComponent(), "cpu-usage");
        ThresholdConfig.ThresholdValue gcThreshold = thresholdConfig.getThreshold(
            metrics.getService(), metrics.getComponent(), "gc-time");
        
        // 检查内存
        if (memoryUsage != null && memoryThreshold != null) {
            if (memoryThreshold.isExceeded(memoryUsage, true)) {
                return HealthStatus.CRITICAL.getCode();
            }
            if (memoryThreshold.isExceeded(memoryUsage, false)) {
                return HealthStatus.WARNING.getCode();
            }
        }
        
        // 检查CPU
        if (cpuUsage != null && cpuThreshold != null) {
            if (cpuThreshold.isExceeded(cpuUsage, true)) {
                return HealthStatus.CRITICAL.getCode();
            }
            if (cpuThreshold.isExceeded(cpuUsage, false)) {
                return HealthStatus.WARNING.getCode();
            }
        }
        
        // 检查GC时间
        if (gcTime != null && gcThreshold != null) {
            if (gcThreshold.isExceeded(gcTime.doubleValue(), true)) {
                return HealthStatus.CRITICAL.getCode();
            }
            if (gcThreshold.isExceeded(gcTime.doubleValue(), false)) {
                return HealthStatus.WARNING.getCode();
            }
        }
        
        return HealthStatus.HEALTHY.getCode();
    }
    
    /**
     * 基于规则的分析（使用配置的阈值）
     * 后续替换为LLM分析
     */
    private void performRuleBasedAnalysis(ComponentMetrics metrics, AnalysisResult result) {
        AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
        List<AnalysisResult.DetectedIssue> issues = new ArrayList<>();
        
        String service = metrics.getService();
        String component = metrics.getComponent();
        
        // 检查内存使用率
        Double memoryUsage = metrics.getMetricAsDouble("memory_usage");
        if (memoryUsage != null) {
            ThresholdConfig.ThresholdValue threshold = thresholdConfig.getThreshold(
                service, component, "memory-usage");
            if (threshold != null) {
                checkAndAddIssue(issues, "memory_usage", memoryUsage, threshold,
                    "内存使用率", service, component);
            }
        }
        
        // 检查CPU使用率
        Double cpuUsage = metrics.getMetricAsDouble("cpu_usage");
        if (cpuUsage != null) {
            ThresholdConfig.ThresholdValue threshold = thresholdConfig.getThreshold(
                service, component, "cpu-usage");
            if (threshold != null) {
                checkAndAddIssue(issues, "cpu_usage", cpuUsage, threshold,
                    "CPU使用率", service, component);
            }
        }
        
        // 检查GC时间
        Long gcTime = metrics.getMetricAsLong("gc_time");
        if (gcTime != null) {
            ThresholdConfig.ThresholdValue threshold = thresholdConfig.getThreshold(
                service, component, "gc-time");
            if (threshold != null) {
                checkAndAddIssue(issues, "gc_time", gcTime.doubleValue(), threshold,
                    "GC时间", service, component);
            }
        }
        
        // 检查堆内存使用率
        Double heapUsage = metrics.getMetricAsDouble("heap_usage");
        if (heapUsage != null) {
            ThresholdConfig.ThresholdValue threshold = thresholdConfig.getThreshold(
                service, component, "heap-usage");
            if (threshold != null) {
                checkAndAddIssue(issues, "heap_usage", heapUsage, threshold,
                    "堆内存使用率", service, component);
            }
        }
        
        diagnosis.setIssues(issues);
        diagnosis.setSummary(issues.isEmpty() ? "组件运行正常" : 
            String.format("检测到 %d 个问题需要关注", issues.size()));
        
        result.setDiagnosis(diagnosis);
        
        // 设置健康状态
        if (issues.stream().anyMatch(i -> i.getSeverity() == HealthStatus.CRITICAL)) {
            result.setHealthStatus(HealthStatus.CRITICAL);
        } else if (issues.stream().anyMatch(i -> i.getSeverity() == HealthStatus.WARNING)) {
            result.setHealthStatus(HealthStatus.WARNING);
        } else {
            result.setHealthStatus(HealthStatus.HEALTHY);
        }
        
        // 生成建议
        generateRecommendations(result, issues);
    }
    
    /**
     * 检查指标并添加问题
     */
    private void checkAndAddIssue(List<AnalysisResult.DetectedIssue> issues, 
            String metricName, Double value, ThresholdConfig.ThresholdValue threshold,
            String metricLabel, String service, String component) {
        
        boolean isCritical = threshold.isExceeded(value, true);
        boolean isWarning = threshold.isExceeded(value, false);
        
        if (!isCritical && !isWarning) {
            return;
        }
        
        HealthStatus severity = isCritical ? HealthStatus.CRITICAL : HealthStatus.WARNING;
        Double thresholdValue = threshold.getThreshold(isCritical);
        
        String valueStr = metricName.contains("usage") || metricName.contains("ratio") 
            ? String.format("%.1f%%", value * 100) 
            : String.format("%.0f", value);
        String thresholdStr = metricName.contains("usage") || metricName.contains("ratio")
            ? String.format("<%.0f%%", thresholdValue * 100)
            : String.format("<%.0f", thresholdValue);
        
        String title = metricLabel + (isCritical ? "过高" : "偏高");
        String description = String.format("%s为 %s，%s", metricLabel, valueStr, 
            isCritical ? "需要立即关注" : "建议关注");
        
        issues.add(createIssue("resource", severity, title, description, 
            metricName, valueStr, thresholdStr));
    }
    
    private AnalysisResult.DetectedIssue createIssue(String type, HealthStatus severity,
            String title, String description, String metric, String value, String normalRange) {
        AnalysisResult.DetectedIssue issue = new AnalysisResult.DetectedIssue();
        issue.setIssueType(type);
        issue.setSeverity(severity);
        issue.setTitle(title);
        issue.setDescription(description);
        issue.getRelatedMetrics().add(metric);
        issue.setMetricValue(value);
        issue.setNormalRange(normalRange);
        issue.setDetectedTime(LocalDateTime.now());
        return issue;
    }
    
    private void generateRecommendations(AnalysisResult result, List<AnalysisResult.DetectedIssue> issues) {
        for (AnalysisResult.DetectedIssue issue : issues) {
            AnalysisResult.Recommendation rec = new AnalysisResult.Recommendation();
            rec.setRecommendationType(issue.getIssueType());
            rec.setPriority(issue.getSeverity() == HealthStatus.CRITICAL ? 1 : 2);
            rec.setTitle("解决" + issue.getTitle());
            rec.setDescription("建议检查并优化相关配置");
            
            if ("memory_usage".equals(issue.getRelatedMetrics().get(0))) {
                rec.getActionSteps().add("检查是否存在内存泄漏");
                rec.getActionSteps().add("考虑增加堆内存配置");
                rec.setExpectedOutcome("降低内存使用率，避免OOM风险");
            } else if ("gc_time".equals(issue.getRelatedMetrics().get(0))) {
                rec.getActionSteps().add("分析GC日志找出频繁GC的原因");
                rec.getActionSteps().add("优化对象创建和生命周期管理");
                rec.getActionSteps().add("考虑调整GC策略");
                rec.setExpectedOutcome("减少GC停顿时间，提升性能");
            }
            
            result.addRecommendation(rec);
        }
    }
    
    private AnalysisResult createEmptyResult(String analysisType) {
        AnalysisResult result = new AnalysisResult();
        result.setResultId(UUID.randomUUID().toString());
        result.setAnalysisTime(LocalDateTime.now());
        result.setAnalysisType(analysisType);
        result.setHealthStatus(HealthStatus.UNKNOWN);
        return result;
    }
}
