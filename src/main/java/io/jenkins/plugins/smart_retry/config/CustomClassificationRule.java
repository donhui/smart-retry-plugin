package io.jenkins.plugins.smart_retry.config;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.smart_retry.model.FailureType;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

public final class CustomClassificationRule extends AbstractDescribableImpl<CustomClassificationRule>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String NAME_PREFIX = "custom-rule-";

    private static final List<FailureType> SUPPORTED_FAILURE_TYPES = List.of(
            FailureType.AGENT_LOST,
            FailureType.SCM_TRANSIENT,
            FailureType.NETWORK_TRANSIENT,
            FailureType.ARTIFACT_REPO_TRANSIENT,
            FailureType.IDENTITY_PROVIDER_TRANSIENT);

    private String name = "";
    private String pattern = "";
    private FailureType failureType = FailureType.UNKNOWN;
    private boolean enabled = true;
    private String description = "";

    @DataBoundConstructor
    public CustomClassificationRule() {
        // Required for Jenkins form databinding.
    }

    public String getName() {
        return name;
    }

    public String getNameSuffix() {
        return stripPrefix(name);
    }

    @DataBoundSetter
    public void setName(@CheckForNull String name) {
        this.name = normalizeName(name);
    }

    @DataBoundSetter
    public void setNameSuffix(@CheckForNull String nameSuffix) {
        this.name = normalizeName(nameSuffix);
    }

    public String getPattern() {
        return pattern;
    }

    @DataBoundSetter
    public void setPattern(@CheckForNull String pattern) {
        this.pattern = normalizePattern(pattern);
    }

    public FailureType getFailureType() {
        return failureType;
    }

    @DataBoundSetter
    public void setFailureType(@CheckForNull FailureType failureType) {
        this.failureType = failureType == null ? FailureType.UNKNOWN : failureType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    @DataBoundSetter
    public void setDescription(@CheckForNull String description) {
        this.description = description == null ? "" : description.trim();
    }

    public boolean matchesName(@CheckForNull String ruleName) {
        return normalizeName(ruleName).equals(name);
    }

    public Pattern compiledPattern() {
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    public static List<FailureType> supportedFailureTypes() {
        return SUPPORTED_FAILURE_TYPES;
    }

    public static String normalizeName(@CheckForNull String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(NAME_PREFIX)) {
            normalized = normalized.substring(NAME_PREFIX.length());
        }
        if (normalized.isBlank()) {
            return "";
        }
        return NAME_PREFIX + normalized;
    }

    public static String stripPrefix(@CheckForNull String value) {
        String normalized = normalizeName(value);
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.substring(NAME_PREFIX.length());
    }

    public static String normalizePattern(@CheckForNull String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<CustomClassificationRule> {

        private static void checkAdministerPermission() {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        }

        public List<FailureType> getSupportedFailureTypes() {
            return supportedFailureTypes();
        }

        @POST
        public ListBoxModel doFillFailureTypeItems() {
            checkAdministerPermission();
            ListBoxModel items = new ListBoxModel();
            for (FailureType type : SUPPORTED_FAILURE_TYPES) {
                items.add(type.name(), type.name());
            }
            return items;
        }

        public String describeFailureType(FailureType type) {
            return switch (type) {
                case AGENT_LOST -> "Agent, pod, node, or remoting channel disappeared during execution.";
                case SCM_TRANSIENT -> "Checkout or fetch failed because of temporary SCM or transport instability.";
                case NETWORK_TRANSIENT -> "An external service was temporarily unreachable.";
                case ARTIFACT_REPO_TRANSIENT -> "A dependency or artifact repository was temporarily unavailable.";
                case IDENTITY_PROVIDER_TRANSIENT -> "LDAP or identity-provider reauthentication failed transiently.";
                default -> "";
            };
        }

        @POST
        public FormValidation doCheckNameSuffix(@QueryParameter String value) {
            checkAdministerPermission();
            String normalized = normalizeName(value);
            if (normalized.isBlank()) {
                return FormValidation.error("Rule name suffix is required.");
            }
            return FormValidation.ok("Rule name will be stored as '" + normalized + "'.");
        }

        @POST
        public FormValidation doCheckName(@QueryParameter String value) {
            checkAdministerPermission();
            return doCheckNameSuffix(value);
        }

        @POST
        public FormValidation doCheckPattern(@QueryParameter String value) {
            checkAdministerPermission();
            String normalized = normalizePattern(value);
            if (normalized.isBlank()) {
                return FormValidation.error("Pattern is required.");
            }
            try {
                Pattern.compile(normalized);
                return FormValidation.ok("Pattern is valid.");
            } catch (PatternSyntaxException e) {
                return FormValidation.error("Pattern is invalid: " + e.getMessage());
            }
        }

        @POST
        public FormValidation doCheckFailureType(@QueryParameter String value) {
            checkAdministerPermission();
            if (value == null || value.isBlank()) {
                return FormValidation.error("Failure type is required.");
            }
            try {
                FailureType type = FailureType.valueOf(value.trim().toUpperCase(Locale.ROOT));
                if (SUPPORTED_FAILURE_TYPES.contains(type)) {
                    return FormValidation.ok("Failure type is supported.");
                }
                return FormValidation.warning("Failure type is reserved by the taxonomy but not supported here.");
            } catch (IllegalArgumentException e) {
                return FormValidation.error("Unknown failure type.");
            }
        }
    }
}
