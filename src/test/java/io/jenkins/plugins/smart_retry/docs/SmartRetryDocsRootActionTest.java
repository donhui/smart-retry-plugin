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
        Assertions.assertTrue(text.contains("Smart Retry Docs"));
        Assertions.assertTrue(text.contains("Quick Guide"));
        Assertions.assertTrue(text.contains("Common Cases"));
        Assertions.assertTrue(text.contains("Quick Comparison"));
        Assertions.assertTrue(text.contains("Failure Type Details"));
        Assertions.assertTrue(text.contains("AGENT_LOST"));
        Assertions.assertTrue(text.contains("Start with one of these grouped entry points"));
        Assertions.assertTrue(html.contains("group-agent-lost"));
        Assertions.assertTrue(html.contains("rule-agent-remoting-channel-closed"));
        Assertions.assertTrue(html.contains("Trigger kind"));
    }
}
