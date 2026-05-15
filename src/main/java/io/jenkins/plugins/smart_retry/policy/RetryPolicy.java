package io.jenkins.plugins.smart_retry.policy;

import io.jenkins.plugins.smart_retry.model.FailureClassification;
import io.jenkins.plugins.smart_retry.model.RetryDecision;

public interface RetryPolicy {

    RetryDecision decide(FailureClassification classification, RuntimeSettings settings, int attemptNumber);
}
