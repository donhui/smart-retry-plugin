package io.jenkins.plugins.smart_retry.docs;

import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SmartRetryDocsRootActionTest {

    @Test
    void rendersReferenceCatalog(JenkinsRule jenkins) throws Exception {
        io.jenkins.plugins.smart_retry.config.SmartRetryGlobalConfiguration cfg =
                io.jenkins.plugins.smart_retry.config.SmartRetryGlobalConfiguration.get();
        io.jenkins.plugins.smart_retry.config.CustomClassificationRule rule =
                new io.jenkins.plugins.smart_retry.config.CustomClassificationRule();
        rule.setNameSuffix("network-reset");
        rule.setPattern("connection reset");
        rule.setFailureType(io.jenkins.plugins.smart_retry.model.FailureType.NETWORK_TRANSIENT);
        rule.setDescription("Network reset");
        cfg.setCustomClassificationRules(java.util.List.of(rule));
        cfg.save();

        HtmlPage page = jenkins.createWebClient().goTo("smartRetryDocs");

        String text = page.asNormalizedText();
        String html = page.asXml();
        // App bar title
        Assertions.assertTrue(text.contains("Smart Retry Docs"));
        // Tab panes are rendered server-side and converted into tabs by Jenkins core JS.
        Assertions.assertTrue(html.contains("jenkins-tab-pane__title"));
        Assertions.assertTrue(text.contains("Quick Comparison"));
        Assertions.assertTrue(text.contains("Failure Type Details"));
        Assertions.assertTrue(text.contains("Matched Rules"));
        Assertions.assertTrue(text.contains("Custom Rules"));
        // Quick Comparison tab: failure types appear in table
        Assertions.assertTrue(html.contains("id=\"tab-overview\""));
        Assertions.assertTrue(html.contains("id=\"failure-type-agent-lost\""));
        Assertions.assertTrue(html.contains("id=\"failure-type-scm-transient\""));
        // Failure Type Details tab: master-detail panels present
        Assertions.assertTrue(html.contains("id=\"tab-details\""));
        Assertions.assertTrue(html.contains("id=\"detail-failure-type-agent-lost\""));
        Assertions.assertTrue(html.contains("id=\"detail-failure-type-scm-transient\""));
        // Cross-link from detail panel to rules tab with filter
        Assertions.assertTrue(html.contains("data-tab-target=\"tab-rules\""));
        Assertions.assertTrue(html.contains("data-filter=\"AGENT_LOST\""));
        // Matched Rules tab: rule rows with data-failure-type attribute
        Assertions.assertTrue(html.contains("id=\"tab-rules\""));
        Assertions.assertTrue(html.contains("id=\"rule-agent-remoting-channel-closed\""));
        Assertions.assertTrue(html.contains("data-failure-type=\"SCM_TRANSIENT\""));
        Assertions.assertTrue(html.contains("Trigger kind"));
        // Custom rules tab: configured custom rules are surfaced
        Assertions.assertTrue(html.contains("id=\"tab-custom-rules\""));
        Assertions.assertTrue(html.contains("custom-rule-network-reset"));
        Assertions.assertTrue(html.contains("connection reset"));
        // Filter select populated with failure type names
        Assertions.assertTrue(html.contains("sr-rules-filter"));
    }
}
