package io.jenkins.plugins.smart_retry.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RetryDecisionTest {

    @Test
    void retryFactoryCapturesNextAttemptAndDelay() {
        RetryDecision decision =
                RetryDecision.retry(FailureType.NETWORK_TRANSIENT, "retryable network timeout", 2, 15000L);

        assertTrue(decision.shouldRetry());
        assertEquals(FailureType.NETWORK_TRANSIENT, decision.getFailureType());
        assertEquals("retryable network timeout", decision.getReason());
        assertEquals(2, decision.getNextAttemptNumber());
        assertEquals(15000L, decision.getDelayMillis());
    }

    @Test
    void doNotRetryFactoryClearsAttemptState() {
        RetryDecision decision =
                RetryDecision.doNotRetry(FailureType.COMPILATION_FAILURE, "compilation failures are deterministic");

        assertFalse(decision.shouldRetry());
        assertEquals(0, decision.getNextAttemptNumber());
        assertEquals(0L, decision.getDelayMillis());
    }

    @Test
    void retryDecisionRejectsInvalidRetryState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetryDecision(true, "invalid", FailureType.UNKNOWN, 0, 1000L));
    }
}
