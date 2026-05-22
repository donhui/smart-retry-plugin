package io.jenkins.plugins.smart_retry.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.smart_retry.model.FailureClassification;
import io.jenkins.plugins.smart_retry.model.FailureType;
import io.jenkins.plugins.smart_retry.model.RetryDecision;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeterministicRetryPolicyTest {

    private final RetryPolicy policy = new DeterministicRetryPolicy();

    @Test
    void unknownNeverRetries() {
        RuntimeSettings settings = new RuntimeSettings(
                BuiltInProfiles.PROFILE_CONSERVATIVE, Set.of(FailureType.UNKNOWN), 5, BackoffStrategy.FIXED, 15);
        FailureClassification classification =
                FailureClassification.retryCandidate(FailureType.UNKNOWN, "unknown", "unknown");

        RetryDecision decision = policy.decide(classification, settings, 1);

        assertFalse(decision.shouldRetry());
        assertEquals(FailureType.UNKNOWN, decision.getFailureType());
    }

    @Test
    void respectsProfileRetryableTypes() {
        RuntimeSettings settings = new RuntimeSettings(
                BuiltInProfiles.PROFILE_CONSERVATIVE,
                Set.of(FailureType.NETWORK_TRANSIENT),
                2,
                BackoffStrategy.FIXED,
                15);
        FailureClassification classification =
                FailureClassification.retryCandidate(FailureType.SCM_TRANSIENT, "scm", "scm");

        RetryDecision decision = policy.decide(classification, settings, 1);

        assertFalse(decision.shouldRetry());
        assertEquals("Active profile does not allow retrying: SCM_TRANSIENT", decision.getReason());
    }

    @Test
    void attemptExhaustionStopsRetrying() {
        RuntimeSettings settings = new RuntimeSettings(
                BuiltInProfiles.PROFILE_INFRA, Set.of(FailureType.NETWORK_TRANSIENT), 1, BackoffStrategy.FIXED, 15);
        FailureClassification classification =
                FailureClassification.retryCandidate(FailureType.NETWORK_TRANSIENT, "net", "net");

        RetryDecision first = policy.decide(classification, settings, 1);
        assertTrue(first.shouldRetry());

        RetryDecision second = policy.decide(classification, settings, 2);
        assertFalse(second.shouldRetry());
        assertTrue(second.getReason().contains("exhausted"));
    }

    @Test
    void fixedBackoffUsesConstantDelay() {
        long d1 = DeterministicRetryPolicy.computeDelayMillis(BackoffStrategy.FIXED, 15, 1);
        long d2 = DeterministicRetryPolicy.computeDelayMillis(BackoffStrategy.FIXED, 15, 2);
        assertEquals(15000L, d1);
        assertEquals(15000L, d2);
    }

    @Test
    void exponentialBackoffDoublesEachRetry() {
        long d1 = DeterministicRetryPolicy.computeDelayMillis(BackoffStrategy.EXPONENTIAL, 10, 1);
        long d2 = DeterministicRetryPolicy.computeDelayMillis(BackoffStrategy.EXPONENTIAL, 10, 2);
        long d3 = DeterministicRetryPolicy.computeDelayMillis(BackoffStrategy.EXPONENTIAL, 10, 3);
        assertEquals(10000L, d1);
        assertEquals(20000L, d2);
        assertEquals(40000L, d3);
    }

    @Test
    void builtInProfilesTreatIdentityProviderTransientAsInfraOnly() {
        assertFalse(BuiltInProfiles.conservative()
                .getRetryableFailureTypes()
                .contains(FailureType.IDENTITY_PROVIDER_TRANSIENT));
        assertTrue(
                BuiltInProfiles.infra().getRetryableFailureTypes().contains(FailureType.IDENTITY_PROVIDER_TRANSIENT));
    }

    @Test
    void builtInProfilesShareRetryTimingDefaults() {
        assertEquals(
                BuiltInProfiles.conservative().getMaxRetries(),
                BuiltInProfiles.infra().getMaxRetries());
        assertEquals(
                BuiltInProfiles.conservative().getBackoff(),
                BuiltInProfiles.infra().getBackoff());
        assertEquals(
                BuiltInProfiles.conservative().getInitialDelaySeconds(),
                BuiltInProfiles.infra().getInitialDelaySeconds());
        assertEquals(10, BuiltInProfiles.conservative().getInitialDelaySeconds());
    }

    @Test
    void resolveUsesProvidedProfileDefaults() {
        RuntimeSettings defaults = new RuntimeSettings(
                BuiltInProfiles.PROFILE_INFRA, Set.of(FailureType.NETWORK_TRANSIENT), 3, BackoffStrategy.FIXED, 9);

        RuntimeSettings resolved = BuiltInProfiles.resolve(defaults, null, null, null);

        assertEquals(BuiltInProfiles.PROFILE_INFRA, resolved.getProfile());
        assertEquals(Set.of(FailureType.NETWORK_TRANSIENT), resolved.getRetryableFailureTypes());
        assertEquals(3, resolved.getMaxRetries());
        assertEquals(BackoffStrategy.FIXED, resolved.getBackoff());
        assertEquals(9, resolved.getInitialDelaySeconds());
    }
}
