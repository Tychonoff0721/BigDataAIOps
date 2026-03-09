package com.aiops.bigdata.service.analyzer.impl;

import com.aiops.bigdata.entity.analysis.AnalysisResult;
import com.aiops.bigdata.entity.common.enums.HealthStatus;
import com.aiops.bigdata.entity.config.ThresholdConfig;
import com.aiops.bigdata.entity.metrics.SparkAppMetrics;
import com.aiops.bigdata.service.analyzer.SparkAppAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Spark作业分析服务实现类
 * 负责分析Spark应用程序的性能和健康状态
 * 
 * 这是一个基于规则的降级分析器，当LLM服务不可用时使用。
 * 主要分析逻辑在 AIAnalysisServiceImpl 中实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SparkAppAnalyzerImpl implements SparkAppAnalyzer {
    
    private final ThresholdConfig thresholdConfig;
    
    @Override
    public AnalysisResult analyze(SparkAppMetrics metrics) {
        return analyze(metrics, metrics.getCluster());
    }
    
    @Override
    public AnalysisResult analyze(SparkAppMetrics metrics, String cluster) {
        log.info("开始分析Spark作业: jobId={}, appName={}", metrics.getJobId(), metrics.getAppName());
        
        long startTime = System.currentTimeMillis();
        
        AnalysisResult result = new AnalysisResult();
        result.setResultId(UUID.randomUUID().toString());
        result.setCluster(metrics.getCluster());
        result.setAnalysisTime(LocalDateTime.now());
        result.setAnalysisType("spark_app");
        result.setTargetId(metrics.getUniqueId());
        result.setTargetName(metrics.getAppName());
        
        // 基于规则的分析（作为LLM分析的降级方案）
        // 主要分析逻辑在 AIAnalysisServiceImpl 中实现
        performRuleBasedAnalysis(metrics, result);
        
        result.setAnalysisDuration(System.currentTimeMillis() - startTime);
        result.calculateHealthScore();
        
        log.info("Spark作业分析完成: targetId={}, healthStatus={}, duration={}ms",
                result.getTargetId(), result.getHealthStatus(), result.getAnalysisDuration());
        
        return result;
    }
    
    @Override
    public List<AnalysisResult> analyzeBatch(List<SparkAppMetrics> metricsList) {
        List<AnalysisResult> results = new ArrayList<>();
        for (SparkAppMetrics metrics : metricsList) {
            results.add(analyze(metrics));
        }
        return results;
    }
    
    @Override
    public AnalysisResult analyzeStageBottlenecks(SparkAppMetrics metrics) {
        AnalysisResult result = createBaseResult(metrics);
        result.setAnalysisType("stage_bottleneck");
        
        AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
        List<AnalysisResult.DetectedIssue> issues = new ArrayList<>();
        
        // 分析每个Stage
        for (SparkAppMetrics.StageInfo stage : metrics.getStages()) {
            // 检查执行时长
            if (stage.getDuration() != null && stage.getDuration() > 60000) { // > 1分钟
                issues.add(createStageIssue(stage, "duration",
                    HealthStatus.WARNING, "Stage执行时间过长",
                    String.format("Stage %s 执行时间 %d ms", stage.getStageId(), stage.getDuration())));
            }
            
            // 检查GC时间
            if (stage.getGcTime() != null && stage.getGcTime() > stage.getDuration() * 0.1) {
                issues.add(createStageIssue(stage, "gc",
                    HealthStatus.WARNING, "Stage GC时间占比过高",
                    String.format("Stage %s GC时间 %d ms，占比 %.1f%%", 
                        stage.getStageId(), stage.getGcTime(), 
                        stage.getDuration() > 0 ? (double)stage.getGcTime() / stage.getDuration() * 100 : 0)));
            }
            
            // 检查Shuffle
            long shuffleTotal = (stage.getShuffleReadBytes() != null ? stage.getShuffleReadBytes() : 0) +
                                (stage.getShuffleWriteBytes() != null ? stage.getShuffleWriteBytes() : 0);
            if (shuffleTotal > 1_000_000_000L) { // > 1GB
                issues.add(createStageIssue(stage, "shuffle",
                    HealthStatus.WARNING, "Stage Shuffle数据量大",
                    String.format("Stage %s Shuffle数据量 %.2f GB", 
                        stage.getStageId(), shuffleTotal / 1e9)));
            }
            
            // 检查数据倾斜
            if (stage.getHasSkew() != null && stage.getHasSkew()) {
                double skewRatio = stage.getSkewRatio();
                ThresholdConfig.ThresholdValue skewThreshold = thresholdConfig.getSparkAppThreshold("skew-ratio");
                HealthStatus severity = skewThreshold != null && skewRatio > skewThreshold.getCritical() 
                    ? HealthStatus.CRITICAL : HealthStatus.WARNING;
                issues.add(createStageIssue(stage, "skew", severity,
                    "Stage存在数据倾斜",
                    String.format("Stage %s 数据倾斜率 %.1f", stage.getStageId(), skewRatio)));
            }
        }
        
        diagnosis.setIssues(issues);
        diagnosis.setSummary(issues.isEmpty() ? "未检测到明显的Stage瓶颈" :
            String.format("检测到 %d 个Stage存在潜在问题", issues.size()));
        result.setDiagnosis(diagnosis);
        determineHealthStatus(result, issues);
        
        return result;
    }
    
    @Override
    public AnalysisResult analyzeDataSkew(SparkAppMetrics metrics) {
        AnalysisResult result = createBaseResult(metrics);
        result.setAnalysisType("data_skew");
        
        AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
        List<AnalysisResult.DetectedIssue> issues = new ArrayList<>();
        
        ThresholdConfig.ThresholdValue skewThreshold = thresholdConfig.getSparkAppThreshold("skew-ratio");
        double warningThreshold = skewThreshold != null ? skewThreshold.getWarning() : 3.0;
        double criticalThreshold = skewThreshold != null ? skewThreshold.getCritical() : 10.0;
        
        for (SparkAppMetrics.StageInfo stage : metrics.getStages()) {
            if (stage.getSkewRatio() > warningThreshold) {
                HealthStatus severity = stage.getSkewRatio() > criticalThreshold ?
                    HealthStatus.CRITICAL : HealthStatus.WARNING;
                
                AnalysisResult.DetectedIssue issue = new AnalysisResult.DetectedIssue();
                issue.setIssueType("data_skew");
                issue.setSeverity(severity);
                issue.setTitle("Stage " + stage.getStageId() + " 存在数据倾斜");
                issue.setDescription(String.format(
                    "最大任务时长 %d ms，中位任务时长 %d ms，倾斜率 %.1f",
                    stage.getMaxTaskTime(), stage.getMedianTaskTime(), stage.getSkewRatio()));
                issue.setRelatedComponent(stage.getStageId());
                issue.setDetectedTime(LocalDateTime.now());
                issues.add(issue);
            }
        }
        
        diagnosis.setIssues(issues);
        diagnosis.setSummary(issues.isEmpty() ? "未检测到数据倾斜问题" :
            String.format("检测到 %d 个Stage存在数据倾斜", issues.size()));
        diagnosis.setRootCauseAnalysis(issues.isEmpty() ? null :
            "可能原因：1) Join Key分布不均匀 2) GroupBy Key值倾斜 3) 数据源本身倾斜");
        result.setDiagnosis(diagnosis);
        determineHealthStatus(result, issues);
        
        // 添加优化建议
        if (!issues.isEmpty()) {
            AnalysisResult.Recommendation rec = new AnalysisResult.Recommendation();
            rec.setRecommendationType("code");
            rec.setPriority(1);
            rec.setTitle("解决数据倾斜");
            rec.setDescription("建议对倾斜的Key进行特殊处理");
            rec.getActionSteps().add("对倾斜Key进行加盐处理");
            rec.getActionSteps().add("使用broadcast join替代shuffle join");
            rec.getActionSteps().add("考虑对数据进行预聚合");
            rec.setExpectedOutcome("消除数据倾斜，提升作业性能");
            result.addRecommendation(rec);
        }
        
        return result;
    }
    
    @Override
    public AnalysisResult analyzeResourceConfiguration(SparkAppMetrics metrics) {
        AnalysisResult result = createBaseResult(metrics);
        result.setAnalysisType("resource_config");
        
        AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
        List<AnalysisResult.DetectedIssue> issues = new ArrayList<>();
        
        // 分析Executor数量
        if (metrics.getExecutorCount() != null) {
            if (metrics.getExecutorCount() > 100) {
                issues.add(createConfigIssue("executor_count", HealthStatus.WARNING,
                    "Executor数量较多",
                    String.format("当前Executor数量: %d", metrics.getExecutorCount()),
                    "考虑减少Executor数量，增加每个Executor的核心数"));
            }
        }
        
        // 分析Shuffle比例与资源配置的关系
        double shuffleRatio = metrics.getShuffleRatio();
        ThresholdConfig.ThresholdValue shuffleThreshold = thresholdConfig.getSparkAppThreshold("shuffle-ratio");
        if (shuffleThreshold != null && shuffleRatio > shuffleThreshold.getWarning()) {
            HealthStatus severity = shuffleRatio > shuffleThreshold.getCritical() 
                ? HealthStatus.CRITICAL : HealthStatus.WARNING;
            issues.add(createConfigIssue("shuffle_config", severity,
                "Shuffle比例较高",
                String.format("Shuffle数据占比: %.1f%%", shuffleRatio * 100),
                "考虑增加shuffle partition数量或优化shuffle配置"));
        }
        
        // 分析Executor内存使用
        ThresholdConfig.ThresholdValue executorMemThreshold = thresholdConfig.getSparkAppThreshold("executor-memory-usage");
        for (SparkAppMetrics.ExecutorInfo executor : metrics.getExecutors()) {
            double memoryRate = executor.getMemoryUsageRate();
            if (executorMemThreshold != null && memoryRate > executorMemThreshold.getWarning()) {
                HealthStatus severity = memoryRate > executorMemThreshold.getCritical() 
                    ? HealthStatus.CRITICAL : HealthStatus.WARNING;
                issues.add(createConfigIssue("executor_memory", severity,
                    "Executor " + executor.getExecutorId() + " 内存使用率高",
                    String.format("内存使用率: %.1f%%", memoryRate * 100),
                    "考虑增加Executor内存配置"));
                break; // 只报告一次
            }
        }
        
        diagnosis.setIssues(issues);
        diagnosis.setSummary(issues.isEmpty() ? "资源配置合理" :
            String.format("发现 %d 个资源配置优化点", issues.size()));
        result.setDiagnosis(diagnosis);
        determineHealthStatus(result, issues);
        
        return result;
    }
    
    @Override
    public AnalysisResult generateOptimizationSuggestions(SparkAppMetrics metrics) {
        AnalysisResult result = analyze(metrics);
        result.setAnalysisType("optimization");
        
        // 综合分析并生成优化建议
        List<AnalysisResult.Recommendation> recommendations = new ArrayList<>();
        
        // Shuffle优化建议
        ThresholdConfig.ThresholdValue shuffleThreshold = thresholdConfig.getSparkAppThreshold("shuffle-ratio");
        double shuffleWarningThreshold = shuffleThreshold != null ? shuffleThreshold.getWarning() : 0.5;
        if (metrics.getShuffleRatio() > shuffleWarningThreshold) {
            recommendations.add(createOptimization("shuffle", 1,
                "优化Shuffle配置",
                "Shuffle数据量较大，建议优化",
                List.of("调整spark.sql.shuffle.partitions",
                       "考虑使用AQE(Apaptive Query Execution)",
                       "优化join策略")));
        }
        
        // 数据倾斜优化建议
        ThresholdConfig.ThresholdValue skewThreshold = thresholdConfig.getSparkAppThreshold("skew-ratio");
        double skewWarningThreshold = skewThreshold != null ? skewThreshold.getWarning() : 3.0;
        for (SparkAppMetrics.StageInfo stage : metrics.getStages()) {
            if (stage.getSkewRatio() > skewWarningThreshold) {
                recommendations.add(createOptimization("skew", 1,
                    "处理数据倾斜",
                    "Stage " + stage.getStageId() + " 存在数据倾斜",
                    List.of("对倾斜Key加盐", "使用broadcast join", "数据预聚合")));
                break;
            }
        }
        
        // 资源配置优化建议
        if (metrics.getExecutorCount() != null && metrics.getDuration() != null) {
            if (metrics.getDuration() > 300000 && metrics.getExecutorCount() < 20) {
                recommendations.add(createOptimization("resource", 2,
                    "增加并行度",
                    "作业执行时间较长，可考虑增加资源",
                    List.of("增加Executor数量", "增加每个Executor的核心数")));
            }
        }
        
        result.setRecommendations(recommendations);
        
        return result;
    }
    
    private void performRuleBasedAnalysis(SparkAppMetrics metrics, AnalysisResult result) {
        AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
        List<AnalysisResult.DetectedIssue> issues = new ArrayList<>();
        
        // 检查作业执行时长
        ThresholdConfig.ThresholdValue durationThreshold = thresholdConfig.getSparkAppThreshold("duration");
        if (metrics.getDuration() != null && durationThreshold != null 
            && metrics.getDuration() > durationThreshold.getWarning()) {
            HealthStatus severity = metrics.getDuration() > durationThreshold.getCritical() 
                ? HealthStatus.CRITICAL : HealthStatus.WARNING;
            issues.add(createIssue("performance", severity,
                "作业执行时间较长",
                String.format("作业执行时间: %d 秒", metrics.getDuration() / 1000),
                "duration", metrics.getDuration() + "ms", null));
        }
        
        // 检查Shuffle比例
        double shuffleRatio = metrics.getShuffleRatio();
        ThresholdConfig.ThresholdValue shuffleThreshold = thresholdConfig.getSparkAppThreshold("shuffle-ratio");
        if (shuffleThreshold != null) {
            if (shuffleRatio > shuffleThreshold.getCritical()) {
                issues.add(createIssue("performance", HealthStatus.CRITICAL,
                    "Shuffle数据量过大",
                    String.format("Shuffle占比: %.1f%%", shuffleRatio * 100),
                    "shuffle_ratio", String.format("%.1f%%", shuffleRatio * 100), "<50%"));
            } else if (shuffleRatio > shuffleThreshold.getWarning()) {
                issues.add(createIssue("performance", HealthStatus.WARNING,
                    "Shuffle数据量偏大",
                    String.format("Shuffle占比: %.1f%%", shuffleRatio * 100),
                    "shuffle_ratio", String.format("%.1f%%", shuffleRatio * 100), "<50%"));
            }
        }
        
        // 检查失败任务
        for (SparkAppMetrics.StageInfo stage : metrics.getStages()) {
            if (stage.getFailedTasks() != null && stage.getFailedTasks() > 0) {
                issues.add(createIssue("error", HealthStatus.CRITICAL,
                    "Stage存在失败任务",
                    String.format("Stage %s 有 %d 个失败任务", 
                        stage.getStageId(), stage.getFailedTasks()),
                    "failed_tasks", stage.getFailedTasks().toString(), "0"));
            }
        }
        
        // 检查数据倾斜
        ThresholdConfig.ThresholdValue skewThreshold2 = thresholdConfig.getSparkAppThreshold("skew-ratio");
        double skewWarning = skewThreshold2 != null ? skewThreshold2.getWarning() : 3.0;
        double skewCritical = skewThreshold2 != null ? skewThreshold2.getCritical() : 10.0;
        for (SparkAppMetrics.StageInfo stage : metrics.getStages()) {
            if (stage.getSkewRatio() > skewWarning) {
                HealthStatus severity = stage.getSkewRatio() > skewCritical ?
                    HealthStatus.CRITICAL : HealthStatus.WARNING;
                issues.add(createIssue("skew", severity,
                    "存在数据倾斜",
                    String.format("Stage %s 倾斜率: %.1f", stage.getStageId(), stage.getSkewRatio()),
                    "skew_ratio", String.format("%.1f", stage.getSkewRatio()), "<3.0"));
            }
        }
        
        diagnosis.setIssues(issues);
        diagnosis.setSummary(issues.isEmpty() ? "作业运行正常" :
            String.format("检测到 %d 个问题需要关注", issues.size()));
        result.setDiagnosis(diagnosis);
        determineHealthStatus(result, issues);
    }
    
    private AnalysisResult createBaseResult(SparkAppMetrics metrics) {
        AnalysisResult result = new AnalysisResult();
        result.setResultId(UUID.randomUUID().toString());
        result.setCluster(metrics.getCluster());
        result.setAnalysisTime(LocalDateTime.now());
        result.setTargetId(metrics.getUniqueId());
        result.setTargetName(metrics.getAppName());
        return result;
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
    
    private AnalysisResult.DetectedIssue createStageIssue(SparkAppMetrics.StageInfo stage,
            String type, HealthStatus severity, String title, String description) {
        AnalysisResult.DetectedIssue issue = new AnalysisResult.DetectedIssue();
        issue.setIssueType(type);
        issue.setSeverity(severity);
        issue.setTitle(title);
        issue.setDescription(description);
        issue.setRelatedComponent(stage.getStageId());
        issue.setDetectedTime(LocalDateTime.now());
        return issue;
    }
    
    private AnalysisResult.DetectedIssue createConfigIssue(String type, HealthStatus severity,
            String title, String description, String suggestion) {
        AnalysisResult.DetectedIssue issue = new AnalysisResult.DetectedIssue();
        issue.setIssueType("config_" + type);
        issue.setSeverity(severity);
        issue.setTitle(title);
        issue.setDescription(description);
        issue.setDetectedTime(LocalDateTime.now());
        return issue;
    }
    
    private void determineHealthStatus(AnalysisResult result, List<AnalysisResult.DetectedIssue> issues) {
        if (issues.stream().anyMatch(i -> i.getSeverity() == HealthStatus.CRITICAL)) {
            result.setHealthStatus(HealthStatus.CRITICAL);
        } else if (issues.stream().anyMatch(i -> i.getSeverity() == HealthStatus.WARNING)) {
            result.setHealthStatus(HealthStatus.WARNING);
        } else {
            result.setHealthStatus(HealthStatus.HEALTHY);
        }
    }
    
    private AnalysisResult.Recommendation createOptimization(String type, int priority,
            String title, String description, List<String> actions) {
        AnalysisResult.Recommendation rec = new AnalysisResult.Recommendation();
        rec.setRecommendationType(type);
        rec.setPriority(priority);
        rec.setTitle(title);
        rec.setDescription(description);
        rec.setActionSteps(actions);
        rec.setExpectedOutcome("提升作业执行性能");
        return rec;
    }
}
