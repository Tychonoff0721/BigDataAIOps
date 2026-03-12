package com.aiops.bigdata.config;

import com.aiops.bigdata.service.llm.LLMProvider;
import com.aiops.bigdata.service.llm.impl.ClaudeProvider;
import com.aiops.bigdata.service.llm.impl.LocalProvider;
import com.aiops.bigdata.service.llm.impl.MockProvider;
import com.aiops.bigdata.service.llm.impl.OpenAIProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM配置类
 * @deprecated 使用 Spring AI 的 ChatClient 替代
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@Deprecated
public class LLMConfig {
    
    private final LLMProperties llmProperties;
    
    @Bean
    public LLMProvider llmProvider() {
        String provider = llmProperties.getProvider();
        log.info("初始化LLM Provider: {}", provider);
        
        return switch (provider.toLowerCase()) {
            case "openai" -> {
                if (!llmProperties.getOpenai().isEnabled()) {
                    log.warn("OpenAI未启用，使用MockProvider");
                    yield createMockProvider();
                }
                yield createOpenAIProvider();
            }
            case "claude" -> {
                if (!llmProperties.getClaude().isEnabled()) {
                    log.warn("Claude未启用，使用MockProvider");
                    yield createMockProvider();
                }
                yield createClaudeProvider();
            }
            case "local" -> {
                if (!llmProperties.getLocal().isEnabled()) {
                    log.warn("本地模型未启用，使用MockProvider");
                    yield createMockProvider();
                }
                yield createLocalProvider();
            }
            case "mock" -> createMockProvider();
            default -> {
                log.warn("未知的LLM Provider: {}, 使用MockProvider", provider);
                yield createMockProvider();
            }
        };
    }
    
    private OpenAIProvider createOpenAIProvider() {
        LLMProperties.OpenAIConfig config = llmProperties.getOpenai();
        log.info("创建OpenAI Provider: model={}, baseUrl={}", config.getModel(), config.getBaseUrl());
        return new OpenAIProvider(
            config.getApiKey(),
            config.getBaseUrl(),
            config.getModel(),
            config.getTemperature(),
            config.getMaxTokens(),
            config.getTimeoutSeconds()
        );
    }
    
    private ClaudeProvider createClaudeProvider() {
        LLMProperties.ClaudeConfig config = llmProperties.getClaude();
        log.info("创建Claude Provider: model={}, baseUrl={}", config.getModel(), config.getBaseUrl());
        return new ClaudeProvider(
            config.getApiKey(),
            config.getBaseUrl(),
            config.getModel(),
            config.getMaxTokens(),
            config.getTimeoutSeconds()
        );
    }
    
    private LocalProvider createLocalProvider() {
        LLMProperties.LocalConfig config = llmProperties.getLocal();
        log.info("创建Local Provider: model={}, baseUrl={}", config.getModel(), config.getBaseUrl());
        return new LocalProvider(
            config.getBaseUrl(),
            config.getModel(),
            config.getApiKey(),
            config.getTimeoutSeconds()
        );
    }
    
    private MockProvider createMockProvider() {
        LLMProperties.MockConfig config = llmProperties.getMock();
        log.info("创建Mock Provider: simulateDelay={}, delayMs={}", 
            config.isSimulateDelay(), config.getDelayMs());
        return new MockProvider(config.isSimulateDelay(), config.getDelayMs());
    }
}
