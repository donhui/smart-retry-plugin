package io.jenkins.plugins.smart_retry.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jenkins.management.Badge;
import org.junit.jupiter.api.Test;

class SmartRetryReferenceCatalogTest {

    @Test
    void flowInterruptedRuleKeepsNeverRetryDocsText() {
        SmartRetryReferenceCatalog.MatchedRuleDoc flowInterrupted = SmartRetryReferenceCatalog.matchedRules().stream()
                .filter(rule -> "flow-interrupted".equals(rule.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals("Never retry", flowInterrupted.getDefaultBehavior());
        assertEquals("Exception type", flowInterrupted.getTriggerKind());

        Badge badge = flowInterrupted.getDefaultBehaviorBadge();
        assertNotNull(badge);
        assertTrue(badge.getText().contains("No retry"));
    }

    @Test
    void failureTypeBadgesReflectConfiguredProfileBehavior() {
        SmartRetryReferenceCatalog.FailureTypeDoc networkTransient = SmartRetryReferenceCatalog.failureTypes().stream()
                .filter(type -> "NETWORK_TRANSIENT".equals(type.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals("No retry", networkTransient.getConservativeBehavior());
        assertEquals("Retry allowed", networkTransient.getInfraBehavior());
        assertEquals("Configurable", networkTransient.getCustomBehavior());
    }

    @Test
    void scmConfigurationFailureKeepsNeverRetryDocsText() {
        SmartRetryReferenceCatalog.MatchedRuleDoc rule = SmartRetryReferenceCatalog.matchedRules().stream()
                .filter(item -> "scm-remote-branch-not-found".equals(item.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals("Never retry", rule.getDefaultBehavior());
        assertEquals("No", rule.getDisableable());
    }
}
