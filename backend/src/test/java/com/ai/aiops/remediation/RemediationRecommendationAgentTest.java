package com.ai.aiops.remediation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

import com.ai.aiops.agent.RcaOutput;
import com.fasterxml.jackson.databind.ObjectMapper;

class RemediationRecommendationAgentTest {

    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void systemPromptListsFullActionCatalogAndForbidsOtherActions() throws Exception {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(any(Message.class), any(Message.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(objectMapper.writeValueAsString(
                new RemediationOutput("INVESTIGATE_FURTHER", "d", "e", "b", "r")));

        RcaOutput rca = new RcaOutput("s", "h", 0.5, List.of(), List.of(), List.of());
        new RemediationRecommendationAgent(chatClient).recommend(rca, "incident", "HIGH", "order-service");

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(requestSpec).messages(messageCaptor.capture(), messageCaptor.capture());
        String systemPrompt = messageCaptor.getAllValues().get(0).getText();

        for (RemediationActionType action : RemediationActionType.values()) {
            assertThat(systemPrompt).contains(action.name());
        }
        assertThat(systemPrompt).containsIgnoringCase("you may not propose anything outside this list");
        assertThat(systemPrompt).containsIgnoringCase("DATA");
    }
}
