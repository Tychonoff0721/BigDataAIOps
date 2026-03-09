package com.aiops.bigdata.service.llm.impl;

import com.aiops.bigdata.service.llm.LLMProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Claude Provider实现
 * 通过API调用Anthropic Claude服务
 */
@Slf4j
public class ClaudeProvider implements LLMProvider {
    
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxTokens;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public ClaudeProvider(String apiKey, String baseUrl, String model, 
            int maxTokens, int timeoutSeconds) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build();
    }
    
    @Override
    public ChatResponse chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, Collections.emptyList());
    }
    
    @Override
    public ChatResponse chat(String systemPrompt, String userPrompt, List<ToolDefinition> tools) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 构建Claude请求体
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("system", systemPrompt);
            
            // 构建消息
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> userMessage = new LinkedHashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);
            messages.add(userMessage);
            requestBody.put("messages", messages);
            
            // 添加工具定义
            if (tools != null && !tools.isEmpty()) {
                List<Map<String, Object>> toolsDef = buildToolsDefinition(tools);
                requestBody.put("tools", toolsDef);
            }
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("Claude请求: {}", jsonBody.length() > 500 ? jsonBody.substring(0, 500) + "..." : jsonBody);
            
            // 构建HTTP请求
            Request request = new Request.Builder()
                .url(baseUrl + "/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();
            
            // 发送请求
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("Claude API调用失败: {} - {}", response.code(), errorBody);
                    return new ChatResponse(
                        "API调用失败: " + response.code() + " - " + errorBody,
                        Collections.emptyList(),
                        true,
                        "error",
                        0, 0,
                        System.currentTimeMillis() - startTime
                    );
                }
                
                String responseBody = response.body().string();
                return parseResponse(responseBody, startTime);
            }
            
        } catch (Exception e) {
            log.error("Claude API调用异常: {}", e.getMessage(), e);
            return new ChatResponse(
                "API调用异常: " + e.getMessage(),
                Collections.emptyList(),
                true,
                "error",
                0, 0,
                System.currentTimeMillis() - startTime
            );
        }
    }
    
    @Override
    public ChatResponse continueWithToolResults(List<Message> conversationHistory, List<ToolResult> toolResults) {
        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("max_tokens", maxTokens);
            
            // 提取system prompt
            String systemPrompt = null;
            for (Message msg : conversationHistory) {
                if ("system".equals(msg.role())) {
                    systemPrompt = msg.content();
                    break;
                }
            }
            if (systemPrompt != null) {
                requestBody.put("system", systemPrompt);
            }
            
            // 构建消息列表
            List<Map<String, Object>> messages = new ArrayList<>();
            for (Message msg : conversationHistory) {
                if ("system".equals(msg.role())) continue;
                
                Map<String, Object> message = new LinkedHashMap<>();
                message.put("role", msg.role());
                
                // 处理assistant消息中的工具调用
                if ("assistant".equals(msg.role()) && msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    List<Map<String, Object>> content = new ArrayList<>();
                    
                    // 添加文本内容
                    if (msg.content() != null) {
                        Map<String, Object> textContent = new LinkedHashMap<>();
                        textContent.put("type", "text");
                        textContent.put("text", msg.content());
                        content.add(textContent);
                    }
                    
                    // 添加工具使用
                    for (ToolCall tc : msg.toolCalls()) {
                        Map<String, Object> toolUse = new LinkedHashMap<>();
                        toolUse.put("type", "tool_use");
                        toolUse.put("id", tc.id());
                        toolUse.put("name", tc.name());
                        toolUse.put("input", tc.arguments());
                        content.add(toolUse);
                    }
                    message.put("content", content);
                } else {
                    message.put("content", msg.content());
                }
                
                messages.add(message);
            }
            
            // 添加工具结果
            for (ToolResult result : toolResults) {
                Map<String, Object> toolResultMessage = new LinkedHashMap<>();
                toolResultMessage.put("role", "user");
                
                List<Map<String, Object>> content = new ArrayList<>();
                Map<String, Object> toolResultContent = new LinkedHashMap<>();
                toolResultContent.put("type", "tool_result");
                toolResultContent.put("tool_use_id", result.toolCallId());
                toolResultContent.put("content", result.result());
                content.add(toolResultContent);
                
                toolResultMessage.put("content", content);
                messages.add(toolResultMessage);
            }
            
            requestBody.put("messages", messages);
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            Request request = new Request.Builder()
                .url(baseUrl + "/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("Claude API调用失败: {} - {}", response.code(), errorBody);
                    return new ChatResponse(
                        "API调用失败: " + response.code(),
                        Collections.emptyList(),
                        true,
                        "error",
                        0, 0,
                        System.currentTimeMillis() - startTime
                    );
                }
                
                String responseBody = response.body().string();
                return parseResponse(responseBody, startTime);
            }
            
        } catch (Exception e) {
            log.error("Claude API调用异常: {}", e.getMessage(), e);
            return new ChatResponse(
                "API调用异常: " + e.getMessage(),
                Collections.emptyList(),
                true,
                "error",
                0, 0,
                System.currentTimeMillis() - startTime
            );
        }
    }
    
    private List<Map<String, Object>> buildToolsDefinition(List<ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (ToolDefinition tool : tools) {
            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("name", tool.name());
            toolDef.put("description", tool.description());
            
            // 构建参数schema
            Map<String, Object> inputSchema = new LinkedHashMap<>();
            inputSchema.put("type", "object");
            
            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            
            for (Map.Entry<String, ParameterSchema> entry : tool.parameters().entrySet()) {
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", entry.getValue().type());
                prop.put("description", entry.getValue().description());
                properties.put(entry.getKey(), prop);
                
                if (entry.getValue().required()) {
                    required.add(entry.getKey());
                }
            }
            
            inputSchema.put("properties", properties);
            inputSchema.put("required", required);
            toolDef.put("input_schema", inputSchema);
            
            result.add(toolDef);
        }
        
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private ChatResponse parseResponse(String responseBody, long startTime) throws IOException {
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            return new ChatResponse("", Collections.emptyList(), true, "error", 0, 0,
                System.currentTimeMillis() - startTime);
        }
        
        String textContent = null;
        List<ToolCall> toolCalls = new ArrayList<>();
        
        for (Map<String, Object> block : content) {
            String type = (String) block.get("type");
            if ("text".equals(type)) {
                textContent = (String) block.get("text");
            } else if ("tool_use".equals(type)) {
                String id = (String) block.get("id");
                String name = (String) block.get("name");
                Map<String, Object> input = (Map<String, Object>) block.get("input");
                toolCalls.add(new ToolCall(id, name, input));
            }
        }
        
        String stopReason = (String) response.get("stop_reason");
        boolean finished = "end_turn".equals(stopReason) || 
                          ("tool_use".equals(stopReason) && toolCalls.isEmpty());
        
        // 解析token使用情况
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        int promptTokens = usage != null ? ((Number) usage.get("input_tokens")).intValue() : 0;
        int completionTokens = usage != null ? ((Number) usage.get("output_tokens")).intValue() : 0;
        
        return new ChatResponse(
            textContent,
            toolCalls,
            finished,
            stopReason,
            promptTokens,
            completionTokens,
            System.currentTimeMillis() - startTime
        );
    }
    
    @Override
    public String getProviderName() {
        return "claude";
    }
    
    @Override
    public String getModelName() {
        return model;
    }
    
    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.startsWith("sk-xxx");
    }
}
