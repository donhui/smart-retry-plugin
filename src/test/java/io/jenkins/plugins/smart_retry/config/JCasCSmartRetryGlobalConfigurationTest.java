package io.jenkins.plugins.smart_retry.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.smart_retry.model.FailureType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class JCasCSmartRetryGlobalConfigurationTest {

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    void loadsGlobalConfigurationFromYaml(JenkinsConfiguredWithCodeRule r) throws Exception {
        SmartRetryGlobalConfiguration cfg = SmartRetryGlobalConfiguration.get();

        assertEquals("fixed", cfg.getBackoff());
        assertEquals(200, cfg.getConsoleContextLines());
        assertEquals("conservative", cfg.getDefaultProfile());
        assertEquals(10, cfg.getInitialDelaySeconds());
        assertEquals(1, cfg.getMaxRetries());

        List<CustomProfileSettings> profiles = cfg.getCustomProfiles();
        assertEquals(1, profiles.size());
        assertEquals("okd-infra", profiles.get(0).getName());
        assertEquals(Set.of(FailureType.AGENT_LOST), profiles.get(0).getRetryableFailureTypeSet());

        List<CustomClassificationRule> rules = cfg.getCustomClassificationRules();
        assertEquals(1, rules.size());
        CustomClassificationRule rule = rules.get(0);
        assertEquals("custom-rule-okd-quotas", rule.getName());
        assertEquals("is forbidden: exceeded quota: quota", rule.getPattern());
        assertEquals(FailureType.AGENT_LOST, rule.getFailureType());
        assertEquals("When test (kubedock/testcontainer) test are reaching quotas", rule.getDescription());
    }
}
