package io.jenkins.plugins.smart_retry.step;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Set;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

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
        this.profile = profile;
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
        this.backoff = backoff;
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
    }
}
