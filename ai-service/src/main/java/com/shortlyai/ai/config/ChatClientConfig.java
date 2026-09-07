package com.minima-AI.ai.config;

import com.minima-AI.ai.advisor.AvailableToolsAndMetricsAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.Map;

@Configuration
public class ChatClientConfig {

    private final Resource systemPrompt;

    private final AvailableToolsAndMetricsAdvisor availableToolsAndMetricsAdvisor;

    public ChatClientConfig(
            @Value("classpath:prompts/base-prompt/system.st")
            Resource systemPrompt,
            AvailableToolsAndMetricsAdvisor availableToolsAndMetricsAdvisor
    ) {
        this.systemPrompt = systemPrompt;
        this.availableToolsAndMetricsAdvisor = availableToolsAndMetricsAdvisor;
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {

        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        availableToolsAndMetricsAdvisor
                )
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .extraBody(Map.of("include_reasoning", false)) // gpt-oss-120b is a reasoning model, so it is excluded
                )
                .build();
    }
}

