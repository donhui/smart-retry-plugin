package io.jenkins.plugins.smart_retry.policy;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import io.jenkins.plugins.smart_retry.model.FailureType;
import java.util.Locale;
import java.util.Set;

public final class BuiltInProfiles {

    public static final int DEFAULT_MAX_RETRIES = 1;
    public static final BackoffStrategy DEFAULT_BACKOFF = BackoffStrategy.FIXED;
    public static final int DEFAULT_INITIAL_DELAY_SECONDS = 10;
    public static final String PROFILE_CONSERVATIVE = "conservative";
    public static final String PROFILE_INFRA = "infra";
    public static final String BACKOFF_FIXED = "fixed";
    public static final String BACKOFF_EXPONENTIAL = "exponential";

    private BuiltInProfiles() {}

    public static RuntimeSettings resolve(
            @CheckForNull String profile,
            @CheckForNull Integer maxRetriesOverride,
            @CheckForNull String backoffOverride,
            @CheckForNull Integer initialDelaySecondsOverride) {
        return resolve(defaultsFor(profile), maxRetriesOverride, backoffOverride, initialDelaySecondsOverride);
    }

    public static RuntimeSettings resolve(
            RuntimeSettings defaults,
            @CheckForNull Integer maxRetriesOverride,
            @CheckForNull String backoffOverride,
            @CheckForNull Integer initialDelaySecondsOverride) {
        return new RuntimeSettings(
                defaults.getProfile(),
                defaults.getRetryableFailureTypes(),
                maxRetriesOverride != null ? maxRetriesOverride : defaults.getMaxRetries(),
                backoffOverride != null ? parseBackoff(backoffOverride, defaults.getBackoff()) : defaults.getBackoff(),
                initialDelaySecondsOverride != null ? initialDelaySecondsOverride : defaults.getInitialDelaySeconds());
    }

    public static RuntimeSettings defaultsFor(@CheckForNull String profile) {
        String normalized = normalize(profile);
        if (PROFILE_INFRA.equals(normalized)) {
            return infra();
        }
        if (PROFILE_CONSERVATIVE.equals(normalized)) {
            return conservative();
        }
        throw new IllegalArgumentException("Unknown built-in smartRetry profile: " + profile);
    }

    public static boolean isBuiltInProfile(@CheckForNull String profile) {
        String normalized = normalize(profile);
        return PROFILE_CONSERVATIVE.equals(normalized) || PROFILE_INFRA.equals(normalized);
    }

    public static RuntimeSettings conservative() {
        return new RuntimeSettings(
                PROFILE_CONSERVATIVE,
                Set.of(FailureType.AGENT_LOST, FailureType.SCM_TRANSIENT),
                DEFAULT_MAX_RETRIES,
                DEFAULT_BACKOFF,
                DEFAULT_INITIAL_DELAY_SECONDS);
    }

    public static RuntimeSettings infra() {
        return new RuntimeSettings(
                PROFILE_INFRA,
                Set.of(
                        FailureType.AGENT_LOST,
                        FailureType.SCM_TRANSIENT,
                        FailureType.NETWORK_TRANSIENT,
                        FailureType.ARTIFACT_REPO_TRANSIENT,
                        FailureType.IDENTITY_PROVIDER_TRANSIENT),
                DEFAULT_MAX_RETRIES,
                DEFAULT_BACKOFF,
                DEFAULT_INITIAL_DELAY_SECONDS);
    }

    private static BackoffStrategy parseBackoff(String raw, BackoffStrategy fallback) {
        String normalized = normalize(raw);
        if (BACKOFF_EXPONENTIAL.equals(normalized)) {
            return BackoffStrategy.EXPONENTIAL;
        }
        if (BACKOFF_FIXED.equals(normalized)) {
            return BackoffStrategy.FIXED;
        }
        return fallback;
    }

    @CheckForNull
    private static String normalize(@CheckForNull String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
