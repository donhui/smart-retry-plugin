package io.jenkins.plugins.smart_retry.classify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.jenkins.plugins.smart_retry.model.FailureClassification;
import io.jenkins.plugins.smart_retry.model.FailureType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicFailureClassifierCorpusTest {

    private final FailureClassifier classifier = new DeterministicFailureClassifier();

    @Test
    void classifiesCuratedPositiveCorpusSamples() throws IOException {
        List<CorpusCase> cases = List.of(
                new CorpusCase(
                        "samples/kubernetes-agent-evicted.log",
                        "Container step failed while running on Kubernetes agent",
                        FailureType.AGENT_LOST,
                        "agent-kubernetes-evicted",
                        true),
                new CorpusCase(
                        "samples/git-clone-sideband-interruption.log",
                        "git clone failed",
                        FailureType.SCM_TRANSIENT,
                        "scm-transport-interrupted",
                        true),
                new CorpusCase(
                        "samples/maven-artifact-partial-download.log",
                        "Failed to execute goal on project plugin-platform",
                        FailureType.ARTIFACT_REPO_TRANSIENT,
                        "artifact-partial-download",
                        true),
                new CorpusCase(
                        "samples/docker-registry-connection-reset.log",
                        "docker pull failed",
                        FailureType.NETWORK_TRANSIENT,
                        "network-connection-reset",
                        true),
                new CorpusCase(
                        "samples/ldap-reauthentication.log",
                        "request url http://ldap.example/api, response code 401",
                        FailureType.IDENTITY_PROVIDER_TRANSIENT,
                        "identity-provider-ldap-reauthentication-failed",
                        true),
                new CorpusCase(
                        "samples/gradle-compile-kotlin.log",
                        "Execution failed for task ':compileKotlin'.",
                        FailureType.COMPILATION_FAILURE,
                        "gradle-compile-task-failed",
                        false),
                new CorpusCase(
                        "samples/pytest-failure-summary.log",
                        "pytest reported failures",
                        FailureType.TEST_ASSERTION_FAILURE,
                        "test-pytest-failures-summary",
                        false));

        for (CorpusCase corpusCase : cases) {
            FailureClassification classification = classifier.classify(
                    new Exception(corpusCase.exceptionMessage()), loadSample(corpusCase.resourcePath()));

            assertEquals(corpusCase.expectedType(), classification.getType(), corpusCase.resourcePath());
            assertEquals(corpusCase.expectedRule(), classification.getMatchedRule(), corpusCase.resourcePath());
            assertEquals(corpusCase.retryCandidate(), classification.isRetryCandidate(), corpusCase.resourcePath());
        }
    }

    @Test
    void keepsCuratedNegativeCorpusSamplesConservative() throws IOException {
        List<CorpusCase> cases = List.of(
                new CorpusCase("samples/ambiguous-status-code.log", "", FailureType.UNKNOWN, null, false),
                new CorpusCase(
                        "samples/generic-broken-pipe.log", "wrapper step failed", FailureType.UNKNOWN, null, false),
                new CorpusCase(
                        "samples/gradle-toolchain-provisioning.log",
                        "FAILURE: Build failed with an exception.",
                        FailureType.UNKNOWN,
                        null,
                        false));

        for (CorpusCase corpusCase : cases) {
            FailureClassification classification = classifier.classify(
                    new Exception(corpusCase.exceptionMessage()), loadSample(corpusCase.resourcePath()));

            assertEquals(corpusCase.expectedType(), classification.getType(), corpusCase.resourcePath());
            assertEquals(corpusCase.expectedRule(), classification.getMatchedRule(), corpusCase.resourcePath());
            assertFalse(classification.isRetryCandidate(), corpusCase.resourcePath());
            assertNotNull(classification.getSummary(), corpusCase.resourcePath());
        }
    }

    private static String loadSample(String resourcePath) throws IOException {
        try (InputStream stream = DeterministicFailureClassifierCorpusTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(stream, resourcePath);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record CorpusCase(
            String resourcePath,
            String exceptionMessage,
            FailureType expectedType,
            String expectedRule,
            boolean retryCandidate) {}
}
