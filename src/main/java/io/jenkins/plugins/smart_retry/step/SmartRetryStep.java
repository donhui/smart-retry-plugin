package io.jenkins.plugins.smart_retry.step;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.smart_retry.config.CustomProfileSettings;
import io.jenkins.plugins.smart_retry.config.SmartRetryGlobalConfiguration;
import io.jenkins.plugins.smart_retry.policy.BuiltInProfiles;
import java.util.Locale;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

public class SmartRetryStep extends Step {

    private String profile;
    private Integer maxRetries;
    private String backoff;
    private Integer initialDelaySeconds;

    @DataBoundConstructor
    public SmartRetryStep() {
        // Required for Jenkins Pipeline databinding.
    }

    @Override
    public StepExecution start(StepContext context) {
        return new SmartRetryStepExecution(this, context);
    }

    public String getProfile() {
        return profile;
    }

    @DataBoundSetter
    public void setProfile(String profile) {
        this.profile = normalizeOptionalValue(profile);
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    @DataBoundSetter
    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getBackoff() {
        return backoff;
    }

    @DataBoundSetter
    public void setBackoff(String backoff) {
        this.backoff = normalizeOptionalValue(backoff);
    }

    public Integer getInitialDelaySeconds() {
        return initialDelaySeconds;
    }

    @DataBoundSetter
    public void setInitialDelaySeconds(Integer initialDelaySeconds) {
        this.initialDelaySeconds = initialDelaySeconds;
    }

    @Extension
    @Symbol("smartRetry")
    public static final class DescriptorImpl extends StepDescriptor {

        private static void checkReadPermission() {
            Jenkins.get().checkPermission(Jenkins.READ);
        }

        @Override
        public String getFunctionName() {
            return "smartRetry";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Retry a Pipeline block for transient infrastructure failures";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @NonNull
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, TaskListener.class);
        }

        @POST
        public ListBoxModel doFillProfileItems() {
            checkReadPermission();
            SmartRetryGlobalConfiguration cfg = SmartRetryGlobalConfiguration.get();
            String effectiveDefault = cfg != null ? cfg.getDefaultProfile() : BuiltInProfiles.PROFILE_CONSERVATIVE;

            ListBoxModel items = new ListBoxModel();
            items.add("Use Jenkins default (" + effectiveDefault + ")", "");
            items.add(BuiltInProfiles.PROFILE_CONSERVATIVE, BuiltInProfiles.PROFILE_CONSERVATIVE);
            items.add(BuiltInProfiles.PROFILE_INFRA, BuiltInProfiles.PROFILE_INFRA);
            if (cfg != null) {
                for (CustomProfileSettings customProfile : cfg.getCustomProfiles()) {
                    String name = customProfile.getName();
                    if (!name.isBlank()) {
                        items.add(name, name);
                    }
                }
            }
            return items;
        }

        @POST
        public ListBoxModel doFillBackoffItems() {
            checkReadPermission();
            ListBoxModel items = new ListBoxModel();
            items.add("Use Jenkins default", "");
            items.add(BuiltInProfiles.BACKOFF_FIXED, BuiltInProfiles.BACKOFF_FIXED);
            items.add(BuiltInProfiles.BACKOFF_EXPONENTIAL, BuiltInProfiles.BACKOFF_EXPONENTIAL);
            return items;
        }

        @POST
        public FormValidation doCheckProfile(@QueryParameter String value) {
            checkReadPermission();
            if (value == null || value.isBlank()) {
                SmartRetryGlobalConfiguration cfg = SmartRetryGlobalConfiguration.get();
                String effectiveDefault = cfg != null ? cfg.getDefaultProfile() : BuiltInProfiles.PROFILE_CONSERVATIVE;
                return FormValidation.ok("Using Jenkins default profile '" + effectiveDefault + "'.");
            }

            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (BuiltInProfiles.PROFILE_CONSERVATIVE.equals(normalized)
                    || BuiltInProfiles.PROFILE_INFRA.equals(normalized)) {
                return FormValidation.ok("Using built-in profile '" + normalized + "'.");
            }

            SmartRetryGlobalConfiguration cfg = SmartRetryGlobalConfiguration.get();
            if (cfg != null) {
                for (CustomProfileSettings customProfile : cfg.getCustomProfiles()) {
                    if (customProfile.matchesName(normalized)) {
                        return FormValidation.ok("Using configured custom profile '" + normalized + "'.");
                    }
                }
            }
            return FormValidation.warning(
                    "Unknown profile name. Smart Retry will reject Pipeline requests that reference this profile.");
        }

        @POST
        public FormValidation doCheckMaxRetries(@QueryParameter String value) {
            checkReadPermission();
            return validateOptionalNonNegativeInteger("Max retries", value);
        }

        @POST
        public FormValidation doCheckInitialDelaySeconds(@QueryParameter String value) {
            checkReadPermission();
            return validateOptionalNonNegativeInteger("Initial delay seconds", value);
        }

        private static FormValidation validateOptionalNonNegativeInteger(String label, String value) {
            if (value == null || value.isBlank()) {
                return FormValidation.ok("Using Jenkins default.");
            }
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed < 0) {
                    return FormValidation.error(label + " must be 0 or greater.");
                }
                return FormValidation.ok(label + " override is valid.");
            } catch (NumberFormatException ignored) {
                return FormValidation.error(label + " must be a whole number.");
            }
        }
    }

    private static String normalizeOptionalValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
