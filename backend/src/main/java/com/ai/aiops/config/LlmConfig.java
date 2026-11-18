package com.ai.aiops.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provider-agnostic LLM wiring. Builds the OpenAI {@link ChatModel}/{@link ChatClient} beans
 * manually (rather than relying on Spring AI's OpenAiAutoConfiguration, which is excluded in
 * application.yml) so the application still starts cleanly when {@code OPENAI_API_KEY} isn't
 * set - the agents that depend on {@link ChatClient} fail with a clear error only when they
 * actually try to call the LLM, not at startup. Gemini support (per {@code aiops.llm.provider})
 * is a placeholder for a later phase since no GCP credentials are configured in this environment.
 */
@Configuration
public class LlmConfig {

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
    private String openAiModel;

    @Bean
    public ChatModel chatModel() {
        OpenAiApi openAiApi = new OpenAiApi(openAiApiKey);
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(openAiModel).build();
        return new OpenAiChatModel(openAiApi, options);
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    public boolean isConfigured() {
        return openAiApiKey != null && !openAiApiKey.isBlank();
    }
}
