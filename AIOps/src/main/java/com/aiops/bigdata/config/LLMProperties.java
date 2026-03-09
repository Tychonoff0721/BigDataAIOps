package com.aiops.bigdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM配置属性类
 * 从application.yml读取配置
 */
@Data
@ConfigurationProperties(prefix = "aiops.llm")
public class LLMProperties {
    
    /**
     * 当前使用的provider
     * 可选值: openai, claude, local, mock
     */
    private String provider = "mock";
    
    /**
     * OpenAI配置
     */
    private OpenAIConfig openai = new OpenAIConfig();
    
    /**
     * Claude配置
     */
    private ClaudeConfig claude = new ClaudeConfig();
    
    /**
     * 本地模型配置
     */
    private LocalConfig local = new LocalConfig();
    
    /**
     * Mock配置
     */
    private MockConfig mock = new MockConfig();
    
    @Data
    public static class OpenAIConfig {
        private boolean enabled = false;
        private String apiKey;
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-4";
        private double temperature = 0.7;
        private int maxTokens = 2000;
        private int timeoutSeconds = 60;
    }
    
    @Data
    public static class ClaudeConfig {
        private boolean enabled = false;
        private String apiKey;
        private String baseUrl = "https://api.anthropic.com/v1";
        private String model = "claude-3-opus-20240229";
        private int maxTokens = 2000;
        private int timeoutSeconds = 60;
    }
    
    @Data
    public static class LocalConfig {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:8000/v1";
        private String model = "local-model";
        private String apiKey;
        private int timeoutSeconds = 120;
    }
    
    @Data
    public static class MockConfig {
        private boolean enabled = true;
        private boolean simulateDelay = true;
        private int delayMs = 500;
    }
}
