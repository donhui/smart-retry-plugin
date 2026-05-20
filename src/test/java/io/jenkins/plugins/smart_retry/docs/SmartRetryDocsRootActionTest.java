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
        Assertions.assertTrue(text.contains("Smart Retry Docs"));
        Assertions.assertTrue(text.contains("AGENT_LOST"));
        Assertions.assertTrue(text.contains("agent-remoting-channel-closed"));
        Assertions.assertTrue(text.contains("Retry allowed"));
    }
}
