package io.jenkins.plugins.smart_retry.policy;

import io.jenkins.plugins.smart_retry.model.FailureClassification;
import io.jenkins.plugins.smart_retry.model.FailureType;
import io.jenkins.plugins.smart_retry.model.RetryDecision;
import java.util.Set;

public final class DeterministicRetryPolicy implements RetryPolicy {

    private static final Set<FailureType> HARD_STOP_TYPES = Set.of(
            FailureType.UNKNOWN,
            FailureType.USER_ABORT,
            FailureType.SCM_CONFIGURATION_FAILURE,
            FailureType.PIPELINE_LOGIC_FAILURE,
            FailureType.COMPILATION_FAILURE,
            FailureType.TEST_ASSERTION_FAILURE,
            FailureType.DEPLOYMENT_FAILURE);

    @Override
    public RetryDecision decide(FailureClassification classification, RuntimeSettings settings, int attemptNumber) {
        FailureType type = classification.getType();
        if (HARD_STOP_TYPES.contains(type)) {
            return RetryDecision.doNotRetry(type, "Failure type is not retryable: " + type);
        }
        if (!classification.isRetryCandidate()) {
            return RetryDecision.doNotRetry(type, "Classifier marked failure as non-retryable: " + type);
        }
        if (!settings.getRetryableFailureTypes().contains(type)) {
            return RetryDecision.doNotRetry(type, "Active profile does not allow retrying: " + type);
        }

        int maxRetries = settings.getMaxRetries();
        int maxAttempts = maxRetries + 1;
        if (attemptNumber >= maxAttempts) {
            return RetryDecision.doNotRetry(type, "Retry attempts exhausted (maxRetries=" + maxRetries + ")");
        }

        long delayMillis = computeDelayMillis(settings.getBackoff(), settings.getInitialDelaySeconds(), attemptNumber);
        return RetryDecision.retry(type, "Retryable transient failure: " + type, attemptNumber + 1, delayMillis);
    }

    static long computeDelayMillis(BackoffStrategy strategy, int initialDelaySeconds, int attemptNumber) {
        long base = Math.max(0L, initialDelaySeconds) * 1000L;
        if (base == 0L) {
            return 0L;
        }
        if (strategy == BackoffStrategy.EXPONENTIAL) {
            int exponent = Math.max(0, attemptNumber - 1);
            if (exponent > 30) {
                exponent = 30;
            }
            long multiplier = 1L << exponent;
            long product = base * multiplier;
            if (product / multiplier != base) {
                return Long.MAX_VALUE;
            }
            return product;
        }
        return base;
    }
}
