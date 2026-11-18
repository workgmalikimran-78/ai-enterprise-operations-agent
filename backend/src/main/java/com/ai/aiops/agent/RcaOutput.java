package com.ai.aiops.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Structured output the RCA agent must produce - parsed directly from the LLM response. */
public record RcaOutput(
        @JsonPropertyDescription("One or two sentence plain-English summary of the incident and its likely cause")
        String summary,

        @JsonPropertyDescription("The single most likely root cause, stated as a hypothesis, not a certainty")
        String rootCauseHypothesis,

        @JsonPropertyDescription("Confidence in the root cause hypothesis, from 0.0 (pure guess) to 1.0 (certain), "
                + "based only on the strength of the supplied evidence")
        double confidence,

        @JsonPropertyDescription("Facts directly supported by the supplied evidence (quote or closely paraphrase "
                + "the evidence, do not invent anything not present)")
        List<String> confirmedFacts,

        @JsonPropertyDescription("Plausible explanations that are NOT directly confirmed by the evidence - "
                + "clearly distinguished from confirmedFacts")
        List<String> hypotheses,

        @JsonPropertyDescription("Specific additional evidence that would increase confidence, if it were "
                + "available (e.g. 'database connection pool metrics', 'deployment history')")
        List<String> evidenceGaps
) {
}
