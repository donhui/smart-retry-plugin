package io.jenkins.plugins.smart_retry.classify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.smart_retry.config.CustomClassificationRule;
import io.jenkins.plugins.smart_retry.model.FailureClassification;
import io.jenkins.plugins.smart_retry.model.FailureType;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.junit.jupiter.api.Test;

class DeterministicFailureClassifierTest {

    private final FailureClassifier classifier = new DeterministicFailureClassifier();

    @Test
    void defaultsToUnknownNonRetryable() {
        FailureClassification c = classifier.classify(new RuntimeException("some deterministic error"), null);
        assertEquals(FailureType.UNKNOWN, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("No deterministic rule matched", c.getSummary());
    }

    @Test
    void classifiesSocketTimeoutAsNetworkTransient() {
        FailureClassification c = classifier.classify(new SocketTimeoutException("Read timed out"), null);
        assertEquals(FailureType.NETWORK_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("socket-timeout", c.getMatchedRule());
    }

    @Test
    void classifiesFlowInterruptedAsUserAbort() {
        FailureClassification c = classifier.classify(new FlowInterruptedException(null), null);
        assertEquals(FailureType.USER_ABORT, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("flow-interrupted", c.getMatchedRule());
    }

    @Test
    void classifiesMessagePatternScmTransient() {
        FailureClassification c = classifier.classify(new Exception("remote end hung up unexpectedly"), null);
        assertEquals(FailureType.SCM_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertNotNull(c.getMatchedRule());
    }

    @Test
    void classifiesMessagePatternAgentLost() {
        FailureClassification c = classifier.classify(new Exception("Agent was removed"), null);
        assertEquals(FailureType.AGENT_LOST, c.getType());
        assertTrue(c.isRetryCandidate());
    }

    @Test
    void classifiesChannelClosedAgentFailureAsAgentLost() {
        FailureClassification c = classifier.classify(
                new Exception("Cannot contact cloud-ubuntu587aa2: hudson.remoting.ChannelClosedException: Channel "
                        + "\"hudson.remoting.Channel@38da1aa3:cloud-ubuntu587aa2\": Remote call on "
                        + "cloud-ubuntu587aa2 failed. The channel is closing down or has closed down"),
                null);
        assertEquals(FailureType.AGENT_LOST, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("agent-remoting-channel-closed", c.getMatchedRule());
    }

    @Test
    void classifiesAgentOfflineEofAsAgentLostInsteadOfGenericNetworkTransient() {
        FailureClassification c = classifier.classify(
                new RuntimeException(
                        "cloud-ubuntu587aa2 was marked offline: Connection was broken: java.io.EOFException",
                        new java.io.EOFException("channel closed unexpectedly")),
                "ERROR: Issue with creating launcher for agent cloud-ubuntu587aa2. "
                        + "The agent has not been fully initialized yet");
        assertEquals(FailureType.AGENT_LOST, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("agent-remoting-channel-closed", c.getMatchedRule());
    }

    @Test
    void classifiesKubernetesPodDeletedAsAgentLost() {
        FailureClassification c = classifier.classify(
                new Exception("Pod jenkins-agent-7x9k2 was deleted while the step was still running"), null);
        assertEquals(FailureType.AGENT_LOST, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("agent-kubernetes-pod-not-found", c.getMatchedRule());
    }

    @Test
    void classifiesKubernetesPodNotFoundAsAgentLost() {
        FailureClassification c = classifier.classify(
                new Exception(
                        "io.fabric8.kubernetes.client.KubernetesClientException: pods \"jenkins-agent-7x9k2\" not found"),
                null);
        assertEquals(FailureType.AGENT_LOST, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("agent-kubernetes-pod-not-found", c.getMatchedRule());
    }

    @Test
    void classifiesKubernetesEvictionAsAgentLost() {
        FailureClassification c = classifier.classify(
                new Exception("Pod jenkins-agent-7x9k2 was Evicted. The node was low on resource: ephemeral-storage."),
                null);
        assertEquals(FailureType.AGENT_LOST, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("agent-kubernetes-evicted", c.getMatchedRule());
    }

    @Test
    void disablingScmRemoteEndHungUpFallsBackToUnknown() {
        FailureClassifier classifierWithDisabledRule =
                new DeterministicFailureClassifier(Set.of("scm-remote-end-hung-up"));

        FailureClassification c =
                classifierWithDisabledRule.classify(new Exception("remote end hung up unexpectedly"), null);

        assertEquals(FailureType.UNKNOWN, c.getType());
        assertFalse(c.isRetryCandidate());
        assertNull(c.getMatchedRule());
    }

    @Test
    void disablingArtifactConnectionResetFallsThroughToGenericNetworkRule() {
        FailureClassifier classifierWithDisabledRule =
                new DeterministicFailureClassifier(Set.of("artifact-connection-reset"));

        FailureClassification c = classifierWithDisabledRule.classify(
                new Exception("Failed to download dependency"),
                "Could not transfer artifact org.bouncycastle:bcprov-jdk18on:jar:1.78.1 from/to azure-internal "
                        + "(http://artifact-caching-proxy.privatelink.azurecr.io:8080/): Connection reset");

        assertEquals(FailureType.NETWORK_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("network-connection-reset", c.getMatchedRule());
    }

    @Test
    void unsupportedDisabledRuleIdsAreIgnored() {
        FailureClassifier classifierWithUnsupportedDisabledRules =
                new DeterministicFailureClassifier(Set.of("flow-interrupted", "not-a-rule"));

        FailureClassification c =
                classifierWithUnsupportedDisabledRules.classify(new Exception("remote end hung up unexpectedly"), null);

        assertEquals(FailureType.SCM_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("scm-remote-end-hung-up", c.getMatchedRule());
    }

    @Test
    void customClassificationRulesTakePriorityOverBuiltInMessageRules() {
        CustomClassificationRule rule = new CustomClassificationRule();
        rule.setName("custom-network-reset");
        rule.setPattern("connection reset");
        rule.setFailureType(FailureType.NETWORK_TRANSIENT);
        rule.setDescription("Custom network reset");

        FailureClassifier classifierWithCustomRule = new DeterministicFailureClassifier(Set.of(), List.of(rule));

        FailureClassification c = classifierWithCustomRule.classify(
                new Exception("remote end hung up unexpectedly"),
                "Could not transfer artifact from/to repo: Connection reset");

        assertEquals(FailureType.NETWORK_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("custom-rule-custom-network-reset", c.getMatchedRule());
    }

    @Test
    void classifiesCompilationFailureAsNonRetryable() {
        FailureClassification c = classifier.classify(
                new Exception(
                        "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin\nCompilation failure"),
                null);
        assertEquals(FailureType.COMPILATION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("compilation-failure", c.getMatchedRule());
    }

    @Test
    void classifiesCannotFindSymbolAsCompilationFailure() {
        FailureClassification c =
                classifier.classify(new Exception("src/main/java/App.java:[10,5] error: cannot find symbol"), null);
        assertEquals(FailureType.COMPILATION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("cannot-find-symbol", c.getMatchedRule());
    }

    @Test
    void classifiesTypeScriptCompilerErrorAsCompilationFailure() {
        FailureClassification c = classifier.classify(
                new Exception("npm run build failed"),
                "src/index.ts(14,7): error TS2322: Type 'string' is not assignable to type 'number'.");
        assertEquals(FailureType.COMPILATION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("typescript-compiler-error", c.getMatchedRule());
    }

    @Test
    void classifiesGoCompilerErrorAsCompilationFailure() {
        FailureClassification c =
                classifier.classify(new Exception("go build ./... failed"), "./main.go:12:2: undefined: fmtx");
        assertEquals(FailureType.COMPILATION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("go-compiler-error", c.getMatchedRule());
    }

    @Test
    void classifiesCppCompilerErrorAsCompilationFailure() {
        FailureClassification c = classifier.classify(
                new Exception("make failed"), "src/main.cpp:8:10: fatal error: missing.hpp: No such file or directory");
        assertEquals(FailureType.COMPILATION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("c-family-compiler-error", c.getMatchedRule());
    }

    @Test
    void classifiesCppLinkerErrorAsCompilationFailure() {
        FailureClassification c = classifier.classify(
                new Exception("make failed"),
                "/usr/bin/ld: undefined reference to `doWork()'\ncollect2: error: ld returned 1 exit status");
        assertEquals(FailureType.COMPILATION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("c-family-linker-error", c.getMatchedRule());
    }

    @Test
    void classifiesGradleCompileJavaFailureAsCompilationFailure() {
        FailureClassification c = classifier.classify(
                new Exception("Execution failed for task ':compileJava'."),
                "> Compilation failed; see the compiler error output for details.");
        assertEquals(FailureType.COMPILATION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("gradle-compile-task-failed", c.getMatchedRule());
    }

    @Test
    void classifiesGradleCompileKotlinFailureAsCompilationFailure() {
        FailureClassification c = classifier.classify(
                new Exception("Execution failed for task ':compileKotlin'."),
                "> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction\n"
                        + "> Compilation error. See log for more details");
        assertEquals(FailureType.COMPILATION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("gradle-compile-task-failed", c.getMatchedRule());
    }

    @Test
    void classifiesJavaCompilerDiagnosticAsCompilationFailure() {
        FailureClassification c = classifier.classify(
                new Exception("Execution failed for task ':compileJava'."),
                "src/main/java/App.java:12: error: package com.example.missing does not exist");
        assertEquals(FailureType.COMPILATION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("java-compiler-error", c.getMatchedRule());
    }

    @Test
    void classifiesKotlinCompilerDiagnosticAsCompilationFailure() {
        FailureClassification c = classifier.classify(
                new Exception("Execution failed for task ':compileKotlin'."),
                "e: /workspace/src/main/kotlin/App.kt: (14, 5): Unresolved reference: foo");
        assertEquals(FailureType.COMPILATION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("kotlin-compiler-error", c.getMatchedRule());
    }

    @Test
    void doesNotClassifyGradleToolchainProvisioningFailureAsCompilationFailure() {
        FailureClassification c = classifier.classify(
                new Exception("FAILURE: Build failed with an exception."),
                "* What went wrong:\n"
                        + "A problem occurred configuring root project 'opentelemetry-java-instrumentation'.\n"
                        + "> Could not determine the dependencies of task ':gradle-plugins:compileKotlin'.\n"
                        + "> Cannot find a Java installation on your machine matching: {languageVersion=17}. "
                        + "Some toolchain resolvers had provisioning failures: foojay "
                        + "(Unable to download toolchain matching the requirements from "
                        + "'https://api.foojay.io/disco/v3.0/ids/example/redirect', due to: "
                        + "Could not get resource 'https://api.foojay.io/disco/v3.0/ids/example/redirect'.).");
        assertEquals(FailureType.UNKNOWN, c.getType());
        assertFalse(c.isRetryCandidate());
    }

    @Test
    void doesNotClassifyBareNpmWrapperFailureAsCompilationFailure() {
        FailureClassification c =
                classifier.classify(new Exception("npm ERR! code ELIFECYCLE\nnpm ERR! errno 2"), null);
        assertEquals(FailureType.UNKNOWN, c.getType());
        assertFalse(c.isRetryCandidate());
    }

    @Test
    void classifiesMissingDslMethodAsPipelineLogicFailure() {
        FailureClassification c = classifier.classify(
                new Exception("WorkflowScript: 12: No such DSL method 'mvnn' found among steps"), null);
        assertEquals(FailureType.PIPELINE_LOGIC_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("pipeline-no-such-dsl-method", c.getMatchedRule());
    }

    @Test
    void classifiesScriptSecurityRejectionAsPipelineLogicFailure() {
        FailureClassification c = classifier.classify(
                new Exception(
                        "org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use method java.io.File delete"),
                null);
        assertEquals(FailureType.PIPELINE_LOGIC_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("pipeline-script-security-rejected", c.getMatchedRule());
    }

    @Test
    void prioritizesPipelineLogicFailureOverRetryableScmSignals() {
        FailureClassification c = classifier.classify(
                new Exception(
                        "WorkflowScript: 3: No such DSL method 'gti' found among steps\nremote end hung up unexpectedly"),
                null);
        assertEquals(FailureType.PIPELINE_LOGIC_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("pipeline-no-such-dsl-method", c.getMatchedRule());
    }

    @Test
    void classifiesOpenTest4jAssertionFailureAsTestAssertionFailure() {
        FailureClassification c = classifier.classify(
                new Exception("org.opentest4j.AssertionFailedError: expected: <200> but was: <503>"), null);
        assertEquals(FailureType.TEST_ASSERTION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("test-opentest4j-assertion-failed", c.getMatchedRule());
    }

    @Test
    void classifiesSurefireFailuresSummaryAsTestAssertionFailure() {
        FailureClassification c = classifier.classify(
                new Exception("There are test failures."), "[ERROR] Tests run: 42, Failures: 1, Errors: 0, Skipped: 0");
        assertEquals(FailureType.TEST_ASSERTION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("test-runner-failures-summary", c.getMatchedRule());
    }

    @Test
    void classifiesSurefireErrorsSummaryAsTestAssertionFailure() {
        FailureClassification c = classifier.classify(
                new Exception("Tests failed during Maven Surefire execution"),
                "[ERROR] Tests run: 564, Failures: 0, Errors: 17, Skipped: 0");
        assertEquals(FailureType.TEST_ASSERTION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("test-runner-failures-summary", c.getMatchedRule());
    }

    @Test
    void classifiesPytestTerminalSummaryAsTestAssertionFailure() {
        FailureClassification c = classifier.classify(
                new Exception("pytest reported failures"),
                "===== 1 failed, 441 passed, 17 skipped, 32 warnings in 1196.37s =====");
        assertEquals(FailureType.TEST_ASSERTION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("test-pytest-failures-summary", c.getMatchedRule());
    }

    @Test
    void classifiesPytestShortSummaryInfoAsTestAssertionFailure() {
        FailureClassification c = classifier.classify(
                new Exception("pytest short summary reported failures"),
                "=========================== short test summary info ============================\n"
                        + "FAILED tests/test_stage_list.py::test_stage_list_length - AssertionError: local stage_list length mismatch");
        assertEquals(FailureType.TEST_ASSERTION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("test-pytest-failures-summary", c.getMatchedRule());
    }

    @Test
    void classifiesGradleTestSummaryAsTestAssertionFailure() {
        FailureClassification c = classifier.classify(
                new Exception("Execution failed for task ':test'."),
                "4 tests completed, 1 failed\nThere were failing tests. See the report at: file:///workspace/build/reports/tests/test/index.html");
        assertEquals(FailureType.TEST_ASSERTION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("test-gradle-failures-summary", c.getMatchedRule());
    }

    @Test
    void classifiesMessageContextWhenExceptionMessageIsEmpty() {
        FailureClassification c = classifier.classify(new Exception(""), "503 Service Unavailable");
        assertEquals(FailureType.NETWORK_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("network-http-5xx", c.getMatchedRule());
    }

    @Test
    void classifiesPartialArtifactDownloadAsArtifactRepoTransient() {
        FailureClassification c = classifier.classify(
                new Exception("Failed to execute goal on project plugin-platform"),
                "Could not transfer artifact commons-collections:commons-collections:jar:3.2.2 from/to artifactory\n"
                        + "/root/.m2/repository/commons-collections/commons-collections/3.2.2/commons-collections-3.2.2.jar.part"
                        + " (No such file or directory)");
        assertEquals(FailureType.ARTIFACT_REPO_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("artifact-partial-download", c.getMatchedRule());
    }

    @Test
    void classifiesArtifactRepository5xxAsArtifactRepoTransient() {
        FailureClassification c = classifier.classify(
                new Exception("Failed to download"),
                "Could not transfer artifact commons-collections:commons-collections:jar:3.2.2 from/to artifactory\n"
                        + "503 Service Unavailable");
        assertEquals(FailureType.ARTIFACT_REPO_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("artifact-http-5xx", c.getMatchedRule());
    }

    @Test
    void classifiesArtifactRepositoryConnectionResetAsArtifactRepoTransient() {
        FailureClassification c = classifier.classify(
                new Exception("Failed to download dependency"),
                "Could not transfer artifact org.bouncycastle:bcprov-jdk18on:jar:1.78.1 from/to azure-internal "
                        + "(http://artifact-caching-proxy.privatelink.azurecr.io:8080/): Connection reset");
        assertEquals(FailureType.ARTIFACT_REPO_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("artifact-connection-reset", c.getMatchedRule());
    }

    @Test
    void classifiesArtifactRepositoryConnectionRefusedAsArtifactRepoTransient() {
        FailureClassification c = classifier.classify(
                new Exception("Failed to resolve dependency"),
                "Downloading from artifactory: https://repo.example/artifactory/maven-public\n"
                        + "connect: connection refused");
        assertEquals(FailureType.ARTIFACT_REPO_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("artifact-connection-refused", c.getMatchedRule());
    }

    @Test
    void classifiesGenericTlsHandshakeTimeoutAsNetworkTransient() {
        FailureClassification c =
                classifier.classify(new Exception("tls handshake timeout while calling upstream service"), null);
        assertEquals(FailureType.NETWORK_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("network-tls-handshake-timeout", c.getMatchedRule());
    }

    @Test
    void classifiesExternalServiceConnectionRefusedAsNetworkTransient() {
        FailureClassification c = classifier.classify(
                new Exception("docker pull failed"),
                "Error response from daemon: Get \"https://jfrog.example/v2/\": dial tcp 100.13.11.11:443: connect: connection refused");
        assertEquals(FailureType.NETWORK_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("network-connection-refused", c.getMatchedRule());
    }

    @Test
    void classifiesExternalServiceConnectionResetAsNetworkTransient() {
        FailureClassification c = classifier.classify(
                new Exception("docker pull failed"),
                "Error response from daemon: Get \"https://jfrog.example/v2/\": read tcp 10.0.0.1:41234->100.13.11.11:443: read: connection reset by peer");
        assertEquals(FailureType.NETWORK_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("network-connection-reset", c.getMatchedRule());
    }

    @Test
    void prioritizesArtifactRepositoryConnectionResetOverGenericNetworkReset() {
        FailureClassification c = classifier.classify(
                new Exception("download failed"),
                "Could not transfer artifact commons-collections:commons-collections:jar:3.2.2 from/to artifactory\n"
                        + "https://repo.example/artifactory/maven-public\n"
                        + "read: connection reset by peer");
        assertEquals(FailureType.ARTIFACT_REPO_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("artifact-connection-reset", c.getMatchedRule());
    }

    @Test
    void classifiesArtifactRepositoryTlsHandshakeTimeoutAsArtifactRepoTransient() {
        FailureClassification c = classifier.classify(
                new Exception("Failed to resolve dependency"),
                "Downloading from artifactory: https://repo.example/artifactory/maven-public\n"
                        + "tls handshake timeout");
        assertEquals(FailureType.ARTIFACT_REPO_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("artifact-tls-handshake-timeout", c.getMatchedRule());
    }

    @Test
    void classifiesGenericCouldNotResolveHostAsNetworkTransient() {
        FailureClassification c = classifier.classify(new Exception("Could not resolve host: example.invalid"), null);
        assertEquals(FailureType.NETWORK_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("network-could-not-resolve-host", c.getMatchedRule());
    }

    @Test
    void classifiesScmContextCouldNotResolveHostAsScmTransient() {
        FailureClassification c = classifier.classify(
                new Exception("fatal: unable to access repository"),
                "git fetch origin main\nfatal: Could not resolve host: github.example");
        assertEquals(FailureType.SCM_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("scm-could-not-resolve-host", c.getMatchedRule());
    }

    @Test
    void classifiesScmTransportInterruptionsAsScmTransient() {
        FailureClassification c = classifier.classify(
                new Exception("git clone failed"),
                "git clone --depth 10 --recurse-submodules http://gitlab.example/repo.git\n"
                        + "error: RPC failed; curl 56 Problem (3) in the Chunked-Encoded data\n"
                        + "fetch-pack: unexpected disconnect while reading sideband packet\n"
                        + "fatal: early EOF\n"
                        + "fatal: index-pack failed");
        assertEquals(FailureType.SCM_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("scm-transport-interrupted", c.getMatchedRule());
    }

    @Test
    void classifiesScmHttp5xxAsScmTransient() {
        FailureClassification c = classifier.classify(
                new Exception("git fetch failed"),
                "using GIT_ASKPASS to set credentials\n"
                        + "/usr/bin/git fetch --tags --progress http://gitlab.example/repo.git "
                        + "+refs/heads/*:refs/remotes/origin/*\n"
                        + "stderr: HTTP code = 504");
        assertEquals(FailureType.SCM_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("scm-http-5xx", c.getMatchedRule());
    }

    @Test
    void classifiesRemoteBranchNotFoundAsScmConfigurationFailure() {
        FailureClassification c = classifier.classify(
                new Exception("git clone failed"),
                "git clone --depth 10 --recurse-submodules https://gitlab.example/demo.git -b develop/0.1.0 .\n"
                        + "fatal: Remote branch develop/0.1.0 not found in upstream origin");
        assertEquals(FailureType.SCM_CONFIGURATION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("scm-remote-branch-not-found", c.getMatchedRule());
    }

    @Test
    void classifiesMissingRevisionAsScmConfigurationFailure() {
        FailureClassification c = classifier.classify(
                new Exception("checkout failed"),
                "ERROR: Couldn't find any revision to build. Verify the repository and branch configuration for this job.");
        assertEquals(FailureType.SCM_CONFIGURATION_FAILURE, c.getType());
        assertFalse(c.isRetryCandidate());
        assertEquals("scm-revision-not-found", c.getMatchedRule());
    }

    @Test
    void classifiesLdapReauthenticationFailureAsIdentityProviderTransient() {
        FailureClassification c = classifier.classify(
                new Exception("request url http://ldap.example/api, response code 401"),
                "Can't reauthenticate LDAP for user: 'xxx': user is locked, disabled or does not exist in LDAP");
        assertEquals(FailureType.IDENTITY_PROVIDER_TRANSIENT, c.getType());
        assertTrue(c.isRetryCandidate());
        assertEquals("identity-provider-ldap-reauthentication-failed", c.getMatchedRule());
    }

    @Test
    void doesNotClassifyGeneric401AsIdentityProviderTransient() {
        FailureClassification c =
                classifier.classify(new Exception("[Error] server response: 401"), "\"message\": \"access denied\"");
        assertEquals(FailureType.UNKNOWN, c.getType());
        assertFalse(c.isRetryCandidate());
    }

    @Test
    void doesNotClassifyBareStatusCodeAsNetworkTransient() {
        FailureClassification c =
                classifier.classify(new Exception("assertion failed: expected 503 but was 200"), null);
        assertEquals(FailureType.UNKNOWN, c.getType());
        assertFalse(c.isRetryCandidate());
    }

    @Test
    void doesNotClassifyGenericAssertionWordingWithoutTestSignals() {
        FailureClassification c = classifier.classify(new Exception("assertion failed while validating input"), null);
        assertEquals(FailureType.UNKNOWN, c.getType());
        assertFalse(c.isRetryCandidate());
    }

    @Test
    void doesNotClassifyGenericBrokenPipeMessageAsNetworkTransient() {
        FailureClassification c = classifier.classify(new Exception("write failed: broken pipe"), null);
        assertEquals(FailureType.UNKNOWN, c.getType());
        assertFalse(c.isRetryCandidate());
    }

    @Test
    void doesNotClassifyGenericConnectionRefusedWithoutServiceContextAsNetworkTransient() {
        FailureClassification c =
                classifier.classify(new Exception("dependency check failed: connection refused"), null);
        assertEquals(FailureType.UNKNOWN, c.getType());
        assertFalse(c.isRetryCandidate());
    }

    @Test
    void doesNotClassifyGenericConnectionResetWithoutServiceContextAsNetworkTransient() {
        FailureClassification c =
                classifier.classify(new Exception("dependency check failed: connection reset by peer"), null);
        assertEquals(FailureType.UNKNOWN, c.getType());
        assertFalse(c.isRetryCandidate());
    }
}
