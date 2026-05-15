package io.jenkins.plugins.smart_retry.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public final class FailureClassification implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final FailureType type;
    private final String matchedRule;
    private final String summary;
    private final boolean retryCandidate;

    public FailureClassification(
            FailureType type, @CheckForNull String matchedRule, String summary, boolean retryCandidate) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.matchedRule = normalizeMatchedRule(matchedRule);
        this.summary = Objects.requireNonNull(summary, "summary must not be null");
        this.retryCandidate = retryCandidate;
    }

    public static FailureClassification retryCandidate(
            FailureType type, @CheckForNull String matchedRule, String summary) {
        return new FailureClassification(type, matchedRule, summary, true);
    }

    public static FailureClassification nonRetryable(
            FailureType type, @CheckForNull String matchedRule, String summary) {
        return new FailureClassification(type, matchedRule, summary, false);
    }

    public FailureType getType() {
        return type;
    }

    @CheckForNull
    public String getMatchedRule() {
        return matchedRule;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isRetryCandidate() {
        return retryCandidate;
    }

    @CheckForNull
    private static String normalizeMatchedRule(@CheckForNull String matchedRule) {
        if (matchedRule == null || matchedRule.isBlank()) {
            return null;
        }
        return matchedRule;
    }
}
