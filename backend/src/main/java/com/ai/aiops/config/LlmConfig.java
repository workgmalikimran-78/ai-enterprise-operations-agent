package com.ai.aiops.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provider-agnostic LLM wiring: builds whichever {@link ChatModel} {@code aiops.llm.provider}
 * selects ("openai" or "anthropic"), manually rather than relying on Spring AI's
 * OpenAiAutoConfiguration/AnthropicAutoConfiguration (both excluded in application.yml, since
 * they assert their API key is non-blank at bean-creation time and would crash app startup
 * whenever the key isn't set). This way the app always starts cleanly - agents that depend on
 * {@link ChatClient} fail with a clear error only when they actually try to call the LLM.
 * Gemini is a placeholder for a later phase; no GCP credentials are configured in this environment.
 */
@Configuration
public class LlmConfig {

    @Value("${aiops.llm.provider:openai}")
    private String provider;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${spring.ai.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-6}")
    private String anthropicModel;

    @Bean
    public ChatModel chatModel() {
        if ("anthropic".equalsIgnoreCase(provider)) {
            AnthropicApi anthropicApi = new AnthropicApi(anthropicApiKey);
            // Unlike OpenAI, Anthropic's API has no default and rejects requests outright
            // without it ("max_tokens: Field required").
            AnthropicChatOptions options = AnthropicChatOptions.builder()
                    .model(anthropicModel)
                    .maxTokens(4096)
                    .build();
            return new AnthropicChatModel(anthropicApi, options);
        }
        OpenAiApi openAiApi = new OpenAiApi(openAiApiKey);
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(openAiModel).build();
        return new OpenAiChatModel(openAiApi, options);
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    public boolean isConfigured() {
        String key = "anthropic".equalsIgnoreCase(provider) ? anthropicApiKey : openAiApiKey;
        return key != null && !key.isBlank();
    }
}
