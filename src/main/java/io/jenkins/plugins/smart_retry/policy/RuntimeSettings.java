package io.jenkins.plugins.smart_retry.policy;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import io.jenkins.plugins.smart_retry.model.FailureType;
import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class RuntimeSettings implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String profile;
    private final Set<FailureType> retryableFailureTypes;
    private final int maxRetries;
    private final BackoffStrategy backoff;
    private final int initialDelaySeconds;

    public RuntimeSettings(
            @CheckForNull String profile,
            Set<FailureType> retryableFailureTypes,
            int maxRetries,
            BackoffStrategy backoff,
            int initialDelaySeconds) {
        this.profile = normalizeProfile(profile);
        this.retryableFailureTypes = Set.copyOf(Objects.requireNonNull(retryableFailureTypes, "retryableFailureTypes"));
        this.maxRetries = Math.max(0, maxRetries);
        this.backoff = Objects.requireNonNull(backoff, "backoff must not be null");
        this.initialDelaySeconds = Math.max(0, initialDelaySeconds);
    }

    public String getProfile() {
        return profile;
    }

    public Set<FailureType> getRetryableFailureTypes() {
        return retryableFailureTypes;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public BackoffStrategy getBackoff() {
        return backoff;
    }

    public int getInitialDelaySeconds() {
        return initialDelaySeconds;
    }

    @CheckForNull
    private static String normalizeProfile(@CheckForNull String profile) {
        if (profile == null || profile.isBlank()) {
            return null;
        }
        return profile.trim().toLowerCase(Locale.ROOT);
    }
}
