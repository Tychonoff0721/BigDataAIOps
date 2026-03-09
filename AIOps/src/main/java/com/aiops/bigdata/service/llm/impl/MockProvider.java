package com.aiops.bigdata.service.llm.impl;

import com.aiops.bigdata.entity.common.enums.HealthStatus;
import com.aiops.bigdata.service.llm.LLMProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Mock Provider实现
 * 用于开发测试，模拟LLM行为
 */
@Slf4j
public class MockProvider implements LLMProvider {
    
    private final boolean simulateDelay;
    private final int delayMs;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public MockProvider(boolean simulateDelay, int delayMs) {
        this.simulateDelay = simulateDelay;
        this.delayMs = delayMs;
    }
    
    @Override
    public ChatResponse chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, Collections.emptyList());
    }
    
    @Override
    public ChatResponse chat(String systemPrompt, String userPrompt, List<ToolDefinition> tools) {
        long startTime = System.currentTimeMillis();
        
        log.info("MockProvider开始处理请求...");
        log.debug("SystemPrompt长度: {}, UserPrompt长度: {}", 
            systemPrompt != null ? systemPrompt.length() : 0,
            userPrompt != null ? userPrompt.length() : 0);
        
        // 模拟延迟
        simulateDelay();
        
        // 分析Prompt内容，决定是否需要调用工具
        List<ToolCall> toolCalls = decideToolCalls(userPrompt, tools);
        
        if (!toolCalls.isEmpty()) {
            log.info("MockProvider决定调用{}个工具", toolCalls.size());
            return new ChatResponse(
                null,
                toolCalls,
                false,
                "tool_calls",
                estimateTokens(systemPrompt + userPrompt),
                50,
                System.currentTimeMillis() - startTime
            );
        }
        
        // 生成分析结果
        String content = generateAnalysisContent(userPrompt);
        
        return new ChatResponse(
            content,
            Collections.emptyList(),
            true,
            "stop",
            estimateTokens(systemPrompt + userPrompt),
            estimateTokens(content),
            System.currentTimeMillis() - startTime
        );
    }
    
    @Override
    public ChatResponse continueWithToolResults(List<Message> conversationHistory, List<ToolResult> toolResults) {
        long startTime = System.currentTimeMillis();
        
        log.info("MockProvider继续处理，工具结果数: {}", toolResults.size());
        
        simulateDelay();
        
        // 构建包含工具结果的完整上下文
        StringBuilder contextBuilder = new StringBuilder();
        for (ToolResult result : toolResults) {
            contextBuilder.append("工具[").append(result.name()).append("]结果:\n");
            contextBuilder.append(result.result()).append("\n\n");
        }
        
        // 基于工具结果生成最终分析
        String content = generateAnalysisWithToolResults(contextBuilder.toString());
        
        return new ChatResponse(
            content,
            Collections.emptyList(),
            true,
            "stop",
            500,
            estimateTokens(content),
            System.currentTimeMillis() - startTime
        );
    }
    
    /**
     * 决定是否需要调用工具
     */
    private List<ToolCall> decideToolCalls(String userPrompt, List<ToolDefinition> tools) {
        List<ToolCall> toolCalls = new ArrayList<>();
        
        if (tools == null || tools.isEmpty()) {
            return toolCalls;
        }
        
        // 提取集群名称
        String cluster = extractClusterFromPrompt(userPrompt);
        
        // 模拟LLM的决策逻辑
        if (userPrompt.contains("集群") && !userPrompt.contains("长期状态")) {
            // 查询长期状态
            toolCalls.add(new ToolCall(
                UUID.randomUUID().toString(),
                "get_long_term_status",
                Map.of("cluster", cluster, "days", 7)
            ));
        }
        
        if (userPrompt.contains("告警") || userPrompt.contains("事件") || userPrompt.contains("近期")) {
            // 查询近期事件
            toolCalls.add(new ToolCall(
                UUID.randomUUID().toString(),
                "get_recent_events",
                Map.of("cluster", cluster, "limit", 10)
            ));
        }
        
        // 限制工具调用数量
        if (toolCalls.size() > 2) {
            toolCalls = toolCalls.subList(0, 2);
        }
        
        return toolCalls;
    }
    
    private String extractClusterFromPrompt(String prompt) {
        if (prompt == null) return "default";
        
        if (prompt.contains("集群: ")) {
            int start = prompt.indexOf("集群: ") + 4;
            int end = prompt.indexOf("\n", start);
            if (end > start) {
                return prompt.substring(start, end).trim();
            }
        }
        return "default";
    }
    
    /**
     * 生成分析内容
     */
    private String generateAnalysisContent(String userPrompt) {
        if (userPrompt == null) {
            return createDefaultResponse();
        }
        
        if (userPrompt.contains("Spark") || userPrompt.contains("作业")) {
            return generateSparkAnalysisResult(userPrompt);
        } else {
            return generateComponentAnalysisResult(userPrompt);
        }
    }
    
    private String generateComponentAnalysisResult(String userPrompt) {
        boolean hasAnomaly = userPrompt.contains("anomaly=YES") || userPrompt.contains("异常");
        boolean hasMemoryWarning = userPrompt.contains("memory") && userPrompt.contains("0.8");
        boolean hasCPUWarning = userPrompt.contains("cpu") && userPrompt.contains("0.8");
        
        String healthStatus = "healthy";
        int healthScore = 90;
        List<Map<String, Object>> issues = new ArrayList<>();
        
        if (hasMemoryWarning || hasAnomaly) {
            healthStatus = "warning";
            healthScore -= 20;
            issues.add(Map.of(
                "type", "resource",
                "severity", "warning",
                "title", "内存使用率偏高",
                "description", "内存使用率超过阈值，需要关注",
                "related_metrics", List.of("memory_usage")
            ));
        }
        
        if (hasCPUWarning) {
            healthStatus = "warning".equals(healthStatus) ? "warning" : "warning";
            healthScore -= 15;
            issues.add(Map.of(
                "type", "resource",
                "severity", "warning",
                "title", "CPU使用率偏高",
                "description", "CPU使用率较高，可能影响性能",
                "related_metrics", List.of("cpu_usage")
            ));
        }
        
        List<Map<String, Object>> recommendations = new ArrayList<>();
        if (!issues.isEmpty()) {
            recommendations.add(Map.of(
                "type", "configuration",
                "priority", 1,
                "title", "优化资源配置",
                "description", "建议检查资源配置，考虑扩容或优化",
                "actions", List.of("检查资源使用趋势", "考虑增加资源配额", "优化应用配置")
            ));
        } else {
            recommendations.add(Map.of(
                "type", "maintenance",
                "priority", 3,
                "title", "持续监控",
                "description", "组件运行正常，建议保持监控",
                "actions", List.of("继续监控关键指标", "定期检查日志")
            ));
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("health_status", healthStatus);
        result.put("health_score", Math.max(healthScore, 0));
        result.put("diagnosis", Map.of(
            "summary", issues.isEmpty() ? "组件运行正常" : "检测到" + issues.size() + "个问题",
            "issues", issues,
            "root_cause", issues.isEmpty() ? "无" : "资源使用率偏高"
        ));
        result.put("recommendations", recommendations);
        
        return toJson(result);
    }
    
    private String generateSparkAnalysisResult(String userPrompt) {
        boolean hasSkew = userPrompt.contains("数据倾斜") || userPrompt.contains("skew");
        boolean hasShuffleIssue = userPrompt.contains("Shuffle") && userPrompt.contains("高");
        
        String healthStatus = "healthy";
        int healthScore = 85;
        List<Map<String, Object>> issues = new ArrayList<>();
        List<String> bottlenecks = new ArrayList<>();
        
        if (hasSkew) {
            healthStatus = "warning";
            healthScore -= 20;
            issues.add(Map.of(
                "type", "data_skew",
                "severity", "warning",
                "title", "存在数据倾斜",
                "description", "部分Stage存在数据倾斜",
                "related_metrics", List.of("skew_ratio")
            ));
            bottlenecks.add("skew");
        }
        
        if (hasShuffleIssue) {
            healthStatus = "warning".equals(healthStatus) ? "warning" : "warning";
            healthScore -= 15;
            issues.add(Map.of(
                "type", "shuffle",
                "severity", "warning",
                "title", "Shuffle数据量大",
                "description", "Shuffle数据量较大，可能导致网络IO瓶颈",
                "related_metrics", List.of("shuffle_ratio")
            ));
            bottlenecks.add("shuffle");
        }
        
        List<Map<String, Object>> recommendations = new ArrayList<>();
        if (hasSkew) {
            recommendations.add(Map.of(
                "type", "code",
                "priority", 1,
                "title", "解决数据倾斜",
                "description", "建议对倾斜的Key进行特殊处理",
                "actions", List.of("对倾斜Key进行加盐处理", "使用broadcast join", "数据预聚合")
            ));
        }
        if (recommendations.isEmpty()) {
            recommendations.add(Map.of(
                "type", "monitoring",
                "priority", 3,
                "title", "持续优化",
                "description", "作业运行正常",
                "actions", List.of("监控后续执行情况")
            ));
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("health_status", healthStatus);
        result.put("health_score", Math.max(healthScore, 0));
        result.put("diagnosis", Map.of(
            "summary", issues.isEmpty() ? "作业执行正常" : "检测到瓶颈: " + String.join(", ", bottlenecks),
            "issues", issues,
            "root_cause", issues.isEmpty() ? "无" : "数据分布问题"
        ));
        result.put("recommendations", recommendations);
        
        return toJson(result);
    }
    
    private String generateAnalysisWithToolResults(String toolResults) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("health_status", "healthy");
        result.put("health_score", 85);
        result.put("diagnosis", Map.of(
            "summary", "基于工具查询结果，组件整体运行正常",
            "issues", List.of(),
            "root_cause", "无"
        ));
        result.put("recommendations", List.of(
            Map.of(
                "type", "monitoring",
                "priority", 3,
                "title", "持续监控",
                "description", "基于查询结果，建议保持监控",
                "actions", List.of("继续监控关键指标")
            )
        ));
        
        return toJson(result);
    }
    
    private String createDefaultResponse() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("health_status", "unknown");
        result.put("health_score", 50);
        result.put("diagnosis", Map.of(
            "summary", "无法分析，缺少输入数据",
            "issues", List.of(),
            "root_cause", "无输入"
        ));
        result.put("recommendations", List.of());
        
        return toJson(result);
    }
    
    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    private int estimateTokens(String text) {
        if (text == null) return 0;
        // 粗略估计：1 token ≈ 4 characters
        return text.length() / 4;
    }
    
    private void simulateDelay() {
        if (simulateDelay && delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    @Override
    public String getProviderName() {
        return "mock";
    }
    
    @Override
    public String getModelName() {
        return "mock-llm-v1";
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
}
