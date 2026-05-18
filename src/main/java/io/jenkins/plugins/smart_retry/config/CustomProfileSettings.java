package io.jenkins.plugins.smart_retry.config;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import io.jenkins.plugins.smart_retry.model.FailureType;
import io.jenkins.plugins.smart_retry.policy.BuiltInProfiles;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public final class CustomProfileSettings extends AbstractDescribableImpl<CustomProfileSettings>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<FailureType> SUPPORTED_RETRYABLE_FAILURE_TYPES = List.of(
            FailureType.AGENT_LOST,
            FailureType.SCM_TRANSIENT,
            FailureType.NETWORK_TRANSIENT,
            FailureType.ARTIFACT_REPO_TRANSIENT,
            FailureType.IDENTITY_PROVIDER_TRANSIENT);

    private String name = "";
    private String retryableFailureTypes = "";

    @DataBoundConstructor
    public CustomProfileSettings() {}

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(@CheckForNull String name) {
        this.name = normalizeName(name);
    }

    public String getRetryableFailureTypes() {
        return retryableFailureTypes;
    }

    @DataBoundSetter
    public void setRetryableFailureTypes(@CheckForNull String retryableFailureTypes) {
        this.retryableFailureTypes = formatFailureTypes(parseFailureTypes(retryableFailureTypes));
    }

    public Set<FailureType> getRetryableFailureTypeSet() {
        return parseFailureTypes(retryableFailureTypes);
    }

    public List<String> getRetryableFailureTypeSelections() {
        List<String> selected = new ArrayList<>();
        Set<FailureType> configured = getRetryableFailureTypeSet();
        for (FailureType type : SUPPORTED_RETRYABLE_FAILURE_TYPES) {
            if (configured.contains(type)) {
                selected.add(type.name());
            }
        }
        return selected;
    }

    @DataBoundSetter
    public void setRetryableFailureTypeSelections(@CheckForNull List<String> retryableFailureTypeSelections) {
        if (retryableFailureTypeSelections == null || retryableFailureTypeSelections.isEmpty()) {
            this.retryableFailureTypes = "";
            return;
        }
        this.retryableFailureTypes =
                formatFailureTypes(parseFailureTypes(String.join("\n", retryableFailureTypeSelections)));
    }

    public boolean includesRetryableFailureType(FailureType type) {
        return getRetryableFailureTypeSet().contains(type);
    }

    public boolean matchesName(@CheckForNull String profileName) {
        return normalizeName(profileName).equals(name);
    }

    public static List<FailureType> supportedRetryableFailureTypes() {
        return SUPPORTED_RETRYABLE_FAILURE_TYPES;
    }

    public static String normalizeName(@CheckForNull String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<FailureType> parseFailureTypes(@CheckForNull String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<FailureType> types = new LinkedHashSet<>();
        String[] tokens = rawValue.split("[,\\r\\n]+");
        Set<FailureType> supported = Set.copyOf(SUPPORTED_RETRYABLE_FAILURE_TYPES);
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            try {
                FailureType type = FailureType.valueOf(token.trim().toUpperCase(Locale.ROOT));
                if (supported.contains(type)) {
                    types.add(type);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore unsupported values to keep configuration conservative.
            }
        }
        if (types.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(types));
    }

    private static String formatFailureTypes(Set<FailureType> types) {
        if (types == null || types.isEmpty()) {
            return "";
        }
        List<String> orderedNames = new ArrayList<>();
        for (FailureType type : SUPPORTED_RETRYABLE_FAILURE_TYPES) {
            if (types.contains(type)) {
                orderedNames.add(type.name());
            }
        }
        return String.join("\n", orderedNames);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<CustomProfileSettings> {

        public List<FailureType> getSupportedRetryableFailureTypes() {
            return supportedRetryableFailureTypes();
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            String normalized = normalizeName(value);
            if (normalized.isBlank()) {
                return FormValidation.error("Profile name is required.");
            }
            if (BuiltInProfiles.isBuiltInProfile(normalized)) {
                return FormValidation.error(
                        "Profile name '" + normalized + "' is reserved for a built-in Smart Retry profile.");
            }
            return FormValidation.ok("Profile name will be stored as '" + normalized + "'.");
        }

        public String describeRetryableFailureType(FailureType type) {
            return switch (type) {
                case AGENT_LOST -> "Agent, pod, node, or remoting channel disappeared during execution.";
                case SCM_TRANSIENT -> "Checkout or fetch failed because of temporary SCM or transport instability.";
                case NETWORK_TRANSIENT -> "An external service was temporarily unreachable.";
                case ARTIFACT_REPO_TRANSIENT -> "A dependency or artifact repository was temporarily unavailable.";
                case IDENTITY_PROVIDER_TRANSIENT -> "LDAP or identity-provider reauthentication failed transiently.";
                default -> "";
            };
        }
    }
}
