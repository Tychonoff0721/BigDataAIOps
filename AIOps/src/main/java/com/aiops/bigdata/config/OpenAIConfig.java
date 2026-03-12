package com.aiops.bigdata.config;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationHandler;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * OpenAI HTTP 客户端配置类
 * 自定义 HTTP 客户端超时设置，解决 LLM API 调用超时问题
 * 
 * Spring AI 使用 RestClient 发起 API 请求，通过配置 RestClient.Builder
 * 来设置连接超时和读取超时时间。
 */
@Slf4j
@Configuration
public class OpenAIConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:gpt-4}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double temperature;

    @Value("${spring.ai.openai.chat.options.timeoutSeconds:60}")
    private int timeoutSeconds;

    /**
     * 创建 ObservationRegistry Bean（Micrometer 观测注册表）
     */
    @Bean
    public ObservationRegistry observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        return registry;
    }

    /**
     * 配置 HTTP 客户端请求工厂设置
     */
    @Bean
    public ClientHttpRequestFactorySettings clientHttpRequestFactorySettings() {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        
        log.info("配置 HTTP 客户端超时: connectTimeout={}s, readTimeout={}s", timeoutSeconds, timeoutSeconds);
        
        return ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(timeout)
            .withReadTimeout(timeout);
    }

    /**
     * 创建自定义的 RestClient.Builder，配置超时时间
     */
    private RestClient.Builder createRestClientBuilder(ClientHttpRequestFactorySettings settings) {
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);
        return RestClient.builder()
            .requestFactory(requestFactory);
    }

    /**
     * 自定义 OpenAiApi，使用自定义超时配置
     */
    @Bean
    @Primary
    public OpenAiApi openAiApi(ClientHttpRequestFactorySettings settings) {
        log.info("创建自定义 OpenAiApi，baseUrl: {}, 超时: {}秒", baseUrl, timeoutSeconds);
        
        RestClient.Builder restClientBuilder = createRestClientBuilder(settings);
        // 创建 WebClient.Builder（即使不使用流式调用也需要提供）
        WebClient.Builder webClientBuilder = WebClient.builder();
        return new OpenAiApi(baseUrl, apiKey, restClientBuilder, webClientBuilder);
    }

    /**
     * 自定义 OpenAiChatOptions
     */
    @Bean
    @Primary
    public OpenAiChatOptions openAiChatOptions() {
        log.info("创建 OpenAiChatOptions，model: {}", model);
        return OpenAiChatOptions.builder()
            .model(model)
            .temperature(temperature)
            .build();
    }

    /**
     * 自定义 OpenAiChatModel，注入 FunctionCallback
     */
    @Bean
    @Primary
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions openAiChatOptions,
                                          FunctionCallbackResolver functionCallbackResolver,
                                          List<FunctionCallback> functionCallbacks,
                                          RetryTemplate retryTemplate,
                                          ObservationRegistry observationRegistry) {
        log.info("创建自定义 OpenAiChatModel，注册 {} 个 FunctionCallback", functionCallbacks.size());
        functionCallbacks.forEach(fc -> log.info("  - 注册 Function: {}", fc.getName()));
        return new OpenAiChatModel(openAiApi, openAiChatOptions, functionCallbackResolver, 
            functionCallbacks, retryTemplate, observationRegistry);
    }
}
