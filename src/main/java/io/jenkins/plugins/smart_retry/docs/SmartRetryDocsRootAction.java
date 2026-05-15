package io.jenkins.plugins.smart_retry.docs;

import hudson.Extension;
import hudson.model.RootAction;
import io.jenkins.plugins.smart_retry.classify.DeterministicFailureClassifier;
import io.jenkins.plugins.smart_retry.config.CustomProfileSettings;
import io.jenkins.plugins.smart_retry.model.FailureType;
import io.jenkins.plugins.smart_retry.policy.BuiltInProfiles;
import java.util.List;
import java.util.Set;

@Extension
public final class SmartRetryDocsRootAction implements RootAction {

    private static final Set<String> SUPPORTED_DISABLED_BUILT_IN_RULE_IDS =
            DeterministicFailureClassifier.supportedDisabledBuiltInRuleIds();

    private static final List<FailureTypeDoc> FAILURE_TYPES = List.of(
            failureType(
                    FailureType.AGENT_LOST,
                    "Agent, pod, node, or remoting channel disappeared during execution.",
                    "Current classifier emits this type for agent removal, remoting channel closure, offline/launcher initialization breakage, Kubernetes pod deleted/not-found signals, eviction/resource-pressure failures, and other channel-loss style signals.",
                    "This usually points to execution infrastructure disappearing underneath the build."),
            failureType(
                    FailureType.SCM_TRANSIENT,
                    "Checkout or fetch failed because of temporary SCM or network instability.",
                    "Current classifier emits this type for transient Git remote disconnects, Git transport interruptions, and SCM host-resolution failures.",
                    "These failures are usually external to the repository contents and are safe retry candidates."),
            failureType(
                    FailureType.NETWORK_TRANSIENT,
                    "An external service could not be reached reliably.",
                    "Current classifier emits this type for generic host resolution, explicit HTTP-style 5xx, TLS handshake timeout, refused connections to external services, connect, timeout, reset, and EOF style failures.",
                    "These failures often clear on a later attempt, but the conservative profile intentionally blocks them."),
            failureType(
                    FailureType.ARTIFACT_REPO_TRANSIENT,
                    "A dependency or artifact repository was temporarily unavailable.",
                    "Current classifier emits this type only when repository-specific context is present around transient 5xx, TLS handshake timeout, connection reset/refused, or partial Maven artifact download failures.",
                    "Repository-side outages are often short-lived, but only broader profiles will retry them."),
            failureType(
                    FailureType.IDENTITY_PROVIDER_TRANSIENT,
                    "An external identity provider failed during an authentication or reauthentication flow.",
                    "Current classifier emits this type only for narrow LDAP reauthentication failure signals rather than generic 401 responses.",
                    "Identity backend instability can clear on a later attempt, but Smart Retry keeps this out of the conservative profile."),
            failureType(
                    FailureType.USER_ABORT,
                    "The build was intentionally interrupted by a user or explicit interruption path.",
                    "Current classifier emits this type for FlowInterruptedException paths.",
                    "Intentional interrupts should stop immediately and must not be retried."),
            failureType(
                    FailureType.PIPELINE_LOGIC_FAILURE,
                    "The Jenkinsfile or Pipeline script logic is broken.",
                    "Current classifier emits this type for missing DSL methods, missing properties, script-security rejections, and WorkflowScript/Jenkinsfile Groovy startup failures.",
                    "Retrying broken pipeline logic would repeat the same user-authored error."),
            failureType(
                    FailureType.COMPILATION_FAILURE,
                    "Source compilation or static checking clearly failed.",
                    "Current classifier emits this type for explicit compilation-failure wording, cannot-find-symbol style compiler output, TypeScript compiler diagnostics, Go file:line:column compiler diagnostics, and high-confidence C/C++ compiler or linker failures.",
                    "This points to code defects rather than transient infrastructure conditions."),
            failureType(
                    FailureType.TEST_ASSERTION_FAILURE,
                    "Tests failed because assertions did not match expectations.",
                    "Current classifier emits this type for explicit test assertion exception types and high-confidence test-runner failure summaries.",
                    "The MVP keeps test assertion failures non-retryable to avoid hiding product regressions."),
            failureType(
                    FailureType.DEPLOYMENT_FAILURE,
                    "A release or deployment step failed and may have partial side effects.",
                    "Reserved in the taxonomy; the current classifier does not emit this type yet.",
                    "Automatic retries here risk duplicate or partial deployments, so MVP keeps them off."),
            failureType(
                    FailureType.UNKNOWN,
                    "No deterministic rule matched with enough confidence.",
                    "Current classifier emits this fallback whenever no exception or message rule matches.",
                    "UNKNOWN stays non-retryable so Smart Retry never guesses its way into risky reruns."));

    private static final List<MatchedRuleDoc> MATCHED_RULES = List.of(
            matchedRule(
                    "pipeline-no-such-dsl-method",
                    FailureType.PIPELINE_LOGIC_FAILURE,
                    "Message pattern",
                    "No such DSL method",
                    "Never retry",
                    "This indicates the Jenkinsfile referenced a step or DSL method that Jenkins does not know how to run."),
            matchedRule(
                    "pipeline-no-such-property",
                    FailureType.PIPELINE_LOGIC_FAILURE,
                    "Message pattern",
                    "No such property",
                    "Never retry",
                    "This indicates the Pipeline script referenced a missing variable or property rather than hitting a transient infrastructure issue."),
            matchedRule(
                    "pipeline-script-security-rejected",
                    FailureType.PIPELINE_LOGIC_FAILURE,
                    "Message pattern",
                    "RejectedAccessException | Scripts not permitted to use | script approval",
                    "Never retry",
                    "Script-security rejections are deterministic Jenkins policy failures and should fail fast."),
            matchedRule(
                    "pipeline-groovy-startup-failed",
                    FailureType.PIPELINE_LOGIC_FAILURE,
                    "Message pattern",
                    "WorkflowScript/Jenkinsfile plus startup failed | MissingPropertyException | MissingMethodException | MultipleCompilationErrorsException",
                    "Never retry",
                    "This indicates the Pipeline Groovy script failed during parsing or evaluation rather than during an external transient operation."),
            matchedRule(
                    "compilation-failure",
                    FailureType.COMPILATION_FAILURE,
                    "Message pattern",
                    "Compilation failure | Compilation error",
                    "Never retry",
                    "Explicit compiler failure wording is treated as a deterministic code problem."),
            matchedRule(
                    "cannot-find-symbol",
                    FailureType.COMPILATION_FAILURE,
                    "Message pattern",
                    "cannot find symbol",
                    "Never retry",
                    "Missing symbols in compiler output indicate source or build-definition defects, not transient infrastructure."),
            matchedRule(
                    "typescript-compiler-error",
                    FailureType.COMPILATION_FAILURE,
                    "Message pattern",
                    "error TS<code>:",
                    "Never retry",
                    "TypeScript compiler diagnostic codes are treated as deterministic source or type-check failures rather than transient infrastructure issues."),
            matchedRule(
                    "go-compiler-error",
                    FailureType.COMPILATION_FAILURE,
                    "Message pattern",
                    "<file>.go:<line>:<column>: undefined: | cannot use | no required module provides package | imported and not used | syntax error:",
                    "Never retry",
                    "These are high-confidence Go compiler diagnostics that point to source, import, or module-definition defects."),
            matchedRule(
                    "c-family-compiler-error",
                    FailureType.COMPILATION_FAILURE,
                    "Message pattern",
                    "<file>.c/.cc/.cpp/.cxx/.h/.hpp:<line>[:<column>]: fatal error: | error:",
                    "Never retry",
                    "File-scoped C/C++ compiler diagnostics are treated as deterministic build failures rather than transient infrastructure conditions."),
            matchedRule(
                    "c-family-linker-error",
                    FailureType.COMPILATION_FAILURE,
                    "Message pattern",
                    "undefined reference to | collect2: error: ld returned N exit status | ld returned N exit status | clang: error: linker command failed",
                    "Never retry",
                    "Classic C/C++ linker failures indicate missing symbols or build-definition issues and should fail fast."),
            matchedRule(
                    "gradle-compile-task-failed",
                    FailureType.COMPILATION_FAILURE,
                    "Message pattern",
                    "Execution failed for task ':compileJava/:compileKotlin/...' with Compilation failed/error",
                    "Never retry",
                    "Gradle compile-task failures are only classified here when the log also contains an explicit compilation-failed signal, keeping generic task failures out of this rule."),
            matchedRule(
                    "java-compiler-error",
                    FailureType.COMPILATION_FAILURE,
                    "Message pattern",
                    "<file>.java:<line>[:<column>]: error:",
                    "Never retry",
                    "High-confidence javac diagnostics indicate deterministic source or dependency-definition defects rather than transient infrastructure issues."),
            matchedRule(
                    "kotlin-compiler-error",
                    FailureType.COMPILATION_FAILURE,
                    "Message pattern",
                    "e: <file>.kt: (line, column):",
                    "Never retry",
                    "High-confidence Kotlin compiler diagnostics indicate deterministic source or type-checking failures rather than transient infrastructure conditions."),
            matchedRule(
                    "test-opentest4j-assertion-failed",
                    FailureType.TEST_ASSERTION_FAILURE,
                    "Message pattern",
                    "org.opentest4j.AssertionFailedError",
                    "Never retry",
                    "This is the standard JUnit 5 assertion-failure signal and is treated as a deterministic test result."),
            matchedRule(
                    "test-junit-assertion-failed",
                    FailureType.TEST_ASSERTION_FAILURE,
                    "Message pattern",
                    "AssertionFailedError | ComparisonFailure",
                    "Never retry",
                    "Classic JUnit assertion mismatch types indicate product or test expectation problems rather than transient infrastructure."),
            matchedRule(
                    "test-runner-failures-summary",
                    FailureType.TEST_ASSERTION_FAILURE,
                    "Message pattern",
                    "There are/were test failures | FAILURES!!! | Tests run: N, Failures: M | Tests run: N, Errors: E",
                    "Never retry",
                    "High-confidence test runner summaries should fail fast instead of being hidden behind automatic retries."),
            matchedRule(
                    "test-pytest-failures-summary",
                    FailureType.TEST_ASSERTION_FAILURE,
                    "Message pattern",
                    "short test summary info with FAILED/ERROR | === N failed/error in Xs ===",
                    "Never retry",
                    "Pytest failure summaries are deterministic test-result signals and should fail fast instead of being retried."),
            matchedRule(
                    "test-gradle-failures-summary",
                    FailureType.TEST_ASSERTION_FAILURE,
                    "Message pattern",
                    "There were failing tests. See the report at: | N tests completed, M failed",
                    "Never retry",
                    "Gradle test-task summaries are deterministic test-result signals and should fail fast instead of being retried."),
            matchedRule(
                    "agent-kubernetes-pod-not-found",
                    FailureType.AGENT_LOST,
                    "Message pattern",
                    "Pod/pods <name> was deleted | not found | KubernetesClientException with pod not found",
                    "Retry candidate",
                    "This captures high-confidence Kubernetes agent disappearance signals where the backing pod is gone before the build step can finish."),
            matchedRule(
                    "agent-kubernetes-evicted",
                    FailureType.AGENT_LOST,
                    "Message pattern",
                    "Evicted | node was low on resource | ephemeral-storage | MemoryPressure | DiskPressure",
                    "Retry candidate",
                    "This captures Kubernetes eviction and resource-pressure failures where the agent was removed by the cluster rather than failing because of user code."),
            matchedRule(
                    "agent-remoting-channel-closed",
                    FailureType.AGENT_LOST,
                    "Exception/message context",
                    "ChannelClosedException | Cannot contact agent | was marked offline | connection was broken | agent not fully initialized",
                    "Retry candidate",
                    "This captures Jenkins remoting and launcher failures where the agent channel closes or never finishes coming online, which is usually an infrastructure loss rather than a user-code defect."),
            matchedRule(
                    "agent-removed",
                    FailureType.AGENT_LOST,
                    "Message pattern",
                    "agent was removed | node was offline | channel closed | channel is closing | pod was deleted | evicted",
                    "Retry candidate",
                    "The agent disappeared mid-run, which is a classic infrastructure-loss signal."),
            matchedRule(
                    "scm-remote-end-hung-up",
                    FailureType.SCM_TRANSIENT,
                    "Message pattern",
                    "remote end hung up unexpectedly",
                    "Retry candidate",
                    "The remote SCM side dropped the connection unexpectedly, so a rerun may succeed."),
            matchedRule(
                    "scm-could-not-resolve-host",
                    FailureType.SCM_TRANSIENT,
                    "Message pattern",
                    "SCM context such as git checkout/fetch/clone/ls-remote plus could not resolve host",
                    "Retry candidate",
                    "This only applies when the classifier sees clear SCM context around the host-resolution failure."),
            matchedRule(
                    "scm-transport-interrupted",
                    FailureType.SCM_TRANSIENT,
                    "Message pattern",
                    "SCM context such as git clone/fetch/checkout plus curl 56 | RPC failed | unexpected disconnect while reading sideband packet | early EOF | index-pack failed",
                    "Retry candidate",
                    "This only applies when the classifier sees clear Git transport context around clone or fetch interruption signals."),
            matchedRule(
                    "network-could-not-resolve-host",
                    FailureType.NETWORK_TRANSIENT,
                    "Message pattern",
                    "could not resolve host",
                    "Retry candidate",
                    "Generic host-resolution failures are treated as network problems unless SCM context is explicit."),
            matchedRule(
                    "network-timeout",
                    FailureType.NETWORK_TRANSIENT,
                    "Message pattern",
                    "read timed out | connect timed out | connection timed out",
                    "Retry candidate",
                    "Timeouts often indicate a temporary service or network stall rather than a code problem."),
            matchedRule(
                    "network-http-5xx",
                    FailureType.NETWORK_TRANSIENT,
                    "Message pattern",
                    "502 Bad Gateway | 503 Service Unavailable | 504 Gateway Timeout | received/status code 502/503/504 | HTTP 502/503/504",
                    "Retry candidate",
                    "Only explicit HTTP-style 5xx signals are treated as external service instability unless repository context is explicit."),
            matchedRule(
                    "network-tls-handshake-timeout",
                    FailureType.NETWORK_TRANSIENT,
                    "Message pattern",
                    "tls handshake timeout",
                    "Retry candidate",
                    "TLS handshake timeouts are treated as generic network instability unless repository context is explicit."),
            matchedRule(
                    "network-connection-refused",
                    FailureType.NETWORK_TRANSIENT,
                    "Message pattern",
                    "External-service context such as http/https URL, request url, dial tcp, docker pull, /v2/, or registry plus connection refused",
                    "Retry candidate",
                    "This only applies when the classifier sees clear external-service context around a refused connection rather than generic refused wording."),
            matchedRule(
                    "network-connection-reset",
                    FailureType.NETWORK_TRANSIENT,
                    "Message pattern",
                    "External-service context such as http/https URL, request url, dial tcp, docker pull, /v2/, or registry plus connection reset",
                    "Retry candidate",
                    "The connection dropped unexpectedly after work had already started, but generic reset wording alone is not enough without clear external-service context."),
            matchedRule(
                    "artifact-partial-download",
                    FailureType.ARTIFACT_REPO_TRANSIENT,
                    "Message pattern",
                    "Could not transfer artifact plus .jar.part plus No such file or directory",
                    "Retry candidate",
                    "This usually means Maven was in the middle of downloading from the artifact repository when the transfer broke."),
            matchedRule(
                    "artifact-http-5xx",
                    FailureType.ARTIFACT_REPO_TRANSIENT,
                    "Message pattern",
                    "Artifact repository context plus explicit HTTP-style 502/503/504 signal",
                    "Retry candidate",
                    "This only applies when the classifier sees repository-specific context around the transient 5xx response."),
            matchedRule(
                    "artifact-tls-handshake-timeout",
                    FailureType.ARTIFACT_REPO_TRANSIENT,
                    "Message pattern",
                    "Artifact repository context plus tls handshake timeout",
                    "Retry candidate",
                    "This only applies when the classifier sees repository-specific context around the TLS failure."),
            matchedRule(
                    "artifact-connection-refused",
                    FailureType.ARTIFACT_REPO_TRANSIENT,
                    "Message pattern",
                    "Artifact repository context plus connection refused",
                    "Retry candidate",
                    "This only applies when the classifier sees repository-specific context around the refused connection, so generic service failures do not get misclassified as repository outages."),
            matchedRule(
                    "artifact-connection-reset",
                    FailureType.ARTIFACT_REPO_TRANSIENT,
                    "Message pattern",
                    "Artifact repository context plus connection reset",
                    "Retry candidate",
                    "This only applies when the classifier sees repository-specific context around the dropped connection, which makes it a better artifact-repository match than the generic network bucket."),
            matchedRule(
                    "identity-provider-ldap-reauthentication-failed",
                    FailureType.IDENTITY_PROVIDER_TRANSIENT,
                    "Message pattern",
                    "LDAP reauthentication wording plus HTTP-style 401 signal",
                    "Retry candidate",
                    "This only applies to narrow LDAP identity-provider reauthentication failures and does not treat generic 401 responses as transient."),
            matchedRule(
                    "flow-interrupted",
                    FailureType.USER_ABORT,
                    "Exception type",
                    "FlowInterruptedException",
                    "Never retry",
                    "This is treated as an intentional stop signal, not a flaky infrastructure event."),
            matchedRule(
                    "socket-timeout",
                    FailureType.NETWORK_TRANSIENT,
                    "Exception type",
                    "SocketTimeoutException",
                    "Retry candidate",
                    "Socket timeouts are mapped directly to transient network instability."),
            matchedRule(
                    "connect-exception",
                    FailureType.NETWORK_TRANSIENT,
                    "Exception type",
                    "ConnectException",
                    "Retry candidate",
                    "The target service could not be reached at connect time and may recover on a later attempt."),
            matchedRule(
                    "socket-exception",
                    FailureType.NETWORK_TRANSIENT,
                    "Exception type",
                    "SocketException with connection reset or broken pipe",
                    "Retry candidate",
                    "Only the clearly transient socket break variants are accepted here."),
            matchedRule(
                    "eof",
                    FailureType.NETWORK_TRANSIENT,
                    "Exception type",
                    "EOFException",
                    "Retry candidate",
                    "Unexpected end-of-stream often means the remote side dropped the connection mid-flight."));

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Smart Retry Docs";
    }

    @Override
    public String getUrlName() {
        return "smartRetryDocs";
    }

    public List<FailureTypeDoc> getFailureTypes() {
        return FAILURE_TYPES;
    }

    public List<MatchedRuleDoc> getMatchedRules() {
        return MATCHED_RULES;
    }

    private static FailureTypeDoc failureType(
            FailureType type, String meaning, String currentImplementation, String rationale) {
        return new FailureTypeDoc(
                "failure-type-" + slugify(type.name()),
                type.name(),
                meaning,
                currentImplementation,
                retryPolicyLabel(BuiltInProfiles.conservative()
                        .getRetryableFailureTypes()
                        .contains(type)),
                retryPolicyLabel(
                        BuiltInProfiles.infra().getRetryableFailureTypes().contains(type)),
                customProfileLabel(type),
                rationale);
    }

    private static MatchedRuleDoc matchedRule(
            String name,
            FailureType failureType,
            String triggerKind,
            String triggerHint,
            String defaultBehavior,
            String rationale) {
        boolean disableable = SUPPORTED_DISABLED_BUILT_IN_RULE_IDS.contains(name);
        return new MatchedRuleDoc(
                "rule-" + slugify(name),
                name,
                failureType.name(),
                "failure-type-" + slugify(failureType.name()),
                triggerKind,
                triggerHint,
                defaultBehavior,
                disableable ? "Yes" : "No",
                rationale);
    }

    private static String retryPolicyLabel(boolean retryable) {
        return retryable ? "Retry allowed" : "No retry";
    }

    private static String customProfileLabel(FailureType type) {
        return CustomProfileSettings.supportedRetryableFailureTypes().contains(type) ? "Configurable" : "No retry";
    }

    private static String slugify(String value) {
        return value.toLowerCase().replace('_', '-');
    }

    public static final class FailureTypeDoc {
        private final String anchorId;
        private final String name;
        private final String meaning;
        private final String currentImplementation;
        private final String conservativeBehavior;
        private final String infraBehavior;
        private final String customBehavior;
        private final String rationale;

        FailureTypeDoc(
                String anchorId,
                String name,
                String meaning,
                String currentImplementation,
                String conservativeBehavior,
                String infraBehavior,
                String customBehavior,
                String rationale) {
            this.anchorId = anchorId;
            this.name = name;
            this.meaning = meaning;
            this.currentImplementation = currentImplementation;
            this.conservativeBehavior = conservativeBehavior;
            this.infraBehavior = infraBehavior;
            this.customBehavior = customBehavior;
            this.rationale = rationale;
        }

        public String getAnchorId() {
            return anchorId;
        }

        public String getName() {
            return name;
        }

        public String getMeaning() {
            return meaning;
        }

        public String getCurrentImplementation() {
            return currentImplementation;
        }

        public String getConservativeBehavior() {
            return conservativeBehavior;
        }

        public String getInfraBehavior() {
            return infraBehavior;
        }

        public String getCustomBehavior() {
            return customBehavior;
        }

        public String getRationale() {
            return rationale;
        }
    }

    public static final class MatchedRuleDoc {
        private final String anchorId;
        private final String name;
        private final String failureTypeName;
        private final String failureTypeAnchorId;
        private final String triggerKind;
        private final String triggerHint;
        private final String defaultBehavior;
        private final String disableable;
        private final String rationale;

        MatchedRuleDoc(
                String anchorId,
                String name,
                String failureTypeName,
                String failureTypeAnchorId,
                String triggerKind,
                String triggerHint,
                String defaultBehavior,
                String disableable,
                String rationale) {
            this.anchorId = anchorId;
            this.name = name;
            this.failureTypeName = failureTypeName;
            this.failureTypeAnchorId = failureTypeAnchorId;
            this.triggerKind = triggerKind;
            this.triggerHint = triggerHint;
            this.defaultBehavior = defaultBehavior;
            this.disableable = disableable;
            this.rationale = rationale;
        }

        public String getAnchorId() {
            return anchorId;
        }

        public String getName() {
            return name;
        }

        public String getFailureTypeName() {
            return failureTypeName;
        }

        public String getFailureTypeAnchorId() {
            return failureTypeAnchorId;
        }

        public String getTriggerKind() {
            return triggerKind;
        }

        public String getTriggerHint() {
            return triggerHint;
        }

        public String getDefaultBehavior() {
            return defaultBehavior;
        }

        public String getDisableable() {
            return disableable;
        }

        public String getRationale() {
            return rationale;
        }
    }
}
