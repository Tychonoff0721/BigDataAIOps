package com.aiops.bigdata.service.ai.impl;

import com.aiops.bigdata.entity.analysis.AnalysisResult;
import com.aiops.bigdata.entity.common.enums.HealthStatus;
import com.aiops.bigdata.service.context.MetricsTimeSeriesStore;
import com.aiops.bigdata.service.context.SparkJobStore;
import com.aiops.bigdata.service.ai.AIAnalysisService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 分析服务实现
 * 支持 Spring AI 和 Mock 模式
 */
@Slf4j
@Service
public class AIAnalysisServiceImpl implements AIAnalysisService {
    
    private final MetricsTimeSeriesStore metricsTimeSeriesStore;
    private final SparkJobStore sparkJobStore;
    private final ObjectMapper objectMapper;
    private final ChatClient.Builder chatClientBuilder;
    
    @Value("${aiops.llm.use-mock:true}")
    private boolean useMock;
    
    @Value("${aiops.llm.mock-delay-ms:500}")
    private int mockDelayMs;
    
    @Autowired
    public AIAnalysisServiceImpl(MetricsTimeSeriesStore metricsTimeSeriesStore,
                                  SparkJobStore sparkJobStore,
                                  ObjectMapper objectMapper,
                                  ChatClient.Builder chatClientBuilder) {
        this.metricsTimeSeriesStore = metricsTimeSeriesStore;
        this.sparkJobStore = sparkJobStore;
        this.objectMapper = objectMapper;
        this.chatClientBuilder = chatClientBuilder;
        log.info("AIAnalysisService 初始化, useMock={}", useMock);
    }
    
    // 系统提示词
    private static final String SYSTEM_PROMPT = """
        你是一个专业的大数据运维分析专家。你的任务是分析大数据集群的运行状态，识别潜在问题并给出优化建议。
        
        ## 关于时序数据的重要说明
        
        当你调用 getMetricsTimeSeries 工具时，返回的数据是**时间序列数据（Time Series Data）**：
        - 这是同一个组件在不同时间点采集的指标数据集合
        - 数据按时间顺序排列，每个数据点包含时间戳(timestamp)
        - 你需要分析指标随时间的变化趋势，而不仅仅是单个数值
        - 重点关注：上升趋势、下降趋势、异常波动、周期性模式
        
        时序数据分析要点：
        1. 趋势分析：CPU/内存使用率是否持续上升或下降
        2. 异常检测：是否存在突增或突降的异常点
        3. 阈值判断：当前值是否接近或超过告警阈值
        4. 关联分析：多个指标之间是否存在关联变化
        
        ## 分析步骤
        
        1. 首先调用工具获取数据摘要，了解数据基本情况
        2. 分析时序数据的时间跨度和采样间隔
        3. 识别关键指标的变化趋势和异常点
        4. 结合多种数据源进行综合判断
        5. 给出诊断结论和优化建议
        
        ## 输出格式
        
        请以JSON格式返回分析结果，格式如下：
        {
          "health_status": "healthy|warning|critical|unknown",
          "health_score": 0-100,
          "diagnosis": {
            "summary": "问题摘要",
            "root_cause": "根因分析",
            "issues": [{"type": "问题类型", "severity": "warning|critical", "title": "问题标题", "description": "详细描述"}]
          },
          "recommendations": [{"type": "建议类型", "priority": 1-5, "title": "建议标题", "description": "详细说明", "actions": ["具体步骤"]}]
        }
        """;
    
    @Override
    public AnalysisResult analyzeTimeSeries(String storageId, String cluster) {
        log.info("开始分析时序数据: storageId={}", storageId);
        long startTime = System.currentTimeMillis();
        
        AnalysisResult result = createBaseResult("timeseries", storageId, cluster);
        
        try {
            // 获取数据摘要用于目标名称
            String summary = metricsTimeSeriesStore.getTimeSeriesSummary(storageId);
            result.setTargetName(extractTargetName(summary));
            
            // 构建用户提示词
            String userPrompt = String.format("""
                请分析以下组件的时间序列指标数据：
                
                存储ID: %s
                集群: %s
                
                【重要】这是一组时间序列数据，包含同一组件在不同时间点的多个指标采样。
                
                请按以下步骤分析：
                1. 使用 getMetricsTimeSeries 工具获取时序数据详情
                2. 分析数据的时间跨度和采样间隔
                3. 识别各指标（CPU、内存、GC等）随时间的变化趋势
                4. 检测是否存在异常波动或突变点
                5. 综合评估组件健康状态并给出优化建议
                """, storageId, cluster);
            
            // 调用 LLM
            String response = callLLM(userPrompt);
            parseAnalysisResult(response, result);
            
            result.setAnalysisDuration(System.currentTimeMillis() - startTime);
            log.info("时序数据分析完成: healthStatus={}, duration={}ms", 
                result.getHealthStatus(), result.getAnalysisDuration());
            
        } catch (Exception e) {
            log.error("时序数据分析失败: {}", e.getMessage(), e);
            handleError(result, e);
        }
        
        return result;
    }
    
    @Override
    public AnalysisResult analyzeSparkJob(String jobId, String cluster) {
        log.info("开始分析Spark作业: jobId={}", jobId);
        long startTime = System.currentTimeMillis();
        
        AnalysisResult result = createBaseResult("spark_app", jobId, cluster);
        
        try {
            // 获取作业摘要用于目标名称
            String summary = sparkJobStore.getJobSummary(jobId);
            result.setTargetName(extractAppName(summary));
            
            // 构建用户提示词
            String userPrompt = String.format("""
                请分析以下Spark作业：
                
                作业ID: %s
                集群: %s
                
                请使用 getSparkJob 工具查看作业详情，分析执行情况，
                识别性能瓶颈，并给出优化建议。
                
                建议分析顺序：
                1. 先获取作业摘要了解基本情况
                2. 查看Stage详情识别耗时Stage
                3. 查看Executor详情检查资源使用
                4. 获取瓶颈分析定位主要问题
                """, jobId, cluster);
            
            // 调用 LLM
            String response = callLLM(userPrompt);
            parseAnalysisResult(response, result);
            
            result.setAnalysisDuration(System.currentTimeMillis() - startTime);
            log.info("Spark作业分析完成: healthStatus={}, duration={}ms", 
                result.getHealthStatus(), result.getAnalysisDuration());
            
        } catch (Exception e) {
            log.error("Spark作业分析失败: {}", e.getMessage(), e);
            handleError(result, e);
        }
        
        return result;
    }
    
    @Override
    public AnalysisResult analyzeCluster(String cluster) {
        log.info("开始分析集群状态: cluster={}", cluster);
        long startTime = System.currentTimeMillis();
        
        AnalysisResult result = createBaseResult("cluster", cluster, cluster);
        result.setTargetName(cluster);
        
        try {
            String userPrompt = String.format("""
                请分析以下集群的整体状态：
                
                集群: %s
                
                请使用提供的工具获取集群的实时指标、长期状态和最近告警，
                进行综合分析并给出健康评估和优化建议。
                """, cluster);
            
            String response = callLLM(userPrompt);
            parseAnalysisResult(response, result);
            
            result.setAnalysisDuration(System.currentTimeMillis() - startTime);
            log.info("集群状态分析完成: healthStatus={}, duration={}ms", 
                result.getHealthStatus(), result.getAnalysisDuration());
            
        } catch (Exception e) {
            log.error("集群状态分析失败: {}", e.getMessage(), e);
            handleError(result, e);
        }
        
        return result;
    }
    
    /**
     * 调用 LLM（Spring AI 或 Mock）
     */
    private String callLLM(String userPrompt) {
        log.info("调用 LLM 分析, useMock={}", useMock);
        
        if (useMock) {
            return generateMockResponse(userPrompt);
        }
        
        try {
            ChatClient chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
            
            String response = chatClient.prompt()
                .user(userPrompt)
                .functions("getMetricsTimeSeries", "getSparkJob", "getRealtimeMetrics", 
                           "getLongTermStatus", "getRecentEvents")
                .call()
                .content();
            
            log.info("Spring AI 调用成功");
            return response;
            
        } catch (Exception e) {
            log.warn("Spring AI 调用失败，降级使用 Mock 模式: {}", e.getMessage());
            return generateMockResponse(userPrompt);
        }
    }
    
    /**
     * Mock 响应生成（当 LLM 不可用时）
     */
    private String generateMockResponse(String userPrompt) {
        // 模拟延迟
        if (mockDelayMs > 0) {
            try {
                Thread.sleep(mockDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        
        if (userPrompt.contains("Spark") || userPrompt.contains("作业")) {
            // Spark 作业分析 - 更详细
            result.put("health_status", "warning");
            result.put("health_score", 72);
            result.put("diagnosis", Map.of(
                "summary", "Spark作业存在数据倾斜问题，部分Executor处理时间过长",
                "root_cause", "数据分区不均匀导致部分Task执行时间显著高于平均值",
                "issues", List.of(
                    Map.of("type", "data_skew", "severity", "warning", "title", "数据倾斜", 
                           "description", "检测到5.5倍的数据倾斜率，建议优化分区策略"),
                    Map.of("type", "gc_overhead", "severity", "warning", "title", "GC时间过长",
                           "description", "GC时间占比15%，接近告警阈值，建议增加Executor内存")
                )
            ));
            result.put("recommendations", List.of(
                Map.of("type", "performance", "priority", 1, "title", "优化数据分区",
                       "description", "建议使用repartition或coalesce重新分区，减少数据倾斜",
                       "actions", List.of("分析数据分布情况", "使用repartition调整分区数", "监控优化效果")),
                Map.of("type", "resource", "priority", 2, "title", "调整Executor配置",
                       "description", "增加Executor内存可减少GC压力",
                       "actions", List.of("将Executor内存从4G增加到8G", "调整spark.executor.memoryOverhead", "观察GC时间变化")),
                Map.of("type", "monitoring", "priority", 3, "title", "持续监控",
                       "description", "作业优化后需要持续监控效果",
                       "actions", List.of("监控后续执行情况", "关注资源使用趋势", "建立性能基线"))
            ));
        } else if (userPrompt.contains("集群")) {
            // 集群分析 - 更详细
            result.put("health_status", "warning");
            result.put("health_score", 78);
            result.put("diagnosis", Map.of(
                "summary", "集群整体运行正常，但存在资源使用不均衡和潜在的性能风险",
                "root_cause", "部分节点负载较高，HDFS存储使用率接近告警阈值",
                "issues", List.of(
                    Map.of("type", "resource_imbalance", "severity", "warning", "title", "资源不均衡",
                           "description", "部分DataNode存储使用率差异超过20%"),
                    Map.of("type", "capacity", "severity", "warning", "title", "存储容量告警",
                           "description", "HDFS使用率已达75%，建议扩容或清理历史数据")
                )
            ));
            result.put("recommendations", List.of(
                Map.of("type", "capacity", "priority", 1, "title", "存储容量规划",
                       "description", "HDFS使用率持续增长，需要制定扩容或数据清理计划",
                       "actions", List.of("分析数据增长趋势", "制定数据归档策略", "评估扩容需求")),
                Map.of("type", "balancer", "priority", 2, "title", "数据均衡",
                       "description", "执行HDFS Balancer均衡各DataNode存储",
                       "actions", List.of("启动HDFS Balancer", "监控均衡进度", "验证均衡效果")),
                Map.of("type", "maintenance", "priority", 3, "title", "日常维护",
                       "description", "集群运行稳定，建议进行日常维护",
                       "actions", List.of("定期检查日志", "监控资源使用", "及时处理告警"))
            ));
        } else {
            // 组件时序分析 - 更详细
            result.put("health_status", "warning");
            result.put("health_score", 75);
            result.put("diagnosis", Map.of(
                "summary", "组件运行存在轻微异常，CPU和内存使用率呈上升趋势",
                "root_cause", "业务量增长导致资源消耗增加",
                "issues", List.of(
                    Map.of("type", "cpu_high", "severity", "warning", "title", "CPU使用率偏高",
                           "description", "CPU使用率75%，存在上升趋势，建议关注"),
                    Map.of("type", "memory_high", "severity", "warning", "title", "内存使用率偏高",
                           "description", "内存使用率82%，接近告警阈值85%"),
                    Map.of("type", "gc_time", "severity", "info", "title", "GC时间正常",
                           "description", "GC时间2500ms，在正常范围内")
                )
            ));
            result.put("recommendations", List.of(
                Map.of("type", "capacity", "priority", 1, "title", "资源扩容评估",
                       "description", "资源使用率持续增长，建议评估扩容需求",
                       "actions", List.of("分析资源使用趋势", "评估扩容时机", "制定扩容计划")),
                Map.of("type", "optimization", "priority", 2, "title", "JVM调优",
                       "description", "可考虑优化JVM参数以提升性能",
                       "actions", List.of("分析GC日志", "调整堆内存大小", "优化GC策略")),
                Map.of("type", "monitoring", "priority", 3, "title", "持续监控",
                       "description", "组件状态需要持续关注",
                       "actions", List.of("继续监控关键指标", "定期检查日志", "设置告警阈值"))
            ));
        }
        
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return "{\"health_status\": \"unknown\", \"health_score\": 50}";
        }
    }
    
    /**
     * 创建基础结果对象
     */
    private AnalysisResult createBaseResult(String analysisType, String targetId, String cluster) {
        AnalysisResult result = new AnalysisResult();
        result.setResultId(UUID.randomUUID().toString());
        result.setCluster(cluster);
        result.setAnalysisTime(LocalDateTime.now());
        result.setAnalysisType(analysisType);
        result.setTargetId(targetId);
        return result;
    }
    
    /**
     * 解析 LLM 返回的 JSON 结果
     */
    private void parseAnalysisResult(String content, AnalysisResult result) {
        if (content == null || content.isEmpty()) {
            result.setHealthStatus(HealthStatus.UNKNOWN);
            return;
        }
        
        try {
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
                        if (issueNode.has("type")) issue.setIssueType(issueNode.get("type").asText());
                        if (issueNode.has("severity")) {
                            issue.setSeverity(HealthStatus.fromCode(issueNode.get("severity").asText()));
                        }
                        if (issueNode.has("title")) issue.setTitle(issueNode.get("title").asText());
                        if (issueNode.has("description")) issue.setDescription(issueNode.get("description").asText());
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
                    if (recNode.has("type")) rec.setRecommendationType(recNode.get("type").asText());
                    if (recNode.has("priority")) rec.setPriority(recNode.get("priority").asInt());
                    if (recNode.has("title")) rec.setTitle(recNode.get("title").asText());
                    if (recNode.has("description")) rec.setDescription(recNode.get("description").asText());
                    if (recNode.has("actions") && recNode.get("actions").isArray()) {
                        for (JsonNode action : recNode.get("actions")) {
                            rec.getActionSteps().add(action.asText());
                        }
                    }
                    result.addRecommendation(rec);
                }
            }
            
            result.setRawResponse(content);
            
        } catch (Exception e) {
            log.warn("解析LLM响应失败，使用原始内容: {}", e.getMessage());
            AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
            diagnosis.setSummary(content.length() > 500 ? content.substring(0, 500) + "..." : content);
            result.setDiagnosis(diagnosis);
            result.setRawResponse(content);
            result.setHealthStatus(HealthStatus.UNKNOWN);
        }
    }
    
    /**
     * 处理错误
     */
    private void handleError(AnalysisResult result, Exception e) {
        result.setHealthStatus(HealthStatus.UNKNOWN);
        result.setHealthScore(0);
        AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
        diagnosis.setSummary("分析失败: " + e.getMessage());
        result.setDiagnosis(diagnosis);
    }
    
    /**
     * 从摘要中提取目标名称
     */
    private String extractTargetName(String summary) {
        if (summary == null) return "Unknown";
        try {
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
