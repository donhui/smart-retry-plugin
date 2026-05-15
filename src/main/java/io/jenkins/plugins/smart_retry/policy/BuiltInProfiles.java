package io.jenkins.plugins.smart_retry.policy;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import io.jenkins.plugins.smart_retry.model.FailureType;
import java.util.Locale;
import java.util.Set;

public final class BuiltInProfiles {

    public static final int DEFAULT_MAX_RETRIES = 1;
    public static final BackoffStrategy DEFAULT_BACKOFF = BackoffStrategy.FIXED;
    public static final int DEFAULT_INITIAL_DELAY_SECONDS = 15;

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
        if ("infra".equals(normalized)) {
            return infra();
        }
        if ("conservative".equals(normalized)) {
            return conservative();
        }
        throw new IllegalArgumentException("Unknown built-in smartRetry profile: " + profile);
    }

    public static boolean isBuiltInProfile(@CheckForNull String profile) {
        String normalized = normalize(profile);
        return "conservative".equals(normalized) || "infra".equals(normalized);
    }

    public static RuntimeSettings conservative() {
        return new RuntimeSettings(
                "conservative",
                Set.of(FailureType.AGENT_LOST, FailureType.SCM_TRANSIENT),
                DEFAULT_MAX_RETRIES,
                DEFAULT_BACKOFF,
                DEFAULT_INITIAL_DELAY_SECONDS);
    }

    public static RuntimeSettings infra() {
        return new RuntimeSettings(
                "infra",
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
        if ("exponential".equals(normalized)) {
            return BackoffStrategy.EXPONENTIAL;
        }
        if ("fixed".equals(normalized)) {
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
