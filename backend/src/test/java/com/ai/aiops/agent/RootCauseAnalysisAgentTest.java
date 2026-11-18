package com.ai.aiops.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.ai.aiops.common.exception.LlmUnavailableException;

class RootCauseAnalysisAgentTest {

    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

    private InvestigationContext context() {
        return new InvestigationContext(
                UUID.randomUUID(), "order-service: too many errors", "HIGH", "order-service",
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of("No deployment event history is tracked yet."));
    }

    private void stubChain() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    void returnsStructuredOutputFromChatClient() {
        stubChain();
        RcaOutput expected = new RcaOutput(
                "Order service is failing due to DB connection timeouts.",
                "Database connection pool exhaustion",
                0.7,
                List.of("10 SQLTransientConnectionException errors in 5 minutes"),
                List.of("Possibly triggered by a downstream latency spike"),
                List.of("deployment history", "connection pool metrics"));
        when(callResponseSpec.entity(RcaOutput.class)).thenReturn(expected);

        RootCauseAnalysisAgent agent = new RootCauseAnalysisAgent(chatClient);
        RcaOutput result = agent.analyze(context());

        assertThat(result.rootCauseHypothesis()).isEqualTo("Database connection pool exhaustion");
        assertThat(result.confidence()).isEqualTo(0.7);
    }

    @Test
    void wrapsLlmFailuresInLlmUnavailableException() {
        stubChain();
        when(callResponseSpec.entity(RcaOutput.class)).thenThrow(new RuntimeException("401 Unauthorized"));

        RootCauseAnalysisAgent agent = new RootCauseAnalysisAgent(chatClient);

        assertThatThrownBy(() -> agent.analyze(context()))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("401 Unauthorized");
    }
}
