package io.jenkins.plugins.smart_retry.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FailureClassificationTest {

    @Test
    void retryCandidateFactoryMarksClassificationRetryable() {
        FailureClassification classification = FailureClassification.retryCandidate(
                FailureType.AGENT_LOST, "agent-channel-closed", "Agent channel closed");

        assertEquals(FailureType.AGENT_LOST, classification.getType());
        assertEquals("agent-channel-closed", classification.getMatchedRule());
        assertEquals("Agent channel closed", classification.getSummary());
        assertTrue(classification.isRetryCandidate());
    }

    @Test
    void blankMatchedRuleIsNormalizedToNull() {
        FailureClassification classification =
                FailureClassification.nonRetryable(FailureType.UNKNOWN, "   ", "No deterministic rule matched");

        assertEquals(FailureType.UNKNOWN, classification.getType());
        assertNull(classification.getMatchedRule());
    }
}
