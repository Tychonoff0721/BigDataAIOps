package com.aiops.bigdata.service.llm;

import com.aiops.bigdata.entity.common.enums.HealthStatus;
import com.aiops.bigdata.service.tool.RecentEventsTool;
import com.aiops.bigdata.service.tool.RealtimeMetricsTool;
import com.aiops.bigdata.service.tool.LongTermStatusTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * LLM服务实现类
 * 基于LLMProvider实现，支持多种大模型
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {
    
    private final LLMProvider llmProvider;
    private final RealtimeMetricsTool realtimeMetricsTool;
    private final LongTermStatusTool longTermStatusTool;
    private final RecentEventsTool recentEventsTool;
    
    @Override
    public LLMResponse analyze(String systemPrompt, String userPrompt, List<ToolDefinition> availableTools) {
        log.info("LLM分析开始, Provider: {}", llmProvider.getProviderName());
        
        // 转换Tool定义为Provider格式
        List<LLMProvider.ToolDefinition> tools = convertToolDefinitions(availableTools);
        
        // 调用Provider
        LLMProvider.ChatResponse response = llmProvider.chat(systemPrompt, userPrompt, tools);
        
        // 转换响应
        return new LLMResponse(
            response.content(),
            convertToolCalls(response.toolCalls()),
            response.finished(),
            response.finishReason(),
            response.latencyMs()
        );
    }
    
    @Override
    public LLMResponse analyze(String systemPrompt, String userPrompt) {
        return analyze(systemPrompt, userPrompt, Collections.emptyList());
    }
    
    @Override
    public ToolResult executeTool(String toolName, Map<String, Object> arguments) {
        log.info("执行工具: {} with arguments: {}", toolName, arguments);
        
        try {
            String result = switch (toolName) {
                case "get_realtime_metrics" -> {
                    String cluster = (String) arguments.get("cluster");
                    String service = (String) arguments.get("service");
                    String component = (String) arguments.get("component");
                    String instance = (String) arguments.get("instance");
                    
                    if (cluster == null || service == null) {
                        yield "{\"error\": \"cluster和service参数必填\"}";
                    }
                    
                    yield realtimeMetricsTool.getComponentHealthSummary(cluster, service, component != null ? component : "all");
                }
                
                case "get_long_term_status" -> {
                    String cluster = (String) arguments.get("cluster");
                    
                    if (cluster == null) {
                        yield "{\"error\": \"cluster参数必填\"}";
                    }
                    
                    yield longTermStatusTool.getClusterOverview(cluster);
                }
                
                case "get_recent_events" -> {
                    String cluster = (String) arguments.get("cluster");
                    String severity = (String) arguments.get("severity");
                    Integer limit = arguments.get("limit") != null 
                        ? ((Number) arguments.get("limit")).intValue() : 10;
                    
                    if (cluster == null) {
                        yield "{\"error\": \"cluster参数必填\"}";
                    }
                    
                    HealthStatus severityEnum = severity != null 
                        ? HealthStatus.fromCode(severity) : null;
                    
                    recentEventsTool.execute(cluster, severityEnum, null, false, limit);
                    yield recentEventsTool.getUnresolvedAlertsSummary(cluster);
                }
                
                default -> "{\"error\": \"未知工具: " + toolName + "\"}";
            };
            
            return new ToolResult(true, result, null);
            
        } catch (Exception e) {
            log.error("工具执行失败: {}", e.getMessage(), e);
            return new ToolResult(false, null, e.getMessage());
        }
    }
    
    @Override
    public String getModelName() {
        return llmProvider.getModelName();
    }
    
    /**
     * 转换Tool定义为Provider格式
     */
    private List<LLMProvider.ToolDefinition> convertToolDefinitions(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return Collections.emptyList();
        }
        
        return tools.stream()
            .map(t -> new LLMProvider.ToolDefinition(
                t.name(),
                t.description(),
                convertParameters(t.parameters())
            ))
            .toList();
    }
    
    /**
     * 转换参数定义
     */
    private Map<String, LLMProvider.ParameterSchema> convertParameters(
            Map<String, ParameterSchema> params) {
        if (params == null) {
            return Collections.emptyMap();
        }
        
        Map<String, LLMProvider.ParameterSchema> result = new HashMap<>();
        params.forEach((k, v) -> result.put(k, 
            new LLMProvider.ParameterSchema(v.type(), v.description(), v.required())));
        return result;
    }
    
    /**
     * 转换Tool调用
     */
    private List<ToolCall> convertToolCalls(List<LLMProvider.ToolCall> toolCalls) {
        if (toolCalls == null) {
            return Collections.emptyList();
        }
        
        return toolCalls.stream()
            .map(tc -> new ToolCall(tc.id(), tc.name(), tc.arguments()))
            .toList();
    }
}
