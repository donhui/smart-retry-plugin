package io.jenkins.plugins.smart_retry.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.smart_retry.model.FailureType;
import io.jenkins.plugins.smart_retry.policy.BuiltInProfiles;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CustomProfileSettingsTest {

    @Test
    void normalizesCheckboxSelectionsIntoStoredFailureTypes() {
        CustomProfileSettings profile = new CustomProfileSettings();

        profile.setRetryableFailureTypeSelections(List.of("NETWORK_TRANSIENT", "AGENT_LOST", "unknown"));

        assertEquals("AGENT_LOST\nNETWORK_TRANSIENT", profile.getRetryableFailureTypes());
        assertEquals(List.of("AGENT_LOST", "NETWORK_TRANSIENT"), profile.getRetryableFailureTypeSelections());
        assertTrue(profile.includesRetryableFailureType(FailureType.AGENT_LOST));
    }

    @Test
    void clearsStoredFailureTypesWhenSelectionsAreEmpty() {
        CustomProfileSettings profile = new CustomProfileSettings();
        profile.setRetryableFailureTypes("agent_lost\nnetwork_transient");

        profile.setRetryableFailureTypeSelections(List.of());

        assertEquals("", profile.getRetryableFailureTypes());
        assertEquals(List.of(), profile.getRetryableFailureTypeSelections());
    }

    @Test
    void validatesProfileNames(JenkinsRule jenkins) {
        CustomProfileSettings.DescriptorImpl descriptor = new CustomProfileSettings.DescriptorImpl();

        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckName("").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckName(BuiltInProfiles.PROFILE_INFRA).kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckName("Network_Only").kind);
    }

    @Test
    void exposesSupportedFailureTypesForSelectionWidgets(JenkinsRule jenkins) {
        CustomClassificationRule.DescriptorImpl descriptor = new CustomClassificationRule.DescriptorImpl();
        ListBoxModel items = descriptor.doFillFailureTypeItems();

        assertEquals(5, items.size());
        assertEquals("AGENT_LOST", items.get(0).value);
        assertEquals("IDENTITY_PROVIDER_TRANSIENT", items.get(4).value);
    }

    @Test
    void keepsHelpFileAlignedWithCheckboxBinding() {
        assertNotNull(
                CustomProfileSettings.class.getResource(
                        "/io/jenkins/plugins/smart_retry/config/CustomProfileSettings/help-retryableFailureTypeSelections.html"));
    }

    @Test
    void definesGroupedCheckboxMarkupForRetryableFailureTypes() throws Exception {
        String jelly = new String(
                CustomProfileSettings.class
                        .getResourceAsStream(
                                "/io/jenkins/plugins/smart_retry/config/CustomProfileSettings/config.jelly")
                        .readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(jelly.contains("sr-checkbox-group"));
        assertTrue(jelly.contains("sr-checkbox-option"));
        assertTrue(jelly.contains("sr-checkbox-option__description"));
        assertTrue(jelly.contains("descriptor.describeRetryableFailureType(failureType)"));
        assertTrue(jelly.contains("title=\"${failureType.name()}\""));
    }
}
