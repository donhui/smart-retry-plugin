package io.jenkins.plugins.smart_retry.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public final class RetryDecision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean shouldRetry;
    private final String reason;
    private final FailureType failureType;
    private final int nextAttemptNumber;
    private final long delayMillis;

    public RetryDecision(
            boolean shouldRetry, String reason, FailureType failureType, int nextAttemptNumber, long delayMillis) {
        this.shouldRetry = shouldRetry;
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        this.failureType = Objects.requireNonNull(failureType, "failureType must not be null");
        this.nextAttemptNumber = nextAttemptNumber;
        this.delayMillis = delayMillis;
        validate();
    }

    public static RetryDecision retry(FailureType failureType, String reason, int nextAttemptNumber, long delayMillis) {
        return new RetryDecision(true, reason, failureType, nextAttemptNumber, delayMillis);
    }

    public static RetryDecision doNotRetry(FailureType failureType, String reason) {
        return new RetryDecision(false, reason, failureType, 0, 0L);
    }

    public boolean shouldRetry() {
        return shouldRetry;
    }

    public String getReason() {
        return reason;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public int getNextAttemptNumber() {
        return nextAttemptNumber;
    }

    public long getDelayMillis() {
        return delayMillis;
    }

    private void validate() {
        if (shouldRetry) {
            if (nextAttemptNumber < 1) {
                throw new IllegalArgumentException("nextAttemptNumber must be positive when retrying");
            }
            if (delayMillis < 0L) {
                throw new IllegalArgumentException("delayMillis must not be negative");
            }
            return;
        }
        if (nextAttemptNumber != 0) {
            throw new IllegalArgumentException("nextAttemptNumber must be zero when not retrying");
        }
        if (delayMillis != 0L) {
            throw new IllegalArgumentException("delayMillis must be zero when not retrying");
        }
    }
}
