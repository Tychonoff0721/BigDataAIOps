package com.aiops.bigdata.service.llm;

import com.aiops.bigdata.entity.analysis.AnalysisResult;

import java.util.List;
import java.util.Map;

/**
 * LLM服务接口
 * 负责与大模型交互，支持Tool Calling
 */
public interface LLMService {
    
    /**
     * 执行分析（带Tool Calling支持）
     * 
     * @param systemPrompt 系统Prompt
     * @param userPrompt 用户Prompt
     * @param availableTools 可用的工具列表
     * @return 分析结果
     */
    LLMResponse analyze(String systemPrompt, String userPrompt, List<ToolDefinition> availableTools);
    
    /**
     * 执行分析（无Tool）
     * 
     * @param systemPrompt 系统Prompt
     * @param userPrompt 用户Prompt
     * @return 分析结果
     */
    LLMResponse analyze(String systemPrompt, String userPrompt);
    
    /**
     * 调用工具并获取结果
     * 
     * @param toolName 工具名称
     * @param arguments 参数
     * @return 工具执行结果
     */
    ToolResult executeTool(String toolName, Map<String, Object> arguments);
    
    /**
     * 获取模型名称
     */
    String getModelName();
    
    /**
     * LLM响应
     */
    record LLMResponse(
        String content,                    // 文本响应
        List<ToolCall> toolCalls,          // 工具调用请求
        boolean finished,                  // 是否完成
        String finishReason,               // 完成原因
        long latencyMs                     // 响应延迟
    ) {}
    
    /**
     * 工具调用请求
     */
    record ToolCall(
        String id,                         // 调用ID
        String name,                       // 工具名称
        Map<String, Object> arguments      // 参数
    ) {}
    
    /**
     * 工具定义
     */
    record ToolDefinition(
        String name,                       // 工具名称
        String description,                // 工具描述
        Map<String, ParameterSchema> parameters  // 参数定义
    ) {}
    
    /**
     * 参数定义
     */
    record ParameterSchema(
        String type,                       // 参数类型
        String description,                // 参数描述
        boolean required                   // 是否必填
    ) {}
    
    /**
     * 工具执行结果
     */
    record ToolResult(
        boolean success,
        String result,                     // 执行结果（JSON字符串）
        String error                       // 错误信息
    ) {}
}
