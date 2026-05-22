package io.jenkins.plugins.smart_retry.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.smart_retry.action.SmartRetryRunAction;
import io.jenkins.plugins.smart_retry.policy.BuiltInProfiles;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class SmartRetryResumeTest {

    private static final String BACKOFF_FIXED = BuiltInProfiles.BACKOFF_FIXED;

    @RegisterExtension
    static JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @Test
    void resumesScheduledRetryAfterJenkinsRestart() throws Throwable {
        String jobName = "smart-retry-resume-after-restart";
        int[] buildNumber = new int[1];

        sessions.then(j -> {
            WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, jobName);
            job.setDefinition(new CpsFlowDefinition(
                    "node {\n"
                            + "  smartRetry(maxRetries: 1, backoff: '"
                            + BACKOFF_FIXED
                            + "', initialDelaySeconds: 10) {\n"
                            + "    if (fileExists('sentinel')) {\n"
                            + "      echo 'second attempt after restart'\n"
                            + "    } else {\n"
                            + "      writeFile file: 'sentinel', text: 'x'\n"
                            + "      error('remote end hung up unexpectedly')\n"
                            + "    }\n"
                            + "  }\n"
                            + "}",
                    true));

            WorkflowRun build = job.scheduleBuild2(0).waitForStart();
            assertNotNull(build);
            buildNumber[0] = build.getNumber();

            j.waitForMessage("decision=RETRY", build);
            assertTrue(build.isBuilding());
            j.assertLogContains("[smartRetry] attempt=1", build);
            j.assertLogNotContains("[smartRetry] attempt=2", build);
        });

        sessions.then(j -> {
            WorkflowJob job = j.jenkins.getItemByFullName(jobName, WorkflowJob.class);
            assertNotNull(job);

            WorkflowRun build = job.getBuildByNumber(buildNumber[0]);
            assertNotNull(build);

            j.waitForMessage("[smartRetry] begin attempt=2", build);
            j.assertBuildStatusSuccess(j.waitForCompletion(build));
            j.assertLogContains("second attempt after restart", build);
            j.assertLogContains("Resuming build", build);

            SmartRetryRunAction action = build.getAction(SmartRetryRunAction.class);
            assertNotNull(action);
            assertEquals("SUCCESS", action.getFinalOutcome());
            assertEquals(1, action.getAttempts().size());
            assertEquals("RETRY_SCHEDULED", action.getAttempts().get(0).getOutcome());
        });
    }
}
