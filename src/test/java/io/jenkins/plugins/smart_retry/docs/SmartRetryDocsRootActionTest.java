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
        HtmlPage page = jenkins.createWebClient().goTo("smartRetryDocs");

        String text = page.asNormalizedText();
        String html = page.asXml();
        // App bar title
        Assertions.assertTrue(text.contains("Smart Retry Docs"));
        // Tab bar labels
        Assertions.assertTrue(html.contains("data-tab=\"tab-overview\""));
        Assertions.assertTrue(html.contains("data-tab=\"tab-details\""));
        Assertions.assertTrue(html.contains("data-tab=\"tab-rules\""));
        // Quick Comparison tab: failure types appear in table
        Assertions.assertTrue(html.contains("id=\"failure-type-agent-lost\""));
        Assertions.assertTrue(html.contains("id=\"failure-type-scm-transient\""));
        // Failure Type Details tab: master-detail panels present
        Assertions.assertTrue(html.contains("id=\"detail-failure-type-agent-lost\""));
        Assertions.assertTrue(html.contains("id=\"detail-failure-type-scm-transient\""));
        // Cross-link from detail panel to rules tab with filter
        Assertions.assertTrue(html.contains("data-filter=\"AGENT_LOST\""));
        // Matched Rules tab: rule rows with data-failure-type attribute
        Assertions.assertTrue(html.contains("id=\"rule-agent-remoting-channel-closed\""));
        Assertions.assertTrue(html.contains("data-failure-type=\"SCM_TRANSIENT\""));
        Assertions.assertTrue(html.contains("Trigger kind"));
        // Filter select populated with failure type names
        Assertions.assertTrue(html.contains("sr-rules-filter"));
    }
}
