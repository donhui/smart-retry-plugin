package io.jenkins.plugins.smart_retry.config;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.smart_retry.classify.DeterministicFailureClassifier;
import io.jenkins.plugins.smart_retry.policy.BackoffStrategy;
import io.jenkins.plugins.smart_retry.policy.BuiltInProfiles;
import io.jenkins.plugins.smart_retry.policy.RuntimeSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

@Extension
public final class SmartRetryGlobalConfiguration extends GlobalConfiguration {

    private String defaultProfile = "conservative";
    private int consoleContextLines = 200;
    private int maxRetries = BuiltInProfiles.DEFAULT_MAX_RETRIES;
    private String backoff = BuiltInProfiles.DEFAULT_BACKOFF.name().toLowerCase(Locale.ROOT);
    private int initialDelaySeconds = BuiltInProfiles.DEFAULT_INITIAL_DELAY_SECONDS;
    private List<CustomProfileSettings> customProfiles = new ArrayList<>();
    private String disabledBuiltInRules = "";

    public SmartRetryGlobalConfiguration() {
        load();
    }

    public static SmartRetryGlobalConfiguration get() {
        return GlobalConfiguration.all().get(SmartRetryGlobalConfiguration.class);
    }

    public String getDefaultProfile() {
        return defaultProfile;
    }

    @DataBoundSetter
    public void setDefaultProfile(@CheckForNull String defaultProfile) {
        this.defaultProfile = normalize(defaultProfile, "conservative");
    }

    public int getConsoleContextLines() {
        return consoleContextLines;
    }

    @DataBoundSetter
    public void setConsoleContextLines(int consoleContextLines) {
        this.consoleContextLines = Math.max(0, consoleContextLines);
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    @DataBoundSetter
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
    }

    public String getBackoff() {
        return backoff;
    }

    @DataBoundSetter
    public void setBackoff(@CheckForNull String backoff) {
        this.backoff =
                normalizeBackoff(backoff, BuiltInProfiles.DEFAULT_BACKOFF.name().toLowerCase(Locale.ROOT));
    }

    public int getInitialDelaySeconds() {
        return initialDelaySeconds;
    }

    @DataBoundSetter
    public void setInitialDelaySeconds(int initialDelaySeconds) {
        this.initialDelaySeconds = Math.max(0, initialDelaySeconds);
    }

    public List<CustomProfileSettings> getCustomProfiles() {
        return new ArrayList<>(customProfiles());
    }

    @DataBoundSetter
    public void setCustomProfiles(@CheckForNull List<CustomProfileSettings> customProfiles) {
        this.customProfiles = sanitizeCustomProfiles(customProfiles);
    }

    public RuntimeSettings getProfileSettings(String profileName) {
        String normalized = normalize(profileName, null);
        if (normalized == null) {
            throw new IllegalArgumentException("smartRetry profile name must not be blank");
        }
        if (BuiltInProfiles.isBuiltInProfile(normalized)) {
            RuntimeSettings defaults = BuiltInProfiles.defaultsFor(normalized);
            return new RuntimeSettings(
                    defaults.getProfile(),
                    defaults.getRetryableFailureTypes(),
                    maxRetries,
                    parseBackoff(backoff),
                    initialDelaySeconds);
        }
        for (CustomProfileSettings customProfile : customProfiles()) {
            if (customProfile.matchesName(normalized)) {
                return new RuntimeSettings(
                        customProfile.getName(),
                        customProfile.getRetryableFailureTypeSet(),
                        maxRetries,
                        parseBackoff(backoff),
                        initialDelaySeconds);
            }
        }
        throw new IllegalArgumentException("Unknown smartRetry profile: " + normalized);
    }

    public RuntimeSettings resolveStepSettings(
            @CheckForNull String requestedProfile,
            @CheckForNull Integer maxRetriesOverride,
            @CheckForNull String backoffOverride,
            @CheckForNull Integer initialDelaySecondsOverride) {
        String effectiveProfile = normalize(requestedProfile, getDefaultProfile());
        return BuiltInProfiles.resolve(
                getProfileSettings(effectiveProfile), maxRetriesOverride, backoffOverride, initialDelaySecondsOverride);
    }

    public String getDisabledBuiltInRules() {
        return disabledBuiltInRules;
    }

    public Set<String> getDisabledBuiltInRuleIds() {
        return parseDisabledBuiltInRuleIds(disabledBuiltInRules);
    }

    @DataBoundSetter
    public void setDisabledBuiltInRules(@CheckForNull String disabledBuiltInRules) {
        this.disabledBuiltInRules = formatDisabledBuiltInRuleIds(parseDisabledBuiltInRuleIds(disabledBuiltInRules));
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        validateCustomProfilesForm(json);
        req.bindJSON(this, json);
        validateCustomProfiles(customProfiles());
        save();
        return true;
    }

    public ListBoxModel doFillDefaultProfileItems() {
        ListBoxModel items = new ListBoxModel();
        items.add("conservative", "conservative");
        items.add("infra", "infra");
        for (CustomProfileSettings customProfile : customProfiles()) {
            String name = customProfile.getName();
            if (!name.isBlank()) {
                items.add(name, name);
            }
        }
        return items;
    }

    public ListBoxModel doFillBackoffItems() {
        ListBoxModel items = new ListBoxModel();
        items.add("fixed", "fixed");
        items.add("exponential", "exponential");
        return items;
    }

    public FormValidation doCheckDefaultProfile(@QueryParameter String value) {
        String normalized = normalize(value, "conservative");
        if (BuiltInProfiles.isBuiltInProfile(normalized)) {
            return FormValidation.ok("Using built-in profile '" + normalized + "'.");
        }
        for (CustomProfileSettings customProfile : customProfiles()) {
            if (customProfile.matchesName(normalized)) {
                return FormValidation.ok("Using configured custom profile '" + normalized + "'.");
            }
        }
        return FormValidation.warning(
                "Unknown profile name. Smart Retry will reject Pipeline requests that reference this profile.");
    }

    public FormValidation doCheckDisabledBuiltInRules(@QueryParameter String value) {
        if (value == null || value.isBlank()) {
            return FormValidation.ok("No built-in rules are disabled.");
        }

        String[] tokens = value.split("[,\\r\\n]+");
        Set<String> supported = DeterministicFailureClassifier.supportedDisabledBuiltInRuleIds();
        List<String> unknown = new ArrayList<>();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String id = token.trim();
            if (!supported.contains(id)) {
                unknown.add(id);
            }
        }

        if (unknown.isEmpty()) {
            return FormValidation.ok("Only supported built-in rule ids are listed.");
        }
        return FormValidation.warning("Unknown rule ids will be ignored when saving: " + String.join(", ", unknown));
    }

    public FormValidation doCheckConsoleContextLines(@QueryParameter String value) {
        return validateNonNegativeInteger("Console context lines", value);
    }

    public FormValidation doCheckMaxRetries(@QueryParameter String value) {
        return validateNonNegativeInteger("Max retries", value);
    }

    public FormValidation doCheckInitialDelaySeconds(@QueryParameter String value) {
        return validateNonNegativeInteger("Initial delay seconds", value);
    }

    private List<CustomProfileSettings> customProfiles() {
        if (customProfiles == null) {
            customProfiles = new ArrayList<>();
        }
        return customProfiles;
    }

    static void validateCustomProfilesForm(JSONObject json) throws FormException {
        Object rawProfiles = json.opt("customProfiles");
        if (rawProfiles == null) {
            return;
        }

        List<JSONObject> customProfiles = new ArrayList<>();
        if (rawProfiles instanceof JSONArray array) {
            for (Object candidate : array) {
                if (candidate instanceof JSONObject profileJson) {
                    customProfiles.add(profileJson);
                }
            }
        } else if (rawProfiles instanceof JSONObject profileJson) {
            customProfiles.add(profileJson);
        }

        Set<String> seenNames = new LinkedHashSet<>();
        for (JSONObject customProfile : customProfiles) {
            String normalizedName = CustomProfileSettings.normalizeName(customProfile.optString("name"));
            if (normalizedName.isBlank()) {
                throw new FormException("Custom profile name is required.", "customProfiles");
            }
            if (BuiltInProfiles.isBuiltInProfile(normalizedName)) {
                throw new FormException(
                        "Custom profile '" + normalizedName + "' uses a reserved built-in profile name.",
                        "customProfiles");
            }
            if (!seenNames.add(normalizedName)) {
                throw new FormException(
                        "Custom profile '" + normalizedName + "' is defined more than once.", "customProfiles");
            }
            if (!hasConfiguredFailureTypes(customProfile.opt("retryableFailureTypeSelections"))) {
                throw new FormException(
                        "Custom profile '" + normalizedName + "' must select at least one retryable failure type.",
                        "customProfiles");
            }
        }
    }

    static void validateCustomProfiles(List<CustomProfileSettings> customProfiles) throws FormException {
        if (customProfiles == null || customProfiles.isEmpty()) {
            return;
        }
        for (CustomProfileSettings customProfile : customProfiles) {
            if (customProfile == null) {
                continue;
            }
            if (!customProfile.getName().isBlank()
                    && customProfile.getRetryableFailureTypeSet().isEmpty()) {
                throw new FormException(
                        "Custom profile '" + customProfile.getName()
                                + "' must select at least one retryable failure type.",
                        "customProfiles");
            }
        }
    }

    private static boolean hasConfiguredFailureTypes(Object selections) {
        if (selections instanceof JSONArray array) {
            return !array.isEmpty();
        }
        if (selections instanceof String value) {
            return !value.isBlank();
        }
        return false;
    }

    private static List<CustomProfileSettings> sanitizeCustomProfiles(
            @CheckForNull List<CustomProfileSettings> rawProfiles) {
        if (rawProfiles == null || rawProfiles.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, CustomProfileSettings> orderedProfiles = new LinkedHashMap<>();
        for (CustomProfileSettings rawProfile : rawProfiles) {
            if (rawProfile == null) {
                continue;
            }
            String normalizedName = CustomProfileSettings.normalizeName(rawProfile.getName());
            if (normalizedName.isBlank() || BuiltInProfiles.isBuiltInProfile(normalizedName)) {
                continue;
            }
            if (orderedProfiles.containsKey(normalizedName)) {
                continue;
            }
            CustomProfileSettings sanitized = new CustomProfileSettings();
            sanitized.setName(normalizedName);
            sanitized.setRetryableFailureTypes(rawProfile.getRetryableFailureTypes());
            orderedProfiles.put(normalizedName, sanitized);
        }
        return new ArrayList<>(orderedProfiles.values());
    }

    private static String normalize(@CheckForNull String value, @CheckForNull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static FormValidation validateNonNegativeInteger(String label, @CheckForNull String value) {
        if (value == null || value.isBlank()) {
            return FormValidation.error(label + " is required.");
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0) {
                return FormValidation.error(label + " must be 0 or greater.");
            }
            return FormValidation.ok(label + " is valid.");
        } catch (NumberFormatException ignored) {
            return FormValidation.error(label + " must be a whole number.");
        }
    }

    private static String normalizeBackoff(@CheckForNull String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!Objects.equals(normalized, "fixed") && !Objects.equals(normalized, "exponential")) {
            return fallback;
        }
        return normalized;
    }

    private static BackoffStrategy parseBackoff(String rawValue) {
        return "exponential".equals(normalizeBackoff(rawValue, "fixed"))
                ? BackoffStrategy.EXPONENTIAL
                : BackoffStrategy.FIXED;
    }

    private static Set<String> parseDisabledBuiltInRuleIds(@CheckForNull String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String[] tokens = rawValue.split("[,\\r\\n]+");
        Set<String> supported = DeterministicFailureClassifier.supportedDisabledBuiltInRuleIds();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String id = token.trim();
            if (supported.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }

    private static String formatDisabledBuiltInRuleIds(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return String.join("\n", ids);
    }
}
