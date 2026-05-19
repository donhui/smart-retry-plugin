package io.jenkins.plugins.smart_retry.step;

import io.jenkins.plugins.smart_retry.action.SmartRetryRunAction;
import io.jenkins.plugins.smart_retry.config.CustomProfileSettings;
import io.jenkins.plugins.smart_retry.config.SmartRetryGlobalConfiguration;
import java.util.List;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SmartRetryStepTest {

    @Test
    void executesBodyBlock(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-body");
        job.setDefinition(new CpsFlowDefinition("smartRetry {\n  echo 'inside smart retry'\n}", true));

        WorkflowRun build = jenkins.buildAndAssertSuccess(job);

        jenkins.assertLogContains("inside smart retry", build);
        jenkins.assertLogContains(
                "[smartRetry] completed profile=conservative result=SUCCESS attempts=1 retriesUsed=0 reason=\"Body succeeded on first attempt\"",
                build);
    }

    @Test
    void createsRunActionForDirectSuccess(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-direct-success");
        job.setDefinition(new CpsFlowDefinition("smartRetry {\n  echo 'no retry needed'\n}", true));

        WorkflowRun build = jenkins.buildAndAssertSuccess(job);
        SmartRetryRunAction action = build.getAction(SmartRetryRunAction.class);

        Assertions.assertNotNull(action);
        Assertions.assertEquals("SUCCESS", action.getFinalOutcome());
        Assertions.assertEquals("conservative", action.getProfile());
        Assertions.assertEquals(0, action.getAttempts().size());

        HtmlPage page = jenkins.createWebClient().goTo(build.getUrl() + action.getUrlName());

        Assertions.assertTrue(page.asNormalizedText()
                .contains("The build completed successfully without Smart Retry needing to reschedule the body."));
        Assertions.assertTrue(page.asNormalizedText()
                .contains("The body succeeded on its first attempt, so no failure classification was needed."));
        Assertions.assertTrue(page.asNormalizedText().contains("Success"));
        Assertions.assertFalse(page.asNormalizedText().contains("Terminal failure type"));
    }

    @Test
    void bindsInitialParameters(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-parameters");
        job.setDefinition(new CpsFlowDefinition(
                "smartRetry(profile: 'infra', maxRetries: 2, backoff: 'exponential', initialDelaySeconds: 15) {\n"
                        + "  echo 'configured smart retry'\n"
                        + "}",
                true));

        WorkflowRun build = jenkins.buildAndAssertSuccess(job);

        jenkins.assertLogContains("configured smart retry", build);
    }

    @Test
    void logsClassificationAndDecisionOnFailure(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-failure-logging");
        job.setDefinition(new CpsFlowDefinition("smartRetry { error('remote end hung up unexpectedly') }", true));

        WorkflowRun build = jenkins.assertBuildStatus(hudson.model.Result.FAILURE, job.scheduleBuild2(0));

        jenkins.assertLogContains("[smartRetry] attempt=1 profile=conservative", build);
        jenkins.assertLogContains("classified=SCM_TRANSIENT", build);
        jenkins.assertLogContains("decision=RETRY", build);
    }

    @Test
    void retriesThenSucceeds(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-retry-success");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  smartRetry(maxRetries: 1, backoff: 'fixed', initialDelaySeconds: 0) {\n"
                        + "    if (fileExists('sentinel')) {\n"
                        + "      echo 'second attempt'\n"
                        + "    } else {\n"
                        + "      writeFile file: 'sentinel', text: 'x'\n"
                        + "      error('remote end hung up unexpectedly')\n"
                        + "    }\n"
                        + "  }\n"
                        + "}",
                true));

        WorkflowRun build = jenkins.buildAndAssertSuccess(job);
        SmartRetryRunAction action = build.getAction(SmartRetryRunAction.class);

        jenkins.assertLogContains("[smartRetry] attempt=1", build);
        jenkins.assertLogContains("decision=RETRY", build);
        jenkins.assertLogContains(
                "[smartRetry] scheduling profile=conservative classified=SCM_TRANSIENT nextAttempt=2 delayMillis=0",
                build);
        jenkins.assertLogContains("second attempt", build);
        jenkins.assertLogContains(
                "[smartRetry] completed profile=conservative result=SUCCESS attempts=2 retriesUsed=1 reason=\"Recovered after 1 scheduled retry\"",
                build);
        Assertions.assertNotNull(action);
        Assertions.assertEquals("SUCCESS", action.getFinalOutcome());
        Assertions.assertEquals(1, action.getAttempts().size());
        Assertions.assertEquals("RETRY_SCHEDULED", action.getAttempts().get(0).getOutcome());

        HtmlPage page = jenkins.createWebClient().goTo(build.getUrl() + action.getUrlName());

        Assertions.assertTrue(
                page.asNormalizedText().contains("Smart Retry recovered this build after 1 scheduled retry."));
        Assertions.assertTrue(
                page.asNormalizedText()
                        .contains(
                                "Smart Retry finished successfully after the last recorded retry-triggering failure was classified as SCM_TRANSIENT by rule scm-remote-end-hung-up."));
        Assertions.assertTrue(page.asNormalizedText().contains("Last retry-triggering failure type"));
        Assertions.assertTrue(page.asNormalizedText().contains("Last retry-triggering rule"));
        Assertions.assertFalse(page.asNormalizedText().contains("Terminal failure type"));
        Assertions.assertFalse(page.asNormalizedText().contains("ended with retry scheduled"));
    }

    @Test
    void retriesThenExhausts(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-retry-exhausts");
        job.setDefinition(new CpsFlowDefinition(
                "smartRetry(maxRetries: 1, backoff: 'fixed', initialDelaySeconds: 0) {\n"
                        + "  error('remote end hung up unexpectedly')\n"
                        + "}",
                true));

        WorkflowRun build = jenkins.assertBuildStatus(hudson.model.Result.FAILURE, job.scheduleBuild2(0));

        jenkins.assertLogContains("[smartRetry] attempt=1", build);
        jenkins.assertLogContains("decision=RETRY", build);
        jenkins.assertLogContains("[smartRetry] attempt=2", build);
        jenkins.assertLogContains("decision=FAIL", build);
        jenkins.assertLogContains("exhausted", build);
        jenkins.assertLogContains(
                "[smartRetry] completed profile=conservative result=FAILED attempts=2 classified=SCM_TRANSIENT reason=\"Retry attempts exhausted (maxRetries=1)\"",
                build);

        SmartRetryRunAction action = build.getAction(SmartRetryRunAction.class);
        Assertions.assertNotNull(action);
        Assertions.assertEquals("FAILED", action.getFinalOutcome());
        Assertions.assertEquals(2, action.getAttempts().size());
    }

    @Test
    void exposesDedicatedRunActionPage(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-action-page");
        job.setDefinition(new CpsFlowDefinition(
                "smartRetry(maxRetries: 1, backoff: 'fixed', initialDelaySeconds: 0) {\n"
                        + "  error('remote end hung up unexpectedly')\n"
                        + "}",
                true));

        WorkflowRun build = jenkins.assertBuildStatus(hudson.model.Result.FAILURE, job.scheduleBuild2(0));
        SmartRetryRunAction action = build.getAction(SmartRetryRunAction.class);

        Assertions.assertNotNull(action);
        Assertions.assertEquals("/plugin/smart-retry/icons/smart-retry.svg", action.getIconFileName());

        HtmlPage page = jenkins.createWebClient().goTo(build.getUrl() + action.getUrlName());

        Assertions.assertTrue(page.asNormalizedText().contains("Smart Retry"));
        Assertions.assertTrue(page.asNormalizedText().contains("Deterministic retry details for this build."));
        Assertions.assertTrue(page.asNormalizedText().contains("Recorded attempts"));
        Assertions.assertTrue(
                page.asNormalizedText().contains("Smart Retry scheduled 1 retry before stopping at attempt 2."));
        Assertions.assertTrue(page.asNormalizedText().contains("Terminal classification was SCM_TRANSIENT"));
        Assertions.assertTrue(page.asNormalizedText().contains("Failed"));
        Assertions.assertTrue(page.asNormalizedText().contains("Retry scheduled"));
        Assertions.assertTrue(page.asNormalizedText().contains("SCM_TRANSIENT"));
        Assertions.assertTrue(page.asXml().contains("smartRetryDocs"));
        Assertions.assertTrue(page.asXml().contains("failure-type-scm-transient"));
        Assertions.assertTrue(page.asXml().contains("scm-remote-end-hung-up"));
    }

    @Test
    void exposesDocumentationPage(JenkinsRule jenkins) throws Exception {
        HtmlPage page = jenkins.createWebClient().goTo("smartRetryDocs");

        Assertions.assertTrue(page.asNormalizedText().contains("Smart Retry Docs"));
        Assertions.assertTrue(page.asNormalizedText().contains("Failure Types"));
        Assertions.assertTrue(page.asNormalizedText().contains("Matched Rules"));
        Assertions.assertTrue(page.asNormalizedText().contains("SCM_TRANSIENT"));
        Assertions.assertTrue(page.asNormalizedText().contains("scm-could-not-resolve-host"));
        Assertions.assertTrue(page.asNormalizedText().contains("network-could-not-resolve-host"));
        Assertions.assertTrue(page.asNormalizedText().contains("artifact-partial-download"));
        Assertions.assertTrue(page.asNormalizedText().contains("test-pytest-failures-summary"));
        Assertions.assertTrue(page.asNormalizedText().contains("gradle-compile-task-failed"));
        Assertions.assertTrue(page.asNormalizedText().contains("java-compiler-error"));
        Assertions.assertTrue(page.asNormalizedText().contains("kotlin-compiler-error"));
        Assertions.assertTrue(page.asNormalizedText().contains("test-gradle-failures-summary"));
    }

    @Test
    void usesConsoleContextToClassifyShFailures(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-log-context");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  smartRetry(maxRetries: 1, backoff: 'fixed', initialDelaySeconds: 0) {\n"
                        + "    sh '>&2 echo \"Could not resolve host: example.invalid\"; exit 6'\n"
                        + "  }\n"
                        + "}",
                true));

        WorkflowRun build = jenkins.assertBuildStatus(hudson.model.Result.FAILURE, job.scheduleBuild2(0));

        jenkins.assertLogContains("[smartRetry] attempt=1", build);
        jenkins.assertLogContains("classified=NETWORK_TRANSIENT", build);
        jenkins.assertLogContains("decision=FAIL", build);
        jenkins.assertLogContains(
                "[smartRetry] completed profile=conservative result=FAILED attempts=1 classified=NETWORK_TRANSIENT reason=\"Active profile does not allow retrying: NETWORK_TRANSIENT\"",
                build);
        jenkins.assertLogNotContains("[smartRetry] attempt=2", build);
    }

    @Test
    void classifiesLdapReauthenticationFailuresInInfraProfile(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-ldap-reauth");
        job.setDefinition(new CpsFlowDefinition(
                "smartRetry(profile: 'infra', maxRetries: 1, backoff: 'fixed', initialDelaySeconds: 0) {\n"
                        + "  error(\"request url http://ldap.example/api, response code 401\\n"
                        + "Can't reauthenticate LDAP for user: 'xxx': user is locked, disabled or does not exist in LDAP\")\n"
                        + "}",
                true));

        WorkflowRun build = jenkins.assertBuildStatus(hudson.model.Result.FAILURE, job.scheduleBuild2(0));

        jenkins.assertLogContains("classified=IDENTITY_PROVIDER_TRANSIENT", build);
        jenkins.assertLogContains("decision=RETRY", build);
    }

    @Test
    void usesConfiguredSharedRetryDefaultsForInfraProfile(JenkinsRule jenkins) throws Exception {
        SmartRetryGlobalConfiguration cfg = SmartRetryGlobalConfiguration.get();
        cfg.setDefaultProfile("infra");
        cfg.setMaxRetries(1);
        cfg.setBackoff("fixed");
        cfg.setInitialDelaySeconds(0);
        cfg.save();

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-configured-infra-profile");
        job.setDefinition(new CpsFlowDefinition(
                "smartRetry {\n"
                        + "  error('request url https://services.gradle.org/distributions/gradle-8.11-bin.zip connect timed out')\n"
                        + "}",
                true));

        WorkflowRun build = jenkins.assertBuildStatus(hudson.model.Result.FAILURE, job.scheduleBuild2(0));

        jenkins.assertLogContains("[smartRetry] attempt=1 profile=infra", build);
        jenkins.assertLogContains("classified=NETWORK_TRANSIENT", build);
        jenkins.assertLogContains("decision=RETRY", build);
        jenkins.assertLogContains("[smartRetry] attempt=2", build);
    }

    @Test
    void usesNamedCustomProfile(JenkinsRule jenkins) throws Exception {
        SmartRetryGlobalConfiguration cfg = SmartRetryGlobalConfiguration.get();
        CustomProfileSettings release = new CustomProfileSettings();
        release.setName("release");
        release.setRetryableFailureTypes("NETWORK_TRANSIENT");
        cfg.setCustomProfiles(List.of(release));
        cfg.setMaxRetries(1);
        cfg.setBackoff("fixed");
        cfg.setInitialDelaySeconds(0);
        cfg.save();

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-custom-profile");
        job.setDefinition(new CpsFlowDefinition(
                "smartRetry(profile: 'release') {\n"
                        + "  error('request url https://services.gradle.org/distributions/gradle-8.11-bin.zip connect timed out')\n"
                        + "}",
                true));

        WorkflowRun build = jenkins.assertBuildStatus(hudson.model.Result.FAILURE, job.scheduleBuild2(0));

        jenkins.assertLogContains("[smartRetry] attempt=1 profile=release", build);
        jenkins.assertLogContains("classified=NETWORK_TRANSIENT", build);
        jenkins.assertLogContains("decision=RETRY", build);
        jenkins.assertLogContains("[smartRetry] attempt=2", build);
    }

    @Test
    void failsFastForUnknownProfile(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-unknown-profile");
        job.setDefinition(new CpsFlowDefinition(
                "smartRetry(profile: 'missing-profile') {\n" + "  echo 'should-not-run'\n" + "}", true));

        WorkflowRun build = jenkins.assertBuildStatus(hudson.model.Result.FAILURE, job.scheduleBuild2(0));

        jenkins.assertLogContains("Unknown smartRetry profile: missing-profile", build);
        jenkins.assertLogNotContains("should-not-run", build);
        jenkins.assertLogNotContains("[smartRetry] begin attempt=1", build);
    }

    @Test
    void classifiesGitTransportInterruptionsFromConsoleContextAsScmTransient(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-git-transport");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  smartRetry(maxRetries: 1, backoff: 'fixed', initialDelaySeconds: 0) {\n"
                        + "    sh '''\n"
                        + "      >&2 echo \"git clone --depth 10 --recurse-submodules http://gitlab.example/repo.git -b dev\"\n"
                        + "      >&2 echo \"error: RPC failed; curl 56 Problem (3) in the Chunked-Encoded data\"\n"
                        + "      >&2 echo \"fetch-pack: unexpected disconnect while reading sideband packet\"\n"
                        + "      >&2 echo \"fatal: early EOF\"\n"
                        + "      >&2 echo \"fatal: index-pack failed\"\n"
                        + "      exit 1\n"
                        + "    '''\n"
                        + "  }\n"
                        + "}",
                true));

        WorkflowRun build = jenkins.assertBuildStatus(hudson.model.Result.FAILURE, job.scheduleBuild2(0));

        jenkins.assertLogContains("classified=SCM_TRANSIENT", build);
        jenkins.assertLogContains("decision=RETRY", build);
        jenkins.assertLogContains("[smartRetry] attempt=2", build);
    }

    @Test
    void classifiesConnectionRefusedFromConsoleContextAsNetworkTransient(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-connection-refused");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  smartRetry(profile: 'infra', maxRetries: 1, backoff: 'fixed', initialDelaySeconds: 0) {\n"
                        + "    sh '''\n"
                        + "      >&2 echo \"docker pull jfrog.example/docker/ci_tool:latest\"\n"
                        + "      >&2 echo \"Error response from daemon: Get \\\"https://jfrog.example/v2/\\\": dial tcp 100.13.11.11:443: connect: connection refused\"\n"
                        + "      exit 1\n"
                        + "    '''\n"
                        + "  }\n"
                        + "}",
                true));

        WorkflowRun build = jenkins.assertBuildStatus(hudson.model.Result.FAILURE, job.scheduleBuild2(0));

        SmartRetryRunAction action = build.getAction(SmartRetryRunAction.class);
        Assertions.assertNotNull(action);

        jenkins.assertLogContains("classified=NETWORK_TRANSIENT", build);
        jenkins.assertLogContains("decision=RETRY", build);
        Assertions.assertEquals(
                "network-connection-refused", action.getAttempts().get(0).getMatchedRule());
    }

    @Test
    void classifiesConnectionResetFromConsoleContextAsNetworkTransient(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "smart-retry-connection-reset");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  smartRetry(profile: 'infra', maxRetries: 1, backoff: 'fixed', initialDelaySeconds: 0) {\n"
                        + "    sh '''\n"
                        + "      >&2 echo \"docker pull jfrog.example/docker/ci_tool:latest\"\n"
                        + "      >&2 echo \"Error response from daemon: Get \\\"https://jfrog.example/v2/\\\": read tcp 10.0.0.1:41234->100.13.11.11:443: read: connection reset by peer\"\n"
                        + "      exit 1\n"
                        + "    '''\n"
                        + "  }\n"
                        + "}",
                true));

        WorkflowRun build = jenkins.assertBuildStatus(hudson.model.Result.FAILURE, job.scheduleBuild2(0));

        SmartRetryRunAction action = build.getAction(SmartRetryRunAction.class);
        Assertions.assertNotNull(action);

        jenkins.assertLogContains("classified=NETWORK_TRANSIENT", build);
        jenkins.assertLogContains("decision=RETRY", build);
        Assertions.assertEquals(
                "network-connection-reset", action.getAttempts().get(0).getMatchedRule());
    }
}
