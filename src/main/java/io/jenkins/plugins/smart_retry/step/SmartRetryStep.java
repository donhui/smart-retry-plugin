package io.jenkins.plugins.smart_retry.step;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.smart_retry.config.CustomProfileSettings;
import io.jenkins.plugins.smart_retry.config.SmartRetryGlobalConfiguration;
import java.util.Locale;
import java.util.Set;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class SmartRetryStep extends Step {

    private String profile;
    private Integer maxRetries;
    private String backoff;
    private Integer initialDelaySeconds;

    @DataBoundConstructor
    public SmartRetryStep() {}

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

        public ListBoxModel doFillProfileItems() {
            SmartRetryGlobalConfiguration cfg = SmartRetryGlobalConfiguration.get();
            String effectiveDefault = cfg != null ? cfg.getDefaultProfile() : "conservative";

            ListBoxModel items = new ListBoxModel();
            items.add("Use Jenkins default (" + effectiveDefault + ")", "");
            items.add("conservative", "conservative");
            items.add("infra", "infra");
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

        public ListBoxModel doFillBackoffItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("Use Jenkins default", "");
            items.add("fixed", "fixed");
            items.add("exponential", "exponential");
            return items;
        }

        public FormValidation doCheckProfile(@QueryParameter String value) {
            if (value == null || value.isBlank()) {
                SmartRetryGlobalConfiguration cfg = SmartRetryGlobalConfiguration.get();
                String effectiveDefault = cfg != null ? cfg.getDefaultProfile() : "conservative";
                return FormValidation.ok("Using Jenkins default profile '" + effectiveDefault + "'.");
            }

            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if ("conservative".equals(normalized) || "infra".equals(normalized)) {
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

        public FormValidation doCheckMaxRetries(@QueryParameter String value) {
            return validateOptionalNonNegativeInteger("Max retries", value);
        }

        public FormValidation doCheckInitialDelaySeconds(@QueryParameter String value) {
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
