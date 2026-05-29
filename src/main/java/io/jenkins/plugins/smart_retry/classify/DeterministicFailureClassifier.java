package io.jenkins.plugins.smart_retry.classify;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import io.jenkins.plugins.smart_retry.config.CustomClassificationRule;
import io.jenkins.plugins.smart_retry.model.FailureClassification;
import io.jenkins.plugins.smart_retry.model.FailureType;
import java.io.EOFException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;

/**
 * MVP classifier: deterministic, conservative, and explainable.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Prefer explicit exception-type matches first.
 *   <li>Fall back to message-pattern rules with stable ordering.
 *   <li>Default to {@link FailureType#UNKNOWN} and mark it non-retryable.
 * </ul>
 */
public final class DeterministicFailureClassifier implements FailureClassifier {

    private static final String ARTIFACT_CONTEXT =
            "(could not transfer artifact|downloading from|downloaded from|from/to artifactory|artifactory|nexus|maven-public|\\.m2/repository|\\bartifact\\b)";
    private static final String SCM_CONTEXT =
            "(checkout|fetch|clone|ls-remote|git|fetch-pack|index-pack|version control)";
    private static final String EXTERNAL_SERVICE_CONTEXT =
            "(http://|https://|request url|dial tcp|docker pull|/v2/|registry|daemon: get)";
    private static final String TRANSIENT_HTTP_5XX =
            "(502\\s+bad gateway|503\\s+service unavailable|504\\s+gateway timeout|received status code\\s*(502|503|504)|status code\\s*(502|503|504)|http\\s*(status|code)?\\s*[:=]?\\s*(502|503|504))";
    private static final String JAVA_SOURCE_PATH = "[^\\s\\n]+\\.java";
    private static final String GO_SOURCE_PATH = "[^\\s\\n]+\\.go";
    private static final String KOTLIN_SOURCE_PATH = "[^\\s\\n]+\\.kt";
    private static final String C_FAMILY_SOURCE_PATH = "[^\\s\\n]+\\.(c|cc|cpp|cxx|h|hpp)";
    private static final Pattern AGENT_INFRASTRUCTURE_SIGNAL = Pattern.compile(
            "(channelclosedexception|cannot contact\\s+[^\\s:]+|was marked offline|agent has not been fully initialized|remote call on\\s+[^\\s:]+\\s+failed|hudson\\.remoting\\.channel|connection was broken|channel is closing|channel closed)",
            Pattern.CASE_INSENSITIVE);

    private static final List<MessageRule> MESSAGE_RULES = List.of(
            // PIPELINE_LOGIC_FAILURE
            MessageRule.nonRetryable(
                    FailureType.PIPELINE_LOGIC_FAILURE,
                    "pipeline-no-such-dsl-method",
                    Pattern.compile("no such dsl method", Pattern.CASE_INSENSITIVE),
                    "Pipeline referenced an unknown Jenkins step or DSL method"),
            MessageRule.nonRetryable(
                    FailureType.PIPELINE_LOGIC_FAILURE,
                    "pipeline-no-such-property",
                    Pattern.compile("no such property", Pattern.CASE_INSENSITIVE),
                    "Pipeline referenced an unknown Groovy property or variable"),
            MessageRule.nonRetryable(
                    FailureType.PIPELINE_LOGIC_FAILURE,
                    "pipeline-script-security-rejected",
                    Pattern.compile(
                            "(rejectedaccessexception|scripts not permitted to use|script approval)",
                            Pattern.CASE_INSENSITIVE),
                    "Pipeline script was rejected by Jenkins script security"),
            MessageRule.nonRetryable(
                    FailureType.PIPELINE_LOGIC_FAILURE,
                    "pipeline-groovy-startup-failed",
                    Pattern.compile(
                            "((workflowscript|jenkinsfile).*(startup failed|missingpropertyexception|missingmethodexception|multiplecompilationerrorsexception))"
                                    + "|((startup failed|missingpropertyexception|missingmethodexception|multiplecompilationerrorsexception).*(workflowscript|jenkinsfile))",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Pipeline Groovy script failed before or during evaluation"),
            // COMPILATION_FAILURE
            MessageRule.nonRetryable(
                    FailureType.COMPILATION_FAILURE,
                    "gradle-compile-task-failed",
                    Pattern.compile(
                            "execution failed for task ['\"]?:[^\\n'\"]*compile(java|testjava|groovy|testgroovy|scala|testscala|kotlin|testkotlin)['\"]?\\.?\\s*>\\s*.*(compilation failed|compilation error)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Gradle compile task reported a deterministic compilation failure"),
            MessageRule.nonRetryable(
                    FailureType.COMPILATION_FAILURE,
                    "compilation-failure",
                    Pattern.compile("(compilation failure|compilation error)", Pattern.CASE_INSENSITIVE),
                    "Build compilation failed deterministically"),
            MessageRule.nonRetryable(
                    FailureType.COMPILATION_FAILURE,
                    "cannot-find-symbol",
                    Pattern.compile("cannot find symbol", Pattern.CASE_INSENSITIVE),
                    "Build failed because source symbols could not be resolved"),
            MessageRule.nonRetryable(
                    FailureType.COMPILATION_FAILURE,
                    "java-compiler-error",
                    Pattern.compile(JAVA_SOURCE_PATH + ":\\d+(?::\\d+)?:\\s+error:", Pattern.CASE_INSENSITIVE),
                    "Java compilation failed deterministically"),
            MessageRule.nonRetryable(
                    FailureType.COMPILATION_FAILURE,
                    "kotlin-compiler-error",
                    Pattern.compile(
                            "e:\\s+(file:/+)?" + KOTLIN_SOURCE_PATH + ":\\s*\\(\\d+,\\s*\\d+\\):",
                            Pattern.CASE_INSENSITIVE),
                    "Kotlin compilation failed deterministically"),
            MessageRule.nonRetryable(
                    FailureType.COMPILATION_FAILURE,
                    "typescript-compiler-error",
                    Pattern.compile("error\\s+TS\\d+:", Pattern.CASE_INSENSITIVE),
                    "TypeScript compilation failed deterministically"),
            MessageRule.nonRetryable(
                    FailureType.COMPILATION_FAILURE,
                    "go-compiler-error",
                    Pattern.compile(
                            GO_SOURCE_PATH
                                    + ":\\d+:\\d+:\\s+(undefined:|cannot use|no required module provides package|cannot find package|imported and not used|syntax error:)",
                            Pattern.CASE_INSENSITIVE),
                    "Go compilation failed deterministically"),
            MessageRule.nonRetryable(
                    FailureType.COMPILATION_FAILURE,
                    "c-family-compiler-error",
                    Pattern.compile(
                            C_FAMILY_SOURCE_PATH + ":\\d+(?::\\d+)?:\\s+(fatal\\s+error|error):",
                            Pattern.CASE_INSENSITIVE),
                    "C/C++ compilation failed deterministically"),
            MessageRule.nonRetryable(
                    FailureType.COMPILATION_FAILURE,
                    "c-family-linker-error",
                    Pattern.compile(
                            "(undefined reference to|collect2: error: ld returned \\d+ exit status|ld returned \\d+ exit status|clang: error: linker command failed)",
                            Pattern.CASE_INSENSITIVE),
                    "C/C++ linking failed deterministically"),
            // TEST_ASSERTION_FAILURE
            MessageRule.nonRetryable(
                    FailureType.TEST_ASSERTION_FAILURE,
                    "test-opentest4j-assertion-failed",
                    Pattern.compile("org\\.opentest4j\\.assertionfailederror", Pattern.CASE_INSENSITIVE),
                    "Test framework reported an assertion mismatch"),
            MessageRule.nonRetryable(
                    FailureType.TEST_ASSERTION_FAILURE,
                    "test-junit-assertion-failed",
                    Pattern.compile("(assertionfailederror|comparisonfailure)", Pattern.CASE_INSENSITIVE),
                    "JUnit reported a deterministic assertion failure"),
            MessageRule.nonRetryable(
                    FailureType.TEST_ASSERTION_FAILURE,
                    "test-runner-failures-summary",
                    Pattern.compile(
                            "(there (are|were) test failures|failures!!!|tests run:\\s*\\d+.*(failures|errors):\\s*[1-9]\\d*)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Test runner reported one or more assertion failures"),
            MessageRule.nonRetryable(
                    FailureType.TEST_ASSERTION_FAILURE,
                    "test-pytest-failures-summary",
                    Pattern.compile(
                            "(short test summary info.*\\b(failed|error)s?\\b)"
                                    + "|(={2,}\\s*\\d+\\s+(failed|error)s?\\b[^\\n]*\\bin\\s+\\d+(?:\\.\\d+)?s\\s*={2,})",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Pytest reported one or more deterministic test failures"),
            MessageRule.nonRetryable(
                    FailureType.TEST_ASSERTION_FAILURE,
                    "test-gradle-failures-summary",
                    Pattern.compile(
                            "(there were failing tests\\.\\s*see the report at:)"
                                    + "|(\\b\\d+\\s+tests? completed,\\s*\\d+\\s+failed\\b(?:,\\s*\\d+\\s+skipped\\b)?)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Gradle test task reported one or more deterministic test failures"),
            // AGENT_LOST
            MessageRule.retryCandidate(
                    FailureType.AGENT_LOST,
                    "agent-kubernetes-pod-not-found",
                    Pattern.compile(
                            "((pod|pods)\\s+[\"']?[^\\s\"']+[\"']?\\s+(was deleted|not found))|(kubernetesclientexception:.*(pod|pods).*(was deleted|not found))",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Kubernetes agent pod disappeared or could no longer be found"),
            MessageRule.retryCandidate(
                    FailureType.AGENT_LOST,
                    "agent-kubernetes-evicted",
                    Pattern.compile(
                            "(\\bevicted\\b|the node was low on resource|ephemeral-storage|memorypressure|diskpressure)",
                            Pattern.CASE_INSENSITIVE),
                    "Kubernetes agent pod was evicted or the backing node ran out of resources"),
            MessageRule.retryCandidate(
                    FailureType.AGENT_LOST,
                    "agent-removed",
                    Pattern.compile(
                            "(agent was removed|node was offline|channel closed|channel is closing|pod was deleted|evicted)",
                            Pattern.CASE_INSENSITIVE),
                    "Agent/channel disappeared during execution"),
            // SCM_TRANSIENT
            MessageRule.retryCandidate(
                    FailureType.SCM_TRANSIENT,
                    "scm-remote-end-hung-up",
                    Pattern.compile("remote end hung up unexpectedly", Pattern.CASE_INSENSITIVE),
                    "SCM remote hung up unexpectedly"),
            MessageRule.retryCandidate(
                    FailureType.SCM_TRANSIENT,
                    "scm-could-not-resolve-host",
                    Pattern.compile(
                            SCM_CONTEXT + ".*(could not resolve host)|(could not resolve host).*" + SCM_CONTEXT,
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "SCM host could not be resolved"),
            MessageRule.retryCandidate(
                    FailureType.SCM_TRANSIENT,
                    "scm-transport-interrupted",
                    Pattern.compile(
                            SCM_CONTEXT
                                    + ".*(curl\\s*56|rpc failed|unexpected disconnect while reading sideband packet|early eof|index-pack failed|sideband packet)"
                                    + "|(curl\\s*56|rpc failed|unexpected disconnect while reading sideband packet|early eof|index-pack failed|sideband packet).*"
                                    + SCM_CONTEXT,
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "SCM transport disconnected during clone or fetch"),
            MessageRule.retryCandidate(
                    FailureType.SCM_TRANSIENT,
                    "scm-http-5xx",
                    Pattern.compile(
                            SCM_CONTEXT + ".*" + TRANSIENT_HTTP_5XX + "|" + TRANSIENT_HTTP_5XX + ".*" + SCM_CONTEXT,
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "SCM operation returned a transient HTTP 5xx response"),
            MessageRule.nonRetryable(
                    FailureType.SCM_CONFIGURATION_FAILURE,
                    "scm-revision-not-found",
                    Pattern.compile(
                            "(couldn't find any revision to build|could not find any revision to build|unable to find revision to build|verify the repository and branch configuration for this job|no revision to build)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Requested SCM revision, branch, tag, or commit does not exist"),
            MessageRule.nonRetryable(
                    FailureType.SCM_CONFIGURATION_FAILURE,
                    "scm-remote-branch-not-found",
                    Pattern.compile(
                            "(remote branch .* not found in upstream origin|fatal:\\s*remote branch .* not found|branch .* not found in upstream origin)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Requested SCM branch or ref does not exist"),
            // ARTIFACT_REPO_TRANSIENT
            MessageRule.retryCandidate(
                    FailureType.ARTIFACT_REPO_TRANSIENT,
                    "artifact-partial-download",
                    Pattern.compile(
                            "(could not transfer artifact).*(\\.jar\\.part).*(no such file or directory)|(\\.jar\\.part).*(no such file or directory).*(could not transfer artifact)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Artifact download ended with a partial .jar.part transfer"),
            MessageRule.retryCandidate(
                    FailureType.ARTIFACT_REPO_TRANSIENT,
                    "artifact-http-5xx",
                    Pattern.compile(
                            ARTIFACT_CONTEXT
                                    + ".*"
                                    + TRANSIENT_HTTP_5XX
                                    + "|"
                                    + TRANSIENT_HTTP_5XX
                                    + ".*"
                                    + ARTIFACT_CONTEXT,
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Artifact repository returned a transient 5xx response"),
            MessageRule.retryCandidate(
                    FailureType.ARTIFACT_REPO_TRANSIENT,
                    "artifact-tls-handshake-timeout",
                    Pattern.compile(
                            ARTIFACT_CONTEXT + ".*tls handshake timeout|tls handshake timeout.*" + ARTIFACT_CONTEXT,
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "TLS handshake timed out while contacting artifact repository"),
            MessageRule.retryCandidate(
                    FailureType.ARTIFACT_REPO_TRANSIENT,
                    "artifact-connection-refused",
                    Pattern.compile(
                            ARTIFACT_CONTEXT
                                    + ".*(connect:\\s*connection refused|connection refused)"
                                    + "|(connect:\\s*connection refused|connection refused).*"
                                    + ARTIFACT_CONTEXT,
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Artifact repository refused the connection"),
            MessageRule.retryCandidate(
                    FailureType.ARTIFACT_REPO_TRANSIENT,
                    "artifact-connection-reset",
                    Pattern.compile(
                            ARTIFACT_CONTEXT
                                    + ".*(connection reset|connection reset by peer)"
                                    + "|(connection reset|connection reset by peer).*"
                                    + ARTIFACT_CONTEXT,
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Artifact repository connection dropped unexpectedly"),
            // IDENTITY_PROVIDER_TRANSIENT
            MessageRule.retryCandidate(
                    FailureType.IDENTITY_PROVIDER_TRANSIENT,
                    "identity-provider-ldap-reauthentication-failed",
                    Pattern.compile(
                            "(can't reauthenticate ldap|ldap reauthentication failed|reauthenticate ldap).*(response code\\s*401|server response:?\\s*401|status\\s*:?\\s*401|\\b401\\b)"
                                    + "|(response code\\s*401|server response:?\\s*401|status\\s*:?\\s*401|\\b401\\b).*(can't reauthenticate ldap|ldap reauthentication failed|reauthenticate ldap)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "LDAP identity provider failed during reauthentication"),
            // NETWORK_TRANSIENT
            MessageRule.retryCandidate(
                    FailureType.NETWORK_TRANSIENT,
                    "network-python-gitlab-5xx",
                    Pattern.compile(
                            "(gitlab\\.exceptions\\.gitlabgeterror|gitlabgeterror).*(502|503|504|gitlab is not responding)"
                                    + "|(502|503|504|gitlab is not responding).*(gitlab\\.exceptions\\.gitlabgeterror|gitlabgeterror)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "python-gitlab reported a transient GitLab 5xx response"),
            MessageRule.retryCandidate(
                    FailureType.NETWORK_TRANSIENT,
                    "network-could-not-resolve-host",
                    Pattern.compile("could not resolve host", Pattern.CASE_INSENSITIVE),
                    "External service host could not be resolved"),
            MessageRule.retryCandidate(
                    FailureType.NETWORK_TRANSIENT,
                    "network-http-5xx",
                    Pattern.compile(TRANSIENT_HTTP_5XX, Pattern.CASE_INSENSITIVE),
                    "External service returned a transient 5xx response"),
            MessageRule.retryCandidate(
                    FailureType.NETWORK_TRANSIENT,
                    "network-tls-handshake-timeout",
                    Pattern.compile("tls handshake timeout", Pattern.CASE_INSENSITIVE),
                    "TLS handshake timed out while contacting an external service"),
            MessageRule.retryCandidate(
                    FailureType.NETWORK_TRANSIENT,
                    "network-connection-refused",
                    Pattern.compile(
                            EXTERNAL_SERVICE_CONTEXT
                                    + ".*(connect:\\s*connection refused|connection refused)"
                                    + "|(connect:\\s*connection refused|connection refused).*"
                                    + EXTERNAL_SERVICE_CONTEXT,
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Connection was refused by an external service"),
            MessageRule.retryCandidate(
                    FailureType.NETWORK_TRANSIENT,
                    "network-timeout",
                    Pattern.compile(
                            "(read timed out|connect timed out|connection timed out)", Pattern.CASE_INSENSITIVE),
                    "Network timeout while contacting an external service"),
            MessageRule.retryCandidate(
                    FailureType.NETWORK_TRANSIENT,
                    "network-connection-reset",
                    Pattern.compile(
                            EXTERNAL_SERVICE_CONTEXT
                                    + ".*(connection reset|connection reset by peer)"
                                    + "|(connection reset|connection reset by peer).*"
                                    + EXTERNAL_SERVICE_CONTEXT,
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "Network connection dropped unexpectedly"));

    private static final Set<String> SUPPORTED_DISABLED_BUILT_IN_RULE_IDS = computeSupportedDisabledBuiltInRuleIds();

    private final Set<String> disabledBuiltInRuleIds;
    private final List<ConfiguredRule> customClassificationRules;

    public DeterministicFailureClassifier() {
        this(Set.of(), List.of());
    }

    public DeterministicFailureClassifier(Set<String> disabledBuiltInRuleIds) {
        this(disabledBuiltInRuleIds, List.of());
    }

    public DeterministicFailureClassifier(
            Set<String> disabledBuiltInRuleIds, List<CustomClassificationRule> customClassificationRules) {
        this.disabledBuiltInRuleIds = normalizeDisabledBuiltInRuleIds(disabledBuiltInRuleIds);
        this.customClassificationRules = normalizeCustomClassificationRules(customClassificationRules);
    }

    public static Set<String> supportedDisabledBuiltInRuleIds() {
        return SUPPORTED_DISABLED_BUILT_IN_RULE_IDS;
    }

    @Override
    public FailureClassification classify(Throwable error, @CheckForNull String messageContext) {
        Objects.requireNonNull(error, "error must not be null");

        FailureClassification exceptionMatch = classifyByException(error);
        if (exceptionMatch != null) {
            return exceptionMatch;
        }

        String combinedMessage = combineMessages(error, messageContext);
        if (combinedMessage != null) {
            for (ConfiguredRule customRule : customClassificationRules) {
                if (customRule.matches(combinedMessage)) {
                    return customRule.toClassification();
                }
            }
            for (MessageRule rule : MESSAGE_RULES) {
                if (isBuiltInRuleDisabled(rule)) {
                    continue;
                }
                if (rule.matches(combinedMessage)) {
                    return rule.toClassification();
                }
            }
        }

        return FailureClassification.nonRetryable(FailureType.UNKNOWN, null, "No deterministic rule matched");
    }

    @CheckForNull
    private static FailureClassification classifyByException(Throwable error) {
        String throwableContext = collectThrowableContext(error);
        if (matchesAgentInfrastructureSignal(throwableContext)) {
            return FailureClassification.retryCandidate(
                    FailureType.AGENT_LOST,
                    "agent-remoting-channel-closed",
                    "Agent remoting channel closed or agent initialization broke mid-run");
        }
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof FlowInterruptedException) {
                return FailureClassification.nonRetryable(
                        FailureType.USER_ABORT, "flow-interrupted", "Build was interrupted");
            }
            FailureClassification networkExceptionMatch = classifyTransientNetworkException(t);
            if (networkExceptionMatch != null) {
                return networkExceptionMatch;
            }
        }
        return null;
    }

    @CheckForNull
    private static FailureClassification classifyTransientNetworkException(Throwable throwable) {
        if (throwable instanceof SocketTimeoutException) {
            return FailureClassification.retryCandidate(
                    FailureType.NETWORK_TRANSIENT, "socket-timeout", "Socket read/connect timed out");
        }
        if (throwable instanceof ConnectException) {
            return FailureClassification.retryCandidate(
                    FailureType.NETWORK_TRANSIENT, "connect-exception", "Failed to connect to remote service");
        }
        if (throwable instanceof EOFException) {
            return FailureClassification.retryCandidate(
                    FailureType.NETWORK_TRANSIENT, "eof", "Unexpected end of stream");
        }
        if (throwable instanceof SocketException) {
            String message = safeLower(throwable.getMessage());
            if (message != null && (message.contains("connection reset") || message.contains("broken pipe"))) {
                return FailureClassification.retryCandidate(
                        FailureType.NETWORK_TRANSIENT, "socket-exception", "Socket connection was reset or broken");
            }
        }
        return null;
    }

    @CheckForNull
    private static String collectThrowableContext(Throwable error) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(t.getClass().getName());
            String message = t.getMessage();
            if (message != null && !message.isBlank()) {
                sb.append(": ").append(message);
            }
        }
        if (sb.isEmpty()) {
            return null;
        }
        return sb.toString();
    }

    private static boolean matchesAgentInfrastructureSignal(@CheckForNull String message) {
        return message != null && AGENT_INFRASTRUCTURE_SIGNAL.matcher(message).find();
    }

    private boolean isBuiltInRuleDisabled(MessageRule rule) {
        return rule.isDisableable() && disabledBuiltInRuleIds.contains(rule.name());
    }

    @CheckForNull
    private static String combineMessages(Throwable error, @CheckForNull String messageContext) {
        StringBuilder sb = new StringBuilder();
        String rootMessage = error.getMessage();
        if (rootMessage != null && !rootMessage.isBlank()) {
            sb.append(rootMessage);
        }
        if (messageContext != null && !messageContext.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(messageContext);
        }
        if (sb.isEmpty()) {
            return null;
        }
        return sb.toString();
    }

    @CheckForNull
    private static String safeLower(@CheckForNull String s) {
        if (s == null) {
            return null;
        }
        return s.toLowerCase(Locale.ROOT);
    }

    private static Set<String> computeSupportedDisabledBuiltInRuleIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (MessageRule rule : MESSAGE_RULES) {
            if (rule.isDisableable()) {
                ids.add(rule.name());
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    private static Set<String> normalizeDisabledBuiltInRuleIds(Set<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            String id = rawId.trim();
            if (SUPPORTED_DISABLED_BUILT_IN_RULE_IDS.contains(id)) {
                normalized.add(id);
            }
        }
        if (normalized.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static List<ConfiguredRule> normalizeCustomClassificationRules(List<CustomClassificationRule> rawRules) {
        if (rawRules == null || rawRules.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<ConfiguredRule> normalized = new java.util.ArrayList<>();
        for (CustomClassificationRule rawRule : rawRules) {
            if (rawRule == null || !rawRule.isEnabled()) {
                continue;
            }
            String name = CustomClassificationRule.normalizeName(rawRule.getName());
            String pattern = CustomClassificationRule.normalizePattern(rawRule.getPattern());
            FailureType type = rawRule.getFailureType();
            if (name.isBlank() || pattern.isBlank() || type == null || type == FailureType.UNKNOWN) {
                continue;
            }
            normalized.add(new ConfiguredRule(name, type, rawRule.compiledPattern(), rawRule.getDescription()));
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(normalized);
    }

    private record MessageRule(FailureType type, String name, Pattern pattern, String summary, boolean retryCandidate) {

        static MessageRule retryCandidate(FailureType type, String name, Pattern pattern, String summary) {
            return new MessageRule(type, name, pattern, summary, true);
        }

        static MessageRule nonRetryable(FailureType type, String name, Pattern pattern, String summary) {
            return new MessageRule(type, name, pattern, summary, false);
        }

        boolean matches(String message) {
            return pattern.matcher(message).find();
        }

        boolean isDisableable() {
            return retryCandidate;
        }

        FailureClassification toClassification() {
            if (retryCandidate) {
                return FailureClassification.retryCandidate(type, name, summary);
            }
            return FailureClassification.nonRetryable(type, name, summary);
        }
    }

    private record ConfiguredRule(FailureType type, String name, Pattern pattern, String summary) {

        ConfiguredRule(String name, FailureType type, Pattern pattern, @CheckForNull String summary) {
            this(
                    type,
                    name,
                    pattern,
                    summary == null || summary.isBlank()
                            ? "Matched custom classification rule '" + name + "'"
                            : summary.trim());
        }

        boolean matches(String message) {
            return pattern.matcher(message).find();
        }

        FailureClassification toClassification() {
            if (isRetryCandidateType(type)) {
                return FailureClassification.retryCandidate(type, name, summary);
            }
            return FailureClassification.nonRetryable(type, name, summary);
        }

        private static boolean isRetryCandidateType(FailureType type) {
            return switch (type) {
                case AGENT_LOST,
                        SCM_TRANSIENT,
                        NETWORK_TRANSIENT,
                        ARTIFACT_REPO_TRANSIENT,
                        IDENTITY_PROVIDER_TRANSIENT -> true;
                default -> false;
            };
        }
    }
}
