package com.ai.aiops.remediation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import com.ai.aiops.agent.RcaOutput;

class RemediationRecommendationAgentTest {

    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

    @Test
    void systemPromptListsFullActionCatalogAndForbidsOtherActions() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(RemediationOutput.class)).thenReturn(
                new RemediationOutput("INVESTIGATE_FURTHER", "d", "e", "b", "r"));

        RcaOutput rca = new RcaOutput("s", "h", 0.5, List.of(), List.of(), List.of());
        new RemediationRecommendationAgent(chatClient).recommend(rca, "incident", "HIGH", "order-service");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemPromptCaptor.capture());
        String systemPrompt = systemPromptCaptor.getValue();

        for (RemediationActionType action : RemediationActionType.values()) {
            assertThat(systemPrompt).contains(action.name());
        }
        assertThat(systemPrompt).containsIgnoringCase("you may not propose anything outside this list");
        assertThat(systemPrompt).containsIgnoringCase("DATA");
    }
}
