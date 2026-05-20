package io.jenkins.plugins.smart_retry.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.FormValidation;
import io.jenkins.plugins.smart_retry.model.FailureType;
import java.util.List;
import org.junit.jupiter.api.Test;

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
    void validatesProfileNames() {
        CustomProfileSettings.DescriptorImpl descriptor = new CustomProfileSettings.DescriptorImpl();

        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckName("").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckName("infra").kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckName("Network_Only").kind);
    }

    @Test
    void keepsHelpFileAlignedWithCheckboxBinding() {
        assertNotNull(
                CustomProfileSettings.class.getResource(
                        "/io/jenkins/plugins/smart_retry/config/CustomProfileSettings/help-retryableFailureTypeSelections.html"));
    }
}
