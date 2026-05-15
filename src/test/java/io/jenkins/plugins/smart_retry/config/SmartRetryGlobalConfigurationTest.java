package io.jenkins.plugins.smart_retry.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.smart_retry.model.FailureType;
import io.jenkins.plugins.smart_retry.policy.BackoffStrategy;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SmartRetryGlobalConfigurationTest {

    @Test
    void persistsAndReloads(JenkinsRule jenkins) throws Exception {
        SmartRetryGlobalConfiguration cfg = SmartRetryGlobalConfiguration.get();

        CustomProfileSettings release = new CustomProfileSettings();
        release.setName("release");
        release.setRetryableFailureTypes("network_transient\nartifact_repo_transient\nunknown");

        CustomProfileSettings duplicate = new CustomProfileSettings();
        duplicate.setName("release");
        duplicate.setRetryableFailureTypes("agent_lost");

        CustomProfileSettings reserved = new CustomProfileSettings();
        reserved.setName("infra");
        reserved.setRetryableFailureTypes("agent_lost");

        cfg.setDefaultProfile("release");
        cfg.setConsoleContextLines(50);
        cfg.setMaxRetries(3);
        cfg.setBackoff("exponential");
        cfg.setInitialDelaySeconds(7);
        cfg.setCustomProfiles(List.of(release, duplicate, reserved));
        cfg.setDisabledBuiltInRules("scm-remote-end-hung-up\nflow-interrupted\nartifact-connection-reset\nnot-a-rule");
        cfg.save();

        cfg.load();

        assertEquals("release", cfg.getDefaultProfile());
        assertEquals(50, cfg.getConsoleContextLines());
        assertEquals(3, cfg.getMaxRetries());
        assertEquals("exponential", cfg.getBackoff());
        assertEquals(7, cfg.getInitialDelaySeconds());
        assertEquals(1, cfg.getCustomProfiles().size());
        assertEquals("release", cfg.getCustomProfiles().get(0).getName());
        assertEquals(
                "NETWORK_TRANSIENT\nARTIFACT_REPO_TRANSIENT",
                cfg.getCustomProfiles().get(0).getRetryableFailureTypes());
        assertEquals(
                Set.of(FailureType.AGENT_LOST, FailureType.SCM_TRANSIENT),
                cfg.getProfileSettings("conservative").getRetryableFailureTypes());
        assertEquals(
                BackoffStrategy.EXPONENTIAL,
                cfg.getProfileSettings("conservative").getBackoff());
        assertEquals(
                Set.of(
                        FailureType.AGENT_LOST,
                        FailureType.SCM_TRANSIENT,
                        FailureType.NETWORK_TRANSIENT,
                        FailureType.ARTIFACT_REPO_TRANSIENT,
                        FailureType.IDENTITY_PROVIDER_TRANSIENT),
                cfg.getProfileSettings("infra").getRetryableFailureTypes());
        assertEquals(3, cfg.getProfileSettings("infra").getMaxRetries());
        assertEquals(
                Set.of(FailureType.NETWORK_TRANSIENT, FailureType.ARTIFACT_REPO_TRANSIENT),
                cfg.getProfileSettings("release").getRetryableFailureTypes());
        assertEquals(
                BackoffStrategy.EXPONENTIAL, cfg.getProfileSettings("release").getBackoff());
        assertEquals("scm-remote-end-hung-up\nartifact-connection-reset", cfg.getDisabledBuiltInRules());
        assertEquals(Set.of("scm-remote-end-hung-up", "artifact-connection-reset"), cfg.getDisabledBuiltInRuleIds());
    }

    @Test
    void rejectsUnknownProfilesAtResolutionTime(JenkinsRule jenkins) {
        SmartRetryGlobalConfiguration cfg = SmartRetryGlobalConfiguration.get();

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> cfg.getProfileSettings("missing"));

        assertEquals("Unknown smartRetry profile: missing", exception.getMessage());
    }

    @Test
    void exposesProfileAndBackoffChoices(JenkinsRule jenkins) {
        SmartRetryGlobalConfiguration cfg = SmartRetryGlobalConfiguration.get();

        CustomProfileSettings release = new CustomProfileSettings();
        release.setName("release");
        release.setRetryableFailureTypes("network_transient");
        cfg.setCustomProfiles(List.of(release));

        ListBoxModel profileItems = cfg.doFillDefaultProfileItems();
        ListBoxModel backoffItems = cfg.doFillBackoffItems();

        assertEquals(3, profileItems.size());
        assertEquals("conservative", profileItems.get(0).value);
        assertEquals("infra", profileItems.get(1).value);
        assertEquals("release", profileItems.get(2).value);
        assertEquals(2, backoffItems.size());
        assertEquals("fixed", backoffItems.get(0).value);
        assertEquals("exponential", backoffItems.get(1).value);
    }

    @Test
    void validatesDefaultProfileAndDisabledRules(JenkinsRule jenkins) {
        SmartRetryGlobalConfiguration cfg = SmartRetryGlobalConfiguration.get();

        CustomProfileSettings release = new CustomProfileSettings();
        release.setName("release");
        release.setRetryableFailureTypes("network_transient");
        cfg.setCustomProfiles(List.of(release));

        assertEquals(FormValidation.Kind.OK, cfg.doCheckDefaultProfile("infra").kind);
        assertEquals(FormValidation.Kind.OK, cfg.doCheckDefaultProfile("release").kind);
        assertEquals(FormValidation.Kind.WARNING, cfg.doCheckDefaultProfile("missing").kind);
        assertEquals(FormValidation.Kind.OK, cfg.doCheckDisabledBuiltInRules("").kind);
        assertEquals(
                FormValidation.Kind.OK,
                cfg.doCheckDisabledBuiltInRules("scm-remote-end-hung-up\nartifact-connection-reset").kind);
        assertEquals(
                FormValidation.Kind.WARNING,
                cfg.doCheckDisabledBuiltInRules("scm-remote-end-hung-up\nunknown-rule").kind);
    }
}
