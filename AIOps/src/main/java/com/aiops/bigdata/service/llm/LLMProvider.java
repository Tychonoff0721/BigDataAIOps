package com.aiops.bigdata.service.llm;

import java.util.List;
import java.util.Map;

/**
 * LLM Provider接口
 * 定义大模型调用的统一接口
 */
public interface LLMProvider {
    
    /**
     * 执行聊天补全
     * 
     * @param systemPrompt 系统Prompt
     * @param userPrompt 用户Prompt
     * @return 模型响应
     */
    ChatResponse chat(String systemPrompt, String userPrompt);
    
    /**
     * 执行聊天补全（支持工具调用）
     * 
     * @param systemPrompt 系统Prompt
     * @param userPrompt 用户Prompt
     * @param tools 可用工具列表
     * @return 模型响应
     */
    ChatResponse chat(String systemPrompt, String userPrompt, List<ToolDefinition> tools);
    
    /**
     * 继续对话（追加工具调用结果）
     * 
     * @param conversationHistory 对话历史
     * @param toolResults 工具调用结果
     * @return 模型响应
     */
    ChatResponse continueWithToolResults(List<Message> conversationHistory, List<ToolResult> toolResults);
    
    /**
     * 获取Provider名称
     */
    String getProviderName();
    
    /**
     * 获取模型名称
     */
    String getModelName();
    
    /**
     * 检查Provider是否可用
     */
    boolean isAvailable();
    
    /**
     * 聊天响应
     */
    record ChatResponse(
        String content,                    // 文本内容
        List<ToolCall> toolCalls,          // 工具调用请求
        boolean finished,                  // 是否完成
        String finishReason,               // 完成原因: stop, tool_calls, length
        int promptTokens,                  // 输入token数
        int completionTokens,              // 输出token数
        long latencyMs                     // 响应延迟
    ) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
    
    /**
     * 消息
     */
    record Message(
        String role,                       // system, user, assistant, tool
        String content,                    // 内容
        String toolCallId,                 // 工具调用ID（tool角色时使用）
        List<ToolCall> toolCalls           // 工具调用（assistant角色时使用）
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
     * 工具调用结果
     */
    record ToolResult(
        String toolCallId,                 // 对应的工具调用ID
        String name,                       // 工具名称
        String result,                     // 执行结果
        boolean success                    // 是否成功
    ) {}
}
