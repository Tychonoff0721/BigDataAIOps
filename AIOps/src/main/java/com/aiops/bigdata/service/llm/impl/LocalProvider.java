package com.aiops.bigdata.service.llm.impl;

import com.aiops.bigdata.service.llm.LLMProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 本地模型Provider实现
 * 支持OpenAI兼容的本地模型API（如Ollama、vLLM、LocalAI等）
 */
@Slf4j
public class LocalProvider implements LLMProvider {
    
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public LocalProvider(String baseUrl, String model, String apiKey, int timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        
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
            // 构建OpenAI兼容的请求体
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("stream", false);
            
            // 构建消息列表
            List<Map<String, Object>> messages = new ArrayList<>();
            
            Map<String, Object> systemMessage = new LinkedHashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);
            
            Map<String, Object> userMessage = new LinkedHashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);
            messages.add(userMessage);
            
            requestBody.put("messages", messages);
            
            // 添加工具定义（如果本地模型支持）
            if (tools != null && !tools.isEmpty()) {
                List<Map<String, Object>> toolsDef = buildToolsDefinition(tools);
                requestBody.put("tools", toolsDef);
                requestBody.put("tool_choice", "auto");
            }
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("本地模型请求: {}", jsonBody.length() > 500 ? jsonBody.substring(0, 500) + "..." : jsonBody);
            
            // 构建HTTP请求
            Request.Builder requestBuilder = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Content-Type", "application/json");
            
            // 如果有API Key则添加
            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
            }
            
            requestBuilder.post(RequestBody.create(jsonBody, MediaType.parse("application/json")));
            
            Request request = requestBuilder.build();
            
            // 发送请求
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("本地模型API调用失败: {} - {}", response.code(), errorBody);
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
            log.error("本地模型API调用异常: {}", e.getMessage(), e);
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
            requestBody.put("stream", false);
            
            // 构建消息列表（包含历史）
            List<Map<String, Object>> messages = new ArrayList<>();
            
            for (Message msg : conversationHistory) {
                Map<String, Object> message = new LinkedHashMap<>();
                message.put("role", msg.role());
                if (msg.content() != null) {
                    message.put("content", msg.content());
                }
                if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    message.put("tool_calls", convertToolCalls(msg.toolCalls()));
                }
                if (msg.toolCallId() != null) {
                    message.put("tool_call_id", msg.toolCallId());
                }
                messages.add(message);
            }
            
            // 添加工具结果
            for (ToolResult result : toolResults) {
                Map<String, Object> toolMessage = new LinkedHashMap<>();
                toolMessage.put("role", "tool");
                toolMessage.put("tool_call_id", result.toolCallId());
                toolMessage.put("content", result.result());
                messages.add(toolMessage);
            }
            
            requestBody.put("messages", messages);
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            Request.Builder requestBuilder = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Content-Type", "application/json");
            
            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
            }
            
            requestBuilder.post(RequestBody.create(jsonBody, MediaType.parse("application/json")));
            
            Request request = requestBuilder.build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("本地模型API调用失败: {} - {}", response.code(), errorBody);
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
            log.error("本地模型API调用异常: {}", e.getMessage(), e);
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
            toolDef.put("type", "function");
            
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("type", "object");
            
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
            
            parameters.put("properties", properties);
            parameters.put("required", required);
            function.put("parameters", parameters);
            
            toolDef.put("function", function);
            result.add(toolDef);
        }
        
        return result;
    }
    
    private List<Map<String, Object>> convertToolCalls(List<ToolCall> toolCalls) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (ToolCall tc : toolCalls) {
            Map<String, Object> toolCall = new LinkedHashMap<>();
            toolCall.put("id", tc.id());
            toolCall.put("type", "function");
            
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tc.name());
            function.put("arguments", objectMapper.valueToTree(tc.arguments()));
            toolCall.put("function", function);
            
            result.add(toolCall);
        }
        
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private ChatResponse parseResponse(String responseBody, long startTime) throws IOException {
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            return new ChatResponse("", Collections.emptyList(), true, "error", 0, 0,
                System.currentTimeMillis() - startTime);
        }
        
        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        String finishReason = (String) choice.get("finish_reason");
        
        String content = message != null ? (String) message.get("content") : null;
        
        // 解析工具调用
        List<ToolCall> toolCalls = new ArrayList<>();
        if (message != null) {
            List<Map<String, Object>> rawToolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (rawToolCalls != null) {
                for (Map<String, Object> rawTc : rawToolCalls) {
                    String id = (String) rawTc.get("id");
                    Map<String, Object> function = (Map<String, Object>) rawTc.get("function");
                    String name = (String) function.get("name");
                    String argsJson = (String) function.get("arguments");
                    
                    Map<String, Object> args = objectMapper.readValue(argsJson, Map.class);
                    toolCalls.add(new ToolCall(id, name, args));
                }
            }
        }
        
        // 解析token使用情况
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        int promptTokens = usage != null && usage.get("prompt_tokens") != null 
            ? ((Number) usage.get("prompt_tokens")).intValue() : 0;
        int completionTokens = usage != null && usage.get("completion_tokens") != null 
            ? ((Number) usage.get("completion_tokens")).intValue() : 0;
        
        boolean finished = "stop".equals(finishReason) || 
                          ("tool_calls".equals(finishReason) && toolCalls.isEmpty());
        
        return new ChatResponse(
            content,
            toolCalls,
            finished,
            finishReason,
            promptTokens,
            completionTokens,
            System.currentTimeMillis() - startTime
        );
    }
    
    @Override
    public String getProviderName() {
        return "local";
    }
    
    @Override
    public String getModelName() {
        return model;
    }
    
    @Override
    public boolean isAvailable() {
        // 本地模型总是可用（假设服务已启动）
        return true;
    }
}
