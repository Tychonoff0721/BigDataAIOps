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

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 分析服务实现
 * 支持 Spring AI 和 Mock 模式
 * 
 * 优化策略：
 * 1. 时序数据/Spark作业：直接传递摘要给LLM，无需Tool调用
 * 2. 集群上下文：通过Tool让LLM自行查询
 * 3. ChatClient复用：缓存带系统提示词的ChatClient实例
 */
@Slf4j
@Service
public class AIAnalysisServiceImpl implements AIAnalysisService {
    
    private final MetricsTimeSeriesStore metricsTimeSeriesStore;
    private final SparkJobStore sparkJobStore;
    private final ObjectMapper objectMapper;
    private final ChatClient.Builder chatClientBuilder;
    
    // 缓存的 ChatClient 实例（带系统提示词）
    private ChatClient cachedChatClient;
    
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
    
    /**
     * 初始化缓存的 ChatClient
     */
    @PostConstruct
    public void init() {
        if (!useMock) {
            log.info("初始化 ChatClient 缓存...");
            this.cachedChatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
            log.info("ChatClient 缓存初始化完成");
        }
    }
    
    // 系统提示词
    private static final String SYSTEM_PROMPT = """
        你是大数据运维分析专家。根据工具返回的特征数据，诊断问题并给出建议。
        
        输出JSON格式：
        {"health_status":"healthy|warning|critical","health_score":0-100,"diagnosis":{"summary":"摘要","root_cause":"根因","issues":[{"type":"类型","severity":"warning|critical","title":"标题","description":"描述"}]},"recommendations":[{"type":"类型","priority":1,"title":"标题","description":"说明","actions":["步骤"]}]}
        """;
    
    @Override
    public AnalysisResult analyzeTimeSeries(String storageId, String cluster) {
        log.info("开始分析时序数据: storageId={}", storageId);
        long startTime = System.currentTimeMillis();
        
        AnalysisResult result = createBaseResult("timeseries", storageId, cluster);
        
        try {
            // 直接获取数据摘要
            String summary = metricsTimeSeriesStore.getTimeSeriesSummary(storageId);
            result.setTargetName(extractTargetName(summary));
            
            // 让 LLM 自主决定是否调用 Tool 获取集群上下文
            String userPrompt = String.format(

                """
                分析以下组件的时序指标数据并给出诊断结果。
                你可以调用 getRealtimeMetrics/getLongTermStatus/getRecentEvents 获取集群上下文信息辅助分析。
                
                集群: %s
                存储ID: %s
                
                %s
                
                请基于以上数据和可能获取的集群上下文进行分析，输出JSON格式的诊断结果。
                """,
                cluster, storageId, summary);
            
            // 调用 LLM（带Tool，让LLM自主决定是否调用）
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
            // 直接获取作业摘要和瓶颈分析
            String summary = sparkJobStore.getJobSummary(jobId);
            String bottleneck = sparkJobStore.getBottleneckAnalysis(jobId);
            result.setTargetName(extractAppName(summary));
            
            // 让 LLM 自主决定是否调用 Tool 获取集群上下文
            String userPrompt = String.format(
                """
                分析以下Spark作业并给出诊断结果。
                你可以调用 getRealtimeMetrics/getLongTermStatus/getRecentEvents 获取集群上下文信息辅助分析。
                
                集群: %s
                作业ID: %s
                
                %s
                
                %s
                
                请基于以上数据和可能获取的集群上下文进行分析，输出JSON格式的诊断结果。
                """, 
                cluster, jobId, summary, bottleneck);
            
            // 调用 LLM（带Tool，让LLM自主决定是否调用）
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
            String userPrompt = String.format(
                "分析集群状态，cluster=%s。调用getRealtimeMetrics/getLongTermStatus/getRecentEvents获取数据后诊断。", 
                cluster);
            
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
     * 调用 LLM（带Tool，用于集群分析）
     * 使用缓存的 ChatClient，支持 Tool Calling
     */
    private String callLLM(String userPrompt) {
        log.info("\n" + "=".repeat(60));
        log.info("【LLM 调用开始 - 带Tool】");
        log.info("=".repeat(60));
        log.info("Mock模式: {}", useMock);
        
        if (useMock) {
            log.info("使用 Mock 模式生成响应");
            return generateMockResponse(userPrompt);
        }
        
        try {
            log.info("\n---------- 【User Prompt】 ----------");
            log.info(userPrompt);
            log.info("----------------------------------------");
            
            log.info("使用缓存的 ChatClient，注册Tool: getRealtimeMetrics, getLongTermStatus, getRecentEvents");
            log.info("发送请求到 LLM...\n");
            
            long startTime = System.currentTimeMillis();
            
            // 使用缓存的 ChatClient，只需注册 Tool
            String response = cachedChatClient.prompt()
                .user(userPrompt)
                .functions("getRealtimeMetrics", "getLongTermStatus", "getRecentEvents")
                .call()
                .content();
            
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("\n---------- 【LLM 响应】 耗时:{}ms ----------", duration);
            log.info(response);
            log.info("=".repeat(60) + "\n");
            
            return response;
            
        } catch (Exception e) {
            log.error("\n" + "=".repeat(60));
            log.error("【LLM 调用失败】");
            log.error("=".repeat(60));
            log.error("异常类型: {}", e.getClass().getName());
            log.error("异常消息: {}", e.getMessage());
            log.error("完整堆栈信息:", e);
            
            Throwable cause = e.getCause();
            if (cause != null) {
                log.error("Cause 类型: {}", cause.getClass().getName());
                log.error("Cause 消息: {}", cause.getMessage());
                log.error("Cause 堆栈:", cause);
            }
            
            log.warn("降级使用 Mock 模式生成响应\n");
            return generateMockResponse(userPrompt);
        }
    }
    
    /**
     * 调用 LLM（不带Tool，用于时序/Spark分析）
     * 数据已直接传入提示词，无需 Tool 调用
     */
    private String callLLMWithoutTools(String userPrompt) {
        log.info("\n" + "=".repeat(60));
        log.info("【LLM 调用开始 - 无Tool】");
        log.info("=".repeat(60));
        log.info("Mock模式: {}", useMock);
        
        if (useMock) {
            log.info("使用 Mock 模式生成响应");
            return generateMockResponse(userPrompt);
        }
        
        try {
            log.info("\n---------- 【User Prompt】 ----------");
            log.info(userPrompt);
            log.info("----------------------------------------");
            
            log.info("使用缓存的 ChatClient，无Tool调用");
            log.info("发送请求到 LLM...\n");
            
            long startTime = System.currentTimeMillis();
            
            // 使用缓存的 ChatClient，无 Tool
            String response = cachedChatClient.prompt()
                .user(userPrompt)
                .call()
                .content();
            
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("\n---------- 【LLM 响应】 耗时:{}ms ----------", duration);
            log.info(response);
            log.info("=".repeat(60) + "\n");
            
            return response;
            
        } catch (Exception e) {
            log.error("\n" + "=".repeat(60));
            log.error("【LLM 调用失败】");
            log.error("=".repeat(60));
            log.error("异常类型: {}", e.getClass().getName());
            log.error("异常消息: {}", e.getMessage());
            log.error("完整堆栈信息:", e);
            
            Throwable cause = e.getCause();
            if (cause != null) {
                log.error("Cause 类型: {}", cause.getClass().getName());
                log.error("Cause 消息: {}", cause.getMessage());
                log.error("Cause 堆栈:", cause);
            }
            
            log.warn("降级使用 Mock 模式生成响应\n");
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
    
    @Override
    public String testSimpleCall(String prompt) {
        log.info("========== 测试 Spring AI 简单调用 ==========");
        log.info("useMock={}", useMock);
        log.info("提示词: {}", prompt);
        
        if (useMock) {
            log.info("使用 Mock 模式");
            return "Mock 响应: 测试成功";
        }
        
        try {
            log.info("使用缓存的 ChatClient...");
            
            long startTime = System.currentTimeMillis();
            
            String response = cachedChatClient.prompt()
                .user(prompt)
                .call()
                .content();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("调用成功，耗时: {}ms", duration);
            log.info("响应内容: {}", response);
            
            return response;
            
        } catch (Exception e) {
            log.error("========== 测试调用失败 ==========");
            log.error("异常类型: {}", e.getClass().getName());
            log.error("异常消息: {}", e.getMessage());
            log.error("完整堆栈:", e);
            
            Throwable cause = e.getCause();
            if (cause != null) {
                log.error("Cause 类型: {}", cause.getClass().getName());
                log.error("Cause 消息: {}", cause.getMessage());
                log.error("Cause 堆栈:", cause);
            }
            
            throw new RuntimeException("Spring AI 调用失败: " + e.getMessage(), e);
        }
    }
}
