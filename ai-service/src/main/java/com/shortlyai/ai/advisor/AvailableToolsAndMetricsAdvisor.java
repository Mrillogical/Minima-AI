package com.minima-AI.ai.advisor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class AvailableToolsAndMetricsAdvisor implements BaseAdvisor {

    // Tells LLM what tools are available
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {

        List<String> tools = List.of();

        if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions options) {

            options.getToolCallbacks();

            tools = options.getToolCallbacks()
                    .stream()
                    .map(t ->
                            t.getToolDefinition()
                                    .name()
                    )
                    .sorted()
                    .toList();
        }

        log.info("Tools visible to LLM: {}, number of tools: {}", tools, tools.size());

        return chatClientRequest;
    }

    // Logs metrics
    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {

        if (chatClientResponse.chatResponse() != null) {

            ChatResponseMetadata metadata = chatClientResponse
                    .chatResponse()
                    .getMetadata();

            String model = metadata.getModel();

            Usage usage = metadata.getUsage();

            Integer promptTokens = usage.getPromptTokens();

            Integer totalTokens = usage.getTotalTokens();

            log.info("LLM used: '{}', Prompt tokens: {}, Total tokens: {}", model, promptTokens, totalTokens);

        }

        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 1000;
    }
}


