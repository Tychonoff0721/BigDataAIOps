package com.aiops.bigdata.service.analysis.impl;

import com.aiops.bigdata.entity.analysis.AnalysisResult;
import com.aiops.bigdata.entity.common.enums.HealthStatus;
import com.aiops.bigdata.entity.feature.ComponentMetricFeatures;
import com.aiops.bigdata.entity.feature.SparkAppFeatures;
import com.aiops.bigdata.entity.feature.TimeSeriesFeatures;
import com.aiops.bigdata.entity.metrics.ComponentMetrics;
import com.aiops.bigdata.entity.metrics.SparkAppMetrics;
import com.aiops.bigdata.service.analysis.AIAnalysisService;
import com.aiops.bigdata.service.analyzer.ComponentMetricsAnalyzer;
import com.aiops.bigdata.service.analyzer.SparkAppAnalyzer;
import com.aiops.bigdata.service.context.MetricsTimeSeriesStore;
import com.aiops.bigdata.service.context.SparkJobStore;
import com.aiops.bigdata.service.feature.FeatureExtractor;
import com.aiops.bigdata.service.llm.LLMService;
import com.aiops.bigdata.service.llm.LLMService.*;
import com.aiops.bigdata.service.prompt.PromptBuilder;
import com.aiops.bigdata.service.tool.MetricsTimeSeriesTool;
import com.aiops.bigdata.service.tool.SparkJobTool;
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
 * 
 * @deprecated 请使用 {@link com.aiops.bigdata.service.ai.impl.AIAnalysisServiceImpl} 替代
 *             新版本基于 Spring AI 框架，代码更简洁，功能更强大
 */
@Slf4j
@Service("legacyAIAnalysisService")
@RequiredArgsConstructor
@Deprecated
public class AIAnalysisServiceImpl implements AIAnalysisService {
    
    private final FeatureExtractor featureExtractor;
    private final PromptBuilder promptBuilder;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    
    // 数据存储服务
    private final MetricsTimeSeriesStore metricsTimeSeriesStore;
    private final SparkJobStore sparkJobStore;
    
    // Tool服务
    private final MetricsTimeSeriesTool metricsTimeSeriesTool;
    private final SparkJobTool sparkJobTool;
    
    // 降级分析器（当LLM不可用时使用）
    private final ComponentMetricsAnalyzer componentMetricsAnalyzer;
    private final SparkAppAnalyzer sparkAppAnalyzer;
    
    // ============ 新版接口实现 ============
    
    @Override
    public AnalysisResult analyzeTimeSeries(String storageId, String cluster) {
        long startTime = System.currentTimeMillis();
        
        log.info("开始分析时序数据: storageId={}", storageId);
        
        AnalysisResult result = new AnalysisResult();
        result.setResultId(UUID.randomUUID().toString());
        result.setCluster(cluster);
        result.setAnalysisTime(LocalDateTime.now());
        result.setAnalysisType("timeseries");
        result.setTargetId(storageId);
        
        try {
            // 获取数据摘要用于构建目标名称
            String summary = metricsTimeSeriesStore.getTimeSeriesSummary(storageId);
            result.setTargetName(extractTargetName(summary));
            
            // 构建Prompt - 告诉LLM有数据可用，让它通过Tool查看
            String systemPrompt = buildTimeSeriesSystemPrompt();
            String userPrompt = buildTimeSeriesUserPrompt(storageId, cluster);
            
            // 调用LLM（支持Tool Calling）
            LLMResponse response = callLLMWithTimeSeriesTools(systemPrompt, userPrompt, storageId);
            
            // 解析结果
            parseAnalysisResult(response.content(), result);
            
            result.setAnalysisDuration(System.currentTimeMillis() - startTime);
            result.setModelName(llmService.getModelName());
            
            log.info("时序数据分析完成: healthStatus={}, duration={}ms", 
                result.getHealthStatus(), result.getAnalysisDuration());
            
        } catch (Exception e) {
            log.error("时序数据分析失败: {}", e.getMessage());
            result.setHealthStatus(HealthStatus.UNKNOWN);
            result.setHealthScore(0);
            AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
            diagnosis.setSummary("分析失败: " + e.getMessage());
            result.setDiagnosis(diagnosis);
        }
        
        return result;
    }
    
    @Override
    public AnalysisResult analyzeSparkJob(String jobId, String cluster) {
        long startTime = System.currentTimeMillis();
        
        log.info("开始分析Spark作业: jobId={}", jobId);
        
        AnalysisResult result = new AnalysisResult();
        result.setResultId(UUID.randomUUID().toString());
        result.setCluster(cluster);
        result.setAnalysisTime(LocalDateTime.now());
        result.setAnalysisType("spark_app");
        result.setTargetId(jobId);
        
        try {
            // 获取作业摘要用于构建目标名称
            String summary = sparkJobStore.getJobSummary(jobId);
            result.setTargetName(extractAppName(summary));
            
            // 构建Prompt - 告诉LLM有数据可用，让它通过Tool查看
            String systemPrompt = buildSparkJobSystemPrompt();
            String userPrompt = buildSparkJobUserPrompt(jobId, cluster);
            
            // 调用LLM（支持Tool Calling）
            LLMResponse response = callLLMWithSparkJobTools(systemPrompt, userPrompt, jobId);
            
            // 解析结果
            parseAnalysisResult(response.content(), result);
            
            result.setAnalysisDuration(System.currentTimeMillis() - startTime);
            result.setModelName(llmService.getModelName());
            
            log.info("Spark作业分析完成: healthStatus={}, duration={}ms", 
                result.getHealthStatus(), result.getAnalysisDuration());
            
        } catch (Exception e) {
            log.error("Spark作业分析失败: {}", e.getMessage());
            result.setHealthStatus(HealthStatus.UNKNOWN);
            result.setHealthScore(0);
            AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
            diagnosis.setSummary("分析失败: " + e.getMessage());
            result.setDiagnosis(diagnosis);
        }
        
        return result;
    }
    
    // ============ 旧版接口实现 ============
    
    @Override
    public AnalysisResult analyzeComponent(ComponentMetrics metrics) {
        log.info("开始分析组件指标(旧版): service={}, component={}", metrics.getService(), metrics.getComponent());
        
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
        
        log.info("开始分析组件时间序列(旧版): 数据点数={}", metricsHistory.size());
        
        // 1. 时间序列特征提取
        ComponentMetricFeatures features = featureExtractor.extractFromTimeSeries(metricsHistory);
        
        // 2. 分析
        ComponentMetrics latest = metricsHistory.get(metricsHistory.size() - 1);
        return analyzeWithFeatures(features, latest.getCluster());
    }
    
    @Override
    public AnalysisResult analyzeSparkApp(SparkAppMetrics metrics) {
        log.info("开始分析Spark作业(旧版): jobId={}, appName={}", metrics.getJobId(), metrics.getAppName());
        
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
        
        // 从metricFeatures Map中提取各指标特征
        if (features.getMetricFeatures("cpu_usage") != null) {
            TimeSeriesFeatures cpuFeatures = features.getMetricFeatures("cpu_usage");
            if (cpuFeatures.getCurrent() != null) {
                metrics.setCpuUsage(cpuFeatures.getCurrent());
            }
            metrics.addMetric("cpu_usage", cpuFeatures.getCurrent());
        }
        if (features.getMetricFeatures("memory_usage") != null) {
            TimeSeriesFeatures memFeatures = features.getMetricFeatures("memory_usage");
            if (memFeatures.getCurrent() != null) {
                metrics.setMemoryUsage(memFeatures.getCurrent());
            }
            metrics.addMetric("memory_usage", memFeatures.getCurrent());
        }
        if (features.getMetricFeatures("gc_time") != null) {
            TimeSeriesFeatures gcFeatures = features.getMetricFeatures("gc_time");
            if (gcFeatures.getCurrent() != null) {
                metrics.setGcTime(gcFeatures.getCurrent().longValue());
            }
            metrics.addMetric("gc_time", gcFeatures.getCurrent());
        }
        if (features.getMetricFeatures("heap_usage") != null) {
            TimeSeriesFeatures heapFeatures = features.getMetricFeatures("heap_usage");
            metrics.addMetric("heap_usage", heapFeatures.getCurrent());
        }
        
        return metrics;
    }
    
    /**
     * 将Spark特征转换为Spark指标（用于降级分析）
     */
    private SparkAppMetrics convertSparkFeaturesToMetrics(SparkAppFeatures features) {
        SparkAppMetrics metrics = new SparkAppMetrics();
        // SparkAppFeatures没有cluster字段，这里不设置，在调用处从上下文中获取
        metrics.setJobId(features.getJobId());
        metrics.setAppName(features.getAppName());
        metrics.setDuration(features.getDuration());
        metrics.setExecutorCount(features.getExecutorCount());
        metrics.setExecutorMemoryGB(features.getExecutorMemoryGB());
        metrics.setExecutorCores(features.getExecutorCores());
        
        // 转换数据量字段（GB -> bytes）
        if (features.getInputSizeGB() != null) {
            metrics.setInputSize(features.getInputSizeGB().longValue() * 1024 * 1024 * 1024);
        }
        if (features.getOutputSizeGB() != null) {
            metrics.setOutputSize(features.getOutputSizeGB().longValue() * 1024 * 1024 * 1024);
        }
        if (features.getShuffleReadGB() != null) {
            metrics.setShuffleRead(features.getShuffleReadGB().longValue() * 1024 * 1024 * 1024);
        }
        if (features.getShuffleWriteGB() != null) {
            metrics.setShuffleWrite(features.getShuffleWriteGB().longValue() * 1024 * 1024 * 1024);
        }
        
        // 转换倾斜率
        if (features.getMaxSkewRatio() != null) {
            metrics.setSkewRatio(features.getMaxSkewRatio());
        }
        
        // 设置Shuffle比例
        if (features.getShuffleRatio() != null) {
            metrics.setShuffleRatio(features.getShuffleRatio());
        }
        
        return metrics;
    }
    
    // ============ 新版分析辅助方法 ============
    
    /**
     * 构建时序数据分析的系统Prompt
     */
    private String buildTimeSeriesSystemPrompt() {
        return """
            你是一个专业的大数据运维分析专家。你的任务是分析组件指标的历史时序数据，识别潜在问题并给出优化建议。
            
            你可以使用以下工具来获取详细数据：
            1. get_metrics_timeseries: 获取时序数据的详细信息和各指标趋势
            2. get_realtime_metrics: 获取实时指标数据
            3. get_long_term_status: 获取长期状态数据
            4. get_recent_events: 获取近期告警和事件
            
            分析步骤：
            1. 首先使用 get_metrics_timeseries 查看时序数据摘要
            2. 对异常指标使用工具查看详细趋势
            3. 结合实时指标和事件进行综合分析
            4. 给出诊断结论和优化建议
            
            请以JSON格式返回分析结果，格式如下：
            {
              "health_status": "healthy|warning|critical|unknown",
              "health_score": 0-100,
              "diagnosis": {
                "summary": "问题摘要",
                "root_cause": "根因分析",
                "issues": [{"type": "问题类型", "severity": "warning|critical", "title": "问题标题", "description": "详细描述", "related_metrics": ["相关指标"]}]
              },
              "recommendations": [{"type": "建议类型", "priority": 1-5, "title": "建议标题", "description": "详细说明", "actions": ["具体步骤"]}]
            }
            """;
    }
    
    /**
     * 构建时序数据分析的用户Prompt
     */
    private String buildTimeSeriesUserPrompt(String storageId, String cluster) {
        return String.format("""
            请分析以下组件的时序指标数据：
            
            存储ID: %s
            集群: %s
            
            请使用 get_metrics_timeseries 工具查看时序数据详情，分析各指标的变化趋势，
            识别异常波动，并给出健康评估和优化建议。
            
            注意：
            - 重点关注CPU、内存、GC等核心指标的趋势
            - 检查是否存在持续上升或下降的趋势
            - 识别异常尖峰或波动
            - 结合实时指标和告警事件进行综合判断
            """, storageId, cluster);
    }
    
    /**
     * 调用LLM并处理时序分析相关的Tool Calling
     */
    private LLMResponse callLLMWithTimeSeriesTools(String systemPrompt, String userPrompt, String storageId) {
        // 定义可用工具
        List<ToolDefinition> tools = List.of(
            new ToolDefinition(MetricsTimeSeriesTool.NAME,
                MetricsTimeSeriesTool.DESCRIPTION,
                Map.of(
                    "storage_id", new ParameterSchema("string", "时序数据存储ID", true),
                    "metric_name", new ParameterSchema("string", "指标名称（可选）", false),
                    "minutes", new ParameterSchema("integer", "最近N分钟", false)
                )),
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
        int maxIterations = 5;
        int iteration = 0;
        
        while (!response.finished() && "tool_calls".equals(response.finishReason()) && iteration < maxIterations) {
            iteration++;
            log.info("LLM请求调用工具 (迭代{}): {}个工具", iteration, response.toolCalls().size());
            
            StringBuilder toolResults = new StringBuilder();
            toolResults.append("工具调用结果:\n\n");
            
            for (ToolCall toolCall : response.toolCalls()) {
                log.info("执行工具: {} with arguments: {}", toolCall.name(), toolCall.arguments());
                
                String result = executeTimeSeriesTool(toolCall.name(), toolCall.arguments(), storageId);
                toolResults.append("【").append(toolCall.name()).append("】\n");
                toolResults.append(result).append("\n\n");
            }
            
            String newUserPrompt = userPrompt + "\n\n" + toolResults + "\n请基于以上工具结果继续分析。";
            response = llmService.analyze(systemPrompt, newUserPrompt, tools);
        }
        
        return response;
    }
    
    /**
     * 执行时序分析相关的工具
     */
    private String executeTimeSeriesTool(String toolName, Map<String, Object> arguments, String storageId) {
        try {
            return switch (toolName) {
                case "get_metrics_timeseries" -> {
                    String sid = arguments.containsKey("storage_id") 
                        ? arguments.get("storage_id").toString() : storageId;
                    String metricName = arguments.containsKey("metric_name") 
                        ? arguments.get("metric_name").toString() : null;
                    Integer minutes = arguments.containsKey("minutes") 
                        ? ((Number) arguments.get("minutes")).intValue() : null;
                    yield metricsTimeSeriesTool.execute(sid, metricName, minutes);
                }
                default -> {
                    ToolResult result = llmService.executeTool(toolName, arguments);
                    yield result.success() ? result.result() : "工具执行失败: " + result.error();
                }
            };
        } catch (Exception e) {
            log.error("工具执行失败: {}", e.getMessage(), e);
            return "工具执行失败: " + e.getMessage();
        }
    }
    
    /**
     * 构建Spark作业分析的系统Prompt
     */
    private String buildSparkJobSystemPrompt() {
        return """
            你是一个专业的Spark作业性能分析专家。你的任务是分析Spark作业的执行情况，识别性能瓶颈并给出优化建议。
            
            你可以使用以下工具来获取详细数据：
            1. get_spark_job: 获取Spark作业的详细信息（摘要、Stage、Executor、瓶颈分析）
            2. get_realtime_metrics: 获取集群实时指标
            3. get_recent_events: 获取近期告警和事件
            
            分析步骤：
            1. 首先使用 get_spark_job 获取作业摘要
            2. 查看Stage详情，识别耗时长的Stage
            3. 查看Executor详情，检查资源使用情况
            4. 使用瓶颈分析工具识别问题
            5. 给出诊断结论和优化建议
            
            常见问题类型：
            - 数据倾斜：某些Task处理的数据量远大于其他Task
            - GC瓶颈：GC时间占比过高
            - Shuffle瓶颈：Shuffle数据量过大
            - 内存瓶颈：Executor内存使用率过高
            - 任务失败：存在失败的任务
            
            请以JSON格式返回分析结果，格式如下：
            {
              "health_status": "healthy|warning|critical|unknown",
              "health_score": 0-100,
              "diagnosis": {
                "summary": "问题摘要",
                "root_cause": "根因分析",
                "issues": [{"type": "问题类型", "severity": "warning|critical", "title": "问题标题", "description": "详细描述", "related_metrics": ["相关指标"]}]
              },
              "recommendations": [{"type": "建议类型", "priority": 1-5, "title": "建议标题", "description": "详细说明", "actions": ["具体步骤"]}]
            }
            """;
    }
    
    /**
     * 构建Spark作业分析的用户Prompt
     */
    private String buildSparkJobUserPrompt(String jobId, String cluster) {
        return String.format("""
            请分析以下Spark作业：
            
            作业ID: %s
            集群: %s
            
            请使用 get_spark_job 工具查看作业详情，分析执行情况，
            识别性能瓶颈，并给出优化建议。
            
            建议分析顺序：
            1. 先获取作业摘要了解基本情况
            2. 查看Stage详情识别耗时Stage
            3. 查看Executor详情检查资源使用
            4. 获取瓶颈分析定位主要问题
            
            注意：
            - 重点关注数据倾斜、GC、Shuffle等常见瓶颈
            - 检查是否有失败任务
            - 分析资源配置是否合理
            """, jobId, cluster);
    }
    
    /**
     * 调用LLM并处理Spark作业分析相关的Tool Calling
     */
    private LLMResponse callLLMWithSparkJobTools(String systemPrompt, String userPrompt, String jobId) {
        // 定义可用工具
        List<ToolDefinition> tools = List.of(
            new ToolDefinition(SparkJobTool.NAME,
                SparkJobTool.DESCRIPTION,
                Map.of(
                    "job_id", new ParameterSchema("string", "作业ID", true),
                    "detail_type", new ParameterSchema("string", "详情类型: summary/stages/executors/bottleneck", false)
                )),
            new ToolDefinition("get_realtime_metrics",
                "获取集群组件的实时指标数据",
                Map.of(
                    "cluster", new ParameterSchema("string", "集群名称", true),
                    "service", new ParameterSchema("string", "服务类型", true),
                    "component", new ParameterSchema("string", "组件类型", false)
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
        int maxIterations = 5;
        int iteration = 0;
        
        while (!response.finished() && "tool_calls".equals(response.finishReason()) && iteration < maxIterations) {
            iteration++;
            log.info("LLM请求调用工具 (迭代{}): {}个工具", iteration, response.toolCalls().size());
            
            StringBuilder toolResults = new StringBuilder();
            toolResults.append("工具调用结果:\n\n");
            
            for (ToolCall toolCall : response.toolCalls()) {
                log.info("执行工具: {} with arguments: {}", toolCall.name(), toolCall.arguments());
                
                String result = executeSparkJobTool(toolCall.name(), toolCall.arguments(), jobId);
                toolResults.append("【").append(toolCall.name()).append("】\n");
                toolResults.append(result).append("\n\n");
            }
            
            String newUserPrompt = userPrompt + "\n\n" + toolResults + "\n请基于以上工具结果继续分析。";
            response = llmService.analyze(systemPrompt, newUserPrompt, tools);
        }
        
        return response;
    }
    
    /**
     * 执行Spark作业分析相关的工具
     */
    private String executeSparkJobTool(String toolName, Map<String, Object> arguments, String jobId) {
        try {
            return switch (toolName) {
                case "get_spark_job" -> {
                    String jid = arguments.containsKey("job_id") 
                        ? arguments.get("job_id").toString() : jobId;
                    String detailType = arguments.containsKey("detail_type") 
                        ? arguments.get("detail_type").toString() : "summary";
                    yield sparkJobTool.execute(jid, detailType);
                }
                default -> {
                    ToolResult result = llmService.executeTool(toolName, arguments);
                    yield result.success() ? result.result() : "工具执行失败: " + result.error();
                }
            };
        } catch (Exception e) {
            log.error("工具执行失败: {}", e.getMessage(), e);
            return "工具执行失败: " + e.getMessage();
        }
    }
    
    /**
     * 从摘要中提取目标名称
     */
    private String extractTargetName(String summary) {
        if (summary == null) return "Unknown";
        try {
            // 尝试从摘要中提取组件名称
            String[] lines = summary.split("\n");
            for (String line : lines) {
                if (line.startsWith("组件:")) {
                    return line.substring(3).trim();
                }
            }
        } catch (Exception e) {
            log.debug("提取目标名称失败: {}", e.getMessage());
        }
        return "Unknown";
    }
    
    /**
     * 从摘要中提取应用名称
     */
    private String extractAppName(String summary) {
        if (summary == null) return "Unknown";
        try {
            String[] lines = summary.split("\n");
            for (String line : lines) {
                if (line.startsWith("应用名称:")) {
                    return line.substring(5).trim();
                }
            }
        } catch (Exception e) {
            log.debug("提取应用名称失败: {}", e.getMessage());
        }
        return "Unknown";
    }
}
