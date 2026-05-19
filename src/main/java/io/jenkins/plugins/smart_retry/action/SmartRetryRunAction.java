package io.jenkins.plugins.smart_retry.action;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Action;
import hudson.model.Run;
import io.jenkins.plugins.smart_retry.model.AttemptRecord;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.management.Badge;
import jenkins.model.RunAction2;

public final class SmartRetryRunAction implements Action, RunAction2, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private transient Run<?, ?> run;

    private String profile;
    private final List<AttemptRecord> attempts = new ArrayList<>();

    @CheckForNull
    private String finalOutcome;

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/smart-retry/icons/smart-retry.svg";
    }

    @Override
    public String getDisplayName() {
        return "Smart Retry";
    }

    @Override
    public String getUrlName() {
        return "smartRetry";
    }

    public String getProfile() {
        return profile;
    }

    public boolean isHasProfile() {
        return profile != null && !profile.isBlank();
    }

    @CheckForNull
    public Run<?, ?> getRun() {
        return run;
    }

    public List<AttemptRecord> getAttempts() {
        return Collections.unmodifiableList(attempts);
    }

    public int getAttemptsCount() {
        return attempts.size();
    }

    public boolean isHasAttempts() {
        return !attempts.isEmpty();
    }

    public int getRetryScheduledCount() {
        int count = 0;
        for (AttemptRecord attempt : attempts) {
            if (attempt.isRetried()) {
                count++;
            }
        }
        return count;
    }

    @CheckForNull
    public String getFinalOutcome() {
        return finalOutcome;
    }

    public boolean isHasFinalOutcome() {
        return finalOutcome != null && !finalOutcome.isBlank();
    }

    public String getFinalOutcomeDisplay() {
        if (!isHasFinalOutcome()) {
            return "In progress";
        }
        return formatDisplayValue(finalOutcome);
    }

    public Badge getFinalOutcomeBadge() {
        if (!isHasFinalOutcome()) {
            return new Badge("In progress", "Smart Retry has not reached a terminal outcome yet.", Badge.Severity.INFO);
        }
        if ("SUCCESS".equals(finalOutcome)) {
            return new Badge("Success", "Smart Retry completed successfully.", Badge.Severity.INFO);
        }
        if ("FAILED".equals(finalOutcome)) {
            return new Badge("Failed", "Smart Retry exhausted or rejected further retries.", Badge.Severity.DANGER);
        }
        return new Badge(getFinalOutcomeDisplay(), "Smart Retry recorded this final outcome.", Badge.Severity.INFO);
    }

    public Badge getProfileBadge() {
        String effectiveProfile = isHasProfile() ? profile : "conservative";
        return new Badge(
                "Profile: " + effectiveProfile, "The active Smart Retry profile for this build.", Badge.Severity.INFO);
    }

    public Badge getRetriesScheduledBadge() {
        int retries = getRetryScheduledCount();
        Badge.Severity severity = retries > 0 ? Badge.Severity.INFO : Badge.Severity.WARNING;
        return new Badge(
                retries + " " + pluralize("retry", retries) + " scheduled",
                "How many additional attempts Smart Retry scheduled for this build.",
                severity);
    }

    public String getTerminalFailureTypeDisplay() {
        if ("SUCCESS".equals(finalOutcome)) {
            return "n/a";
        }
        AttemptRecord attempt = getTerminalAttempt();
        if (attempt == null) {
            return "n/a";
        }
        return attempt.getFailureType().name();
    }

    public String getTerminalMatchedRuleDisplay() {
        if ("SUCCESS".equals(finalOutcome)) {
            return "n/a";
        }
        AttemptRecord attempt = getTerminalAttempt();
        if (attempt == null) {
            return "n/a";
        }
        return attempt.getMatchedRuleDisplay();
    }

    public String getLastRetryTriggeringFailureTypeDisplay() {
        AttemptRecord attempt = getTerminalAttempt();
        if (attempt == null) {
            return "n/a";
        }
        return attempt.getFailureType().name();
    }

    public String getLastRetryTriggeringMatchedRuleDisplay() {
        AttemptRecord attempt = getTerminalAttempt();
        if (attempt == null) {
            return "n/a";
        }
        return attempt.getMatchedRuleDisplay();
    }

    public boolean isRecoveredBuild() {
        return "SUCCESS".equals(finalOutcome) && !attempts.isEmpty();
    }

    public String getNarrativeSummary() {
        if ("SUCCESS".equals(finalOutcome) && !isHasAttempts()) {
            return "The build completed successfully without Smart Retry needing to reschedule the body.";
        }

        if (!isHasAttempts()) {
            return "This build finished without recording any Smart Retry attempts.";
        }

        int retries = getRetryScheduledCount();
        AttemptRecord terminalAttempt = getTerminalAttempt();
        int terminalAttemptNumber = terminalAttempt == null ? attempts.size() : terminalAttempt.getAttemptNumber();

        if ("SUCCESS".equals(finalOutcome)) {
            if (retries == 0) {
                return "The build completed successfully without Smart Retry needing to reschedule the body.";
            }
            return "Smart Retry recovered this build after "
                    + retries
                    + " scheduled "
                    + pluralize("retry", retries)
                    + ".";
        }

        if ("FAILED".equals(finalOutcome)) {
            if (retries == 0) {
                return "Smart Retry stopped at attempt "
                        + terminalAttemptNumber
                        + " because the failure was not retryable under the active policy.";
            }
            return "Smart Retry scheduled "
                    + retries
                    + " "
                    + pluralize("retry", retries)
                    + " before stopping at attempt "
                    + terminalAttemptNumber
                    + ".";
        }

        return "Smart Retry has recorded " + attempts.size() + " " + pluralize("attempt", attempts.size()) + " so far.";
    }

    public String getNarrativeReason() {
        if ("SUCCESS".equals(finalOutcome)) {
            AttemptRecord lastFailure = getTerminalAttempt();
            if (lastFailure == null) {
                return "The body succeeded on its first attempt, so no failure classification was needed.";
            }
            return "Smart Retry finished successfully after the last recorded retry-triggering failure was classified as "
                    + lastFailure.getFailureType().name()
                    + " by rule "
                    + lastFailure.getMatchedRuleDisplay()
                    + ".";
        }

        AttemptRecord terminalAttempt = getTerminalAttempt();
        if (terminalAttempt == null) {
            return "No terminal classification is available yet.";
        }

        return "Terminal classification was "
                + terminalAttempt.getFailureType().name()
                + ", matched rule "
                + terminalAttempt.getMatchedRuleDisplay()
                + ", and ended with "
                + terminalAttempt.getRetryDecisionDisplay().toLowerCase()
                + ".";
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public void addAttempt(AttemptRecord record) {
        attempts.add(record);
        saveRun();
    }

    public void setFinalOutcome(String finalOutcome) {
        this.finalOutcome = finalOutcome;
        saveRun();
    }

    private void saveRun() {
        Run<?, ?> r = run;
        if (r == null) {
            return;
        }
        try {
            r.save();
        } catch (IOException ignored) {
            // Best-effort persistence; step behavior must not depend on this.
        }
    }

    public static SmartRetryRunAction getOrCreate(Run<?, ?> run) {
        SmartRetryRunAction existing = run.getAction(SmartRetryRunAction.class);
        if (existing != null) {
            return existing;
        }
        SmartRetryRunAction created = new SmartRetryRunAction();
        run.addAction(created);
        created.onAttached(run);
        try {
            run.save();
        } catch (IOException ignored) {
            // Best-effort.
        }
        return created;
    }

    @CheckForNull
    private AttemptRecord getTerminalAttempt() {
        if (attempts.isEmpty()) {
            return null;
        }
        return attempts.get(attempts.size() - 1);
    }

    private static String formatDisplayValue(String value) {
        String normalized = value.replace('_', ' ').toLowerCase();
        String[] parts = normalized.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private static String pluralize(String word, int count) {
        if (count == 1) {
            return word;
        }
        return word + "s";
    }
}
