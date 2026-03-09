package com.aiops.bigdata.service.analysis.impl;

import com.aiops.bigdata.entity.analysis.AnalysisResult;
import com.aiops.bigdata.entity.common.enums.HealthStatus;
import com.aiops.bigdata.entity.feature.ComponentMetricFeatures;
import com.aiops.bigdata.entity.feature.SparkAppFeatures;
import com.aiops.bigdata.entity.metrics.ComponentMetrics;
import com.aiops.bigdata.entity.metrics.SparkAppMetrics;
import com.aiops.bigdata.service.analysis.AIAnalysisService;
import com.aiops.bigdata.service.analyzer.ComponentMetricsAnalyzer;
import com.aiops.bigdata.service.analyzer.SparkAppAnalyzer;
import com.aiops.bigdata.service.feature.FeatureExtractor;
import com.aiops.bigdata.service.llm.LLMService;
import com.aiops.bigdata.service.llm.LLMService.*;
import com.aiops.bigdata.service.prompt.PromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI分析服务实现类
 * 整合特征提取、Prompt构建、LLM调用的完整流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIAnalysisServiceImpl implements AIAnalysisService {
    
    private final FeatureExtractor featureExtractor;
    private final PromptBuilder promptBuilder;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    
    // 降级分析器（当LLM不可用时使用）
    private final ComponentMetricsAnalyzer componentMetricsAnalyzer;
    private final SparkAppAnalyzer sparkAppAnalyzer;
    
    @Override
    public AnalysisResult analyzeComponent(ComponentMetrics metrics) {
        log.info("开始分析组件指标: service={}, component={}", metrics.getService(), metrics.getComponent());
        
        // 1. 特征提取
        ComponentMetricFeatures features = featureExtractor.extract(metrics);
        
        // 2. 分析
        return analyzeWithFeatures(features, metrics.getCluster());
    }
    
    @Override
    public AnalysisResult analyzeComponentTimeSeries(List<ComponentMetrics> metricsHistory) {
        if (metricsHistory == null || metricsHistory.isEmpty()) {
            return createEmptyResult("component");
        }
        
        log.info("开始分析组件时间序列: 数据点数={}", metricsHistory.size());
        
        // 1. 时间序列特征提取
        ComponentMetricFeatures features = featureExtractor.extractFromTimeSeries(metricsHistory);
        
        // 2. 分析
        ComponentMetrics latest = metricsHistory.get(metricsHistory.size() - 1);
        return analyzeWithFeatures(features, latest.getCluster());
    }
    
    @Override
    public AnalysisResult analyzeSparkApp(SparkAppMetrics metrics) {
        log.info("开始分析Spark作业: jobId={}, appName={}", metrics.getJobId(), metrics.getAppName());
        
        // 1. 特征提取
        SparkAppFeatures features = featureExtractor.extract(metrics);
        
        // 2. 分析
        return analyzeSparkWithFeatures(features, metrics.getCluster());
    }
    
    @Override
    public AnalysisResult analyzeWithFeatures(ComponentMetricFeatures features, String cluster) {
        long startTime = System.currentTimeMillis();
        
        AnalysisResult result = new AnalysisResult();
        result.setResultId(UUID.randomUUID().toString());
        result.setCluster(cluster);
        result.setAnalysisTime(LocalDateTime.now());
        result.setAnalysisType("component");
        result.setTargetId(String.format("%s:%s:%s", 
            features.getService(), features.getComponent(), features.getInstance()));
        result.setTargetName(features.getService() + "/" + features.getComponent());
        
        try {
            // 1. 构建Prompt
            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = promptBuilder.buildComponentAnalysisPrompt(features, cluster);
            
            log.debug("构建Prompt完成: systemPrompt长度={}, userPrompt长度={}", 
                systemPrompt.length(), userPrompt.length());
            
            // 2. 调用LLM（支持Tool Calling）
            LLMResponse response = callLLMWithToolSupport(systemPrompt, userPrompt);
            
            // 3. 解析结果
            parseAnalysisResult(response.content(), result);
            
            result.setAnalysisDuration(System.currentTimeMillis() - startTime);
            result.setModelName(llmService.getModelName());
            
            log.info("组件分析完成: healthStatus={}, duration={}ms", 
                result.getHealthStatus(), result.getAnalysisDuration());
            
        } catch (Exception e) {
            log.error("组件分析失败: {}, 使用降级分析器", e.getMessage());
            
            // 使用降级分析器
            try {
                ComponentMetrics fallbackMetrics = convertFeaturesToMetrics(features);
                result = componentMetricsAnalyzer.analyze(fallbackMetrics, cluster);
                result.setModelName("rule-based-fallback");
            } catch (Exception fallbackError) {
                log.error("降级分析也失败: {}", fallbackError.getMessage());
                result.setHealthStatus(HealthStatus.UNKNOWN);
                result.setHealthScore(0);
                AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
                diagnosis.setSummary("分析失败: " + e.getMessage());
                result.setDiagnosis(diagnosis);
            }
        }
        
        return result;
    }
    
    @Override
    public AnalysisResult analyzeSparkWithFeatures(SparkAppFeatures features, String cluster) {
        long startTime = System.currentTimeMillis();
        
        AnalysisResult result = new AnalysisResult();
        result.setResultId(UUID.randomUUID().toString());
        result.setCluster(cluster);
        result.setAnalysisTime(LocalDateTime.now());
        result.setAnalysisType("spark_app");
        result.setTargetId(features.getJobId());
        result.setTargetName(features.getAppName());
        
        try {
            // 1. 构建Prompt
            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = promptBuilder.buildSparkAnalysisPrompt(features, cluster);
            
            // 2. 调用LLM
            LLMResponse response = callLLMWithToolSupport(systemPrompt, userPrompt);
            
            // 3. 解析结果
            parseAnalysisResult(response.content(), result);
            
            result.setAnalysisDuration(System.currentTimeMillis() - startTime);
            result.setModelName(llmService.getModelName());
            
            log.info("Spark作业分析完成: healthStatus={}, duration={}ms", 
                result.getHealthStatus(), result.getAnalysisDuration());
            
        } catch (Exception e) {
            log.error("Spark作业分析失败: {}, 使用降级分析器", e.getMessage());
            
            // 使用降级分析器
            try {
                SparkAppMetrics fallbackMetrics = convertSparkFeaturesToMetrics(features);
                result = sparkAppAnalyzer.analyze(fallbackMetrics, cluster);
                result.setModelName("rule-based-fallback");
            } catch (Exception fallbackError) {
                log.error("降级分析也失败: {}", fallbackError.getMessage());
                result.setHealthStatus(HealthStatus.UNKNOWN);
                result.setHealthScore(0);
                AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
                diagnosis.setSummary("分析失败: " + e.getMessage());
                result.setDiagnosis(diagnosis);
            }
        }
        
        return result;
    }
    
    /**
     * 调用LLM并处理Tool Calling
     */
    private LLMResponse callLLMWithToolSupport(String systemPrompt, String userPrompt) {
        // 定义可用工具
        List<ToolDefinition> tools = List.of(
            new ToolDefinition("get_realtime_metrics", 
                "获取集群组件的实时指标数据",
                Map.of(
                    "cluster", new ParameterSchema("string", "集群名称", true),
                    "service", new ParameterSchema("string", "服务类型", true),
                    "component", new ParameterSchema("string", "组件类型", false),
                    "instance", new ParameterSchema("string", "实例标识", false)
                )),
            new ToolDefinition("get_long_term_status",
                "获取集群的长期状态数据",
                Map.of(
                    "cluster", new ParameterSchema("string", "集群名称", true),
                    "days", new ParameterSchema("integer", "查询最近N天", false)
                )),
            new ToolDefinition("get_recent_events",
                "获取集群近期的告警和事件",
                Map.of(
                    "cluster", new ParameterSchema("string", "集群名称", true),
                    "severity", new ParameterSchema("string", "严重程度过滤", false),
                    "limit", new ParameterSchema("integer", "返回数量限制", false)
                ))
        );
        
        // 第一次调用
        LLMResponse response = llmService.analyze(systemPrompt, userPrompt, tools);
        
        // 处理Tool Calling循环
        int maxIterations = 3;
        int iteration = 0;
        
        while (!response.finished() && "tool_calls".equals(response.finishReason()) && iteration < maxIterations) {
            iteration++;
            log.info("LLM请求调用工具 (迭代{}): {}个工具", iteration, response.toolCalls().size());
            
            // 执行所有工具调用
            StringBuilder toolResults = new StringBuilder();
            toolResults.append("工具调用结果:\n\n");
            
            for (ToolCall toolCall : response.toolCalls()) {
                log.info("执行工具: {} with arguments: {}", toolCall.name(), toolCall.arguments());
                
                ToolResult result = llmService.executeTool(toolCall.name(), toolCall.arguments());
                
                if (result.success()) {
                    toolResults.append("【").append(toolCall.name()).append("】\n");
                    toolResults.append(result.result()).append("\n\n");
                } else {
                    toolResults.append("【").append(toolCall.name()).append("】执行失败: ")
                        .append(result.error()).append("\n\n");
                }
            }
            
            // 将工具结果添加到对话中，再次调用LLM
            String newUserPrompt = userPrompt + "\n\n" + toolResults + 
                "\n请基于以上工具结果继续分析。";
            
            response = llmService.analyze(systemPrompt, newUserPrompt, tools);
        }
        
        return response;
    }
    
    /**
     * 解析LLM返回的JSON结果
     */
    private void parseAnalysisResult(String content, AnalysisResult result) {
        if (content == null || content.isEmpty()) {
            result.setHealthStatus(HealthStatus.UNKNOWN);
            return;
        }
        
        try {
            // 尝试解析JSON
            JsonNode root = objectMapper.readTree(content);
            
            // 解析健康状态
            String healthStatus = root.has("health_status") 
                ? root.get("health_status").asText() : "unknown";
            result.setHealthStatus(HealthStatus.fromCode(healthStatus));
            
            // 解析健康评分
            if (root.has("health_score")) {
                result.setHealthScore(root.get("health_score").asInt());
            } else {
                result.calculateHealthScore();
            }
            
            // 解析诊断信息
            if (root.has("diagnosis")) {
                JsonNode diagnosisNode = root.get("diagnosis");
                AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
                
                if (diagnosisNode.has("summary")) {
                    diagnosis.setSummary(diagnosisNode.get("summary").asText());
                }
                
                if (diagnosisNode.has("root_cause")) {
                    diagnosis.setRootCauseAnalysis(diagnosisNode.get("root_cause").asText());
                }
                
                if (diagnosisNode.has("issues") && diagnosisNode.get("issues").isArray()) {
                    for (JsonNode issueNode : diagnosisNode.get("issues")) {
                        AnalysisResult.DetectedIssue issue = new AnalysisResult.DetectedIssue();
                        
                        if (issueNode.has("type")) {
                            issue.setIssueType(issueNode.get("type").asText());
                        }
                        if (issueNode.has("severity")) {
                            issue.setSeverity(HealthStatus.fromCode(issueNode.get("severity").asText()));
                        }
                        if (issueNode.has("title")) {
                            issue.setTitle(issueNode.get("title").asText());
                        }
                        if (issueNode.has("description")) {
                            issue.setDescription(issueNode.get("description").asText());
                        }
                        if (issueNode.has("related_metrics") && issueNode.get("related_metrics").isArray()) {
                            for (JsonNode metric : issueNode.get("related_metrics")) {
                                issue.getRelatedMetrics().add(metric.asText());
                            }
                        }
                        issue.setDetectedTime(LocalDateTime.now());
                        
                        diagnosis.getIssues().add(issue);
                    }
                }
                
                result.setDiagnosis(diagnosis);
            }
            
            // 解析建议
            if (root.has("recommendations") && root.get("recommendations").isArray()) {
                for (JsonNode recNode : root.get("recommendations")) {
                    AnalysisResult.Recommendation rec = new AnalysisResult.Recommendation();
                    
                    if (recNode.has("type")) {
                        rec.setRecommendationType(recNode.get("type").asText());
                    }
                    if (recNode.has("priority")) {
                        rec.setPriority(recNode.get("priority").asInt());
                    }
                    if (recNode.has("title")) {
                        rec.setTitle(recNode.get("title").asText());
                    }
                    if (recNode.has("description")) {
                        rec.setDescription(recNode.get("description").asText());
                    }
                    if (recNode.has("actions") && recNode.get("actions").isArray()) {
                        for (JsonNode action : recNode.get("actions")) {
                            rec.getActionSteps().add(action.asText());
                        }
                    }
                    
                    result.addRecommendation(rec);
                }
            }
            
            // 保存原始响应
            result.setRawResponse(content);
            
        } catch (Exception e) {
            log.warn("解析LLM响应失败，使用原始内容: {}", e.getMessage());
            
            // 如果JSON解析失败，将原始内容作为摘要
            AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
            diagnosis.setSummary(content.length() > 500 ? content.substring(0, 500) + "..." : content);
            result.setDiagnosis(diagnosis);
            result.setRawResponse(content);
            result.setHealthStatus(HealthStatus.UNKNOWN);
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
    
    /**
     * 将组件特征转换为组件指标（用于降级分析）
     */
    private ComponentMetrics convertFeaturesToMetrics(ComponentMetricFeatures features) {
        ComponentMetrics metrics = new ComponentMetrics();
        metrics.setCluster(features.getCluster());
        metrics.setService(features.getService());
        metrics.setComponent(features.getComponent());
        metrics.setInstance(features.getInstance());
        
        // 从特征中提取当前值
        if (features.getCpuUsage() != null) {
            metrics.addMetric("cpu_usage", features.getCpuUsage().getCurrent());
        }
        if (features.getMemoryUsage() != null) {
            metrics.addMetric("memory_usage", features.getMemoryUsage().getCurrent());
        }
        if (features.getGcTime() != null) {
            metrics.addMetric("gc_time", features.getGcTime().getCurrent());
        }
        if (features.getHeapUsage() != null) {
            metrics.addMetric("heap_usage", features.getHeapUsage().getCurrent());
        }
        
        return metrics;
    }
    
    /**
     * 将Spark特征转换为Spark指标（用于降级分析）
     */
    private SparkAppMetrics convertSparkFeaturesToMetrics(SparkAppFeatures features) {
        SparkAppMetrics metrics = new SparkAppMetrics();
        metrics.setCluster(features.getCluster());
        metrics.setJobId(features.getJobId());
        metrics.setAppName(features.getAppName());
        metrics.setDuration(features.getDuration());
        metrics.setExecutorCount(features.getExecutorCount());
        metrics.setExecutorMemoryGB(features.getExecutorMemoryGB());
        metrics.setExecutorCores(features.getExecutorCores());
        metrics.setInputSize(features.getInputSize());
        metrics.setOutputSize(features.getOutputSize());
        metrics.setShuffleRead(features.getShuffleRead());
        metrics.setShuffleWrite(features.getShuffleWrite());
        metrics.setSkewRatio(features.getSkewRatio());
        metrics.setShuffleRatio(features.getShuffleRatio());
        
        return metrics;
    }
}
