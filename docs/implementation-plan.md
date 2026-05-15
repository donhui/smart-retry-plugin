# Smart Retry Implementation Plan

## 1. Purpose

This document translates the Smart Retry MVP PRD into a concrete technical design for the Jenkins plugin codebase.

It complements:

- [`mvp-prd.md`](./mvp-prd.md): product behavior and scope
- [`development-plan.md`](./development-plan.md): delivery sequence and milestones

If this document conflicts with the PRD, the PRD is the source of truth.

## 2. High-Level Architecture

The MVP can be implemented with five main layers:

1. Pipeline step API
2. execution engine
3. failure classification
4. retry policy
5. observability and configuration

Conceptually:

```text
smartRetry step
  -> execute body
  -> on failure classify
  -> apply retry policy
  -> wait and retry or fail
  -> record attempt history
```

## 3. Suggested Package Structure

Suggested target package:

`io.jenkins.plugins.smart_retry`

Suggested subpackages:

- `io.jenkins.plugins.smart_retry.step`
- `io.jenkins.plugins.smart_retry.classify`
- `io.jenkins.plugins.smart_retry.policy`
- `io.jenkins.plugins.smart_retry.config`
- `io.jenkins.plugins.smart_retry.action`
- `io.jenkins.plugins.smart_retry.model`

This keeps the step API, policy logic, and UI concerns separate.

## 4. Core Domain Model

## `FailureType`

Enum for internal failure categories.

Initial values:

- `AGENT_LOST`
- `SCM_TRANSIENT`
- `NETWORK_TRANSIENT`
- `ARTIFACT_REPO_TRANSIENT`
- `IDENTITY_PROVIDER_TRANSIENT`
- `USER_ABORT`
- `PIPELINE_LOGIC_FAILURE`
- `COMPILATION_FAILURE`
- `TEST_ASSERTION_FAILURE`
- `DEPLOYMENT_FAILURE`
- `UNKNOWN`

Current V1 implementation note:

- the classifier currently emits `AGENT_LOST`, `SCM_TRANSIENT`, `NETWORK_TRANSIENT`, `ARTIFACT_REPO_TRANSIENT`, `IDENTITY_PROVIDER_TRANSIENT`, `PIPELINE_LOGIC_FAILURE`, `COMPILATION_FAILURE`, `TEST_ASSERTION_FAILURE`, `USER_ABORT`, and `UNKNOWN`
- the remaining enum values are kept now to stabilize the taxonomy and docs, but they still need explicit rules before they should appear in runtime behavior

## `FailureClassification`

Suggested fields:

- `FailureType type`
- `String matchedRule`
- `String summary`
- `boolean retryCandidate`

Purpose:

- explain why a failure was classified a certain way
- support logs and build summary

## `RetryDecision`

Suggested fields:

- `boolean shouldRetry`
- `String reason`
- `FailureType failureType`
- `int nextAttemptNumber`
- `long delayMillis`

Purpose:

- represent the result of policy evaluation

## `AttemptRecord`

Suggested fields:

- `int attemptNumber`
- `FailureType failureType`
- `String matchedRule`
- `boolean retried`
- `long delayMillis`
- `String outcome`

Purpose:

- store build-page summary information

## 5. Pipeline Step API

## `SmartRetryStep`

Responsibilities:

- define DataBound parameters
- expose the Pipeline DSL function name
- hold step-level overrides

Suggested fields:

- `String profile`
- `Integer maxRetries`
- `String backoff`
- `Integer initialDelaySeconds`

Possible later fields:

- `List<String> retryOn`
- `List<String> skipOn`

If added later, these should accept `FailureType` names or equivalent typed values, not raw log patterns.

Notes:

- use a body step pattern
- the step should be Pipeline-first and not try to support Freestyle in MVP

## `SmartRetryStep.DescriptorImpl`

Responsibilities:

- define `getFunctionName()` as `smartRetry`
- provide display metadata
- validate basic form fields if needed

## 6. Step Execution Flow

## `SmartRetryStepExecution`

This is the most important runtime class.

Responsibilities:

- invoke the body block
- intercept success and failure callbacks
- count attempts
- ask classifier and policy for next action
- reschedule retry attempts
- append attempt records to build action state

Suggested execution flow:

1. initialize merged runtime settings from global config and step parameters
2. start body execution
3. if body succeeds:
   - mark final success
   - persist final attempt state
4. if body fails:
   - classify the failure
   - compute retry decision
   - log decision
   - if retry allowed:
     - wait for delay
     - rerun body
   - else:
     - propagate failure

Implementation notes:

- keep the retry loop state small and serializable
- prefer immutable policy and classification results
- treat the body execution as the only retried region

## 7. Failure Classification Design

## `FailureClassifier`

Responsibilities:

- inspect thrown exceptions
- inspect associated messages
- assign a `FailureClassification`

Suggested internal structure:

- exception-based rules
- message-pattern rules
- deterministic precedence ordering

Suggested API:

```java
FailureClassification classify(Throwable error, @CheckForNull String messageContext);
```

Classifier behavior:

- return the first highest-priority confident match
- default to `UNKNOWN`
- keep the initial implementation explicit and readable rather than rule-engine heavy
- if custom rules are added later, keep them additive and explainable rather than turning the classifier into an unrestricted rule engine
- use surrounding operation context when a signal is ambiguous; for example, generic `could not resolve host` should map to `NETWORK_TRANSIENT` unless explicit SCM context is present
- treat Maven-style `Could not transfer artifact ... .jar.part (No such file or directory)` as `ARTIFACT_REPO_TRANSIENT` because the dominant signal is an interrupted repository download
- require explicit artifact-repository context before classifying generic `503/504` or `tls handshake timeout` signals as `ARTIFACT_REPO_TRANSIENT`
- prefer explicit HTTP-style 5xx phrases such as `503 Service Unavailable` or `status code 503` over bare numeric status codes when classifying generic network failures
- require explicit external-service context before classifying `connection refused` as `NETWORK_TRANSIENT`
- require explicit external-service context before classifying `connection reset` as `NETWORK_TRANSIENT`
- do not classify generic log-level `broken pipe` text as retryable unless exception context makes the transport failure explicit
- require narrow LDAP reauthentication context before classifying HTTP-style `401` responses as `IDENTITY_PROVIDER_TRANSIENT`
- require explicit SCM context before classifying `curl 56`, `unexpected disconnect`, `early EOF`, or `index-pack failed` as `SCM_TRANSIENT`
- classify high-confidence TypeScript, Go, and C/C++ compiler diagnostics as `COMPILATION_FAILURE`, but leave generic wrapper failures such as bare `npm ERR!` as `UNKNOWN`

## `ClassificationRule`

Optional helper abstraction if needed.

Suggested fields:

- `FailureType type`
- `Pattern pattern`
- `int priority`
- `String name`

This can be useful for message-based matching, but the first implementation does not need a complex DSL.

## 8. Retry Policy Design

## `RetryPolicy`

Responsibilities:

- determine whether a classified failure should be retried
- apply active profile and attempt limits
- calculate delay

Suggested API:

```java
RetryDecision decide(
    FailureClassification classification,
    RuntimeSettings settings,
    int attemptNumber
);
```

Policy inputs:

- active profile
- allowed failure types
- max retries
- backoff strategy
- current attempt number

Policy rules:

- hard-stop failure types should never retry
- `UNKNOWN` should never retry
- if attempts are exhausted, fail immediately

## `BackoffStrategy`

Suggested enum values:

- `FIXED`
- `EXPONENTIAL`

Suggested delay rules:

- fixed: same delay each retry
- exponential: multiply from initial delay

Keep it simple in MVP. No jitter is required initially.

## 9. Configuration Design

## `SmartRetryGlobalConfiguration`

Responsibilities:

- store the default profile for steps that do not set one explicitly
- store the bounded number of console log lines used as attempt-scoped classification context
- store shared retry timing defaults used by all profiles
- store named custom profile allowlists
- store built-in retryable rule disablement by stable rule id

Current V1 model:

- `String defaultProfile`
- `int consoleContextLines`
- `int maxRetries`
- `BackoffStrategy backoff`
- `int initialDelaySeconds`
- `List<CustomProfileSettings> customProfiles`
- `Set<String> disabledBuiltInRules`

Follow-on configuration candidates:

- profile defaults
- built-in rule disabling by stable rule id
- custom message patterns
- persisted custom profile settings

Suggested constrained V2 custom-rule model:

- `Set<String> disabledBuiltInRules`
- `List<CustomClassificationRule> customRules`

## `CustomClassificationRule`

Suggested fields:

- `String name`
- `String pattern`
- `FailureType failureType`
- `boolean enabled`
- `String description`

First implementation constraints:

- allow disabling only built-in retryable message rules, not hard-stop exception semantics
- allow only additive message-pattern rules
- allow only `AGENT_LOST`, `SCM_TRANSIENT`, `NETWORK_TRANSIENT`, `ARTIFACT_REPO_TRANSIENT`, and `IDENTITY_PROVIDER_TRANSIENT`
- reject attempts to map custom rules to `USER_ABORT`, `PIPELINE_LOGIC_FAILURE`, `COMPILATION_FAILURE`, `TEST_ASSERTION_FAILURE`, `DEPLOYMENT_FAILURE`, or `UNKNOWN`
- keep built-in rules present even when custom rules are configured

Responsibility split:

- `disabledBuiltInRules` narrows the active built-in classifier by stable rule id
- `CustomClassificationRule` handles raw exception/message matching
- profile settings handle retry policy after classification
- future `retryOn` and `skipOn` should therefore reference `FailureType` values, not duplicate regex matching

Suggested initial configuration model:

- default profile name
- bounded console context settings

Possible structure:

- current V1: a single `SmartRetryGlobalConfiguration` object with scalar settings
- possible V2 direction: a separate persisted `CustomClassificationRule` list owned by global configuration rather than spreading regex state across profiles

## `CustomProfileSettings`

Suggested fields:

- `String name`
- `Set<FailureType> retryableFailureTypes`

Step-parameter merge rules:

- global config provides one shared set of retry timing defaults
- built-in profiles provide fixed retryable `FailureType` membership
- custom profiles provide named retryable `FailureType` allowlists
- step parameters override the effective defaults when set

Current V1 implementation note:

- built-in profile membership still comes from `BuiltInProfiles`
- globally configured retry timing defaults participate in the same step-override merge path for built-ins and customs
- unknown profile names now fail fast instead of silently falling back to `conservative`
- broader message-pattern authoring remains future work

Suggested custom-rule precedence:

1. built-in hard-stop exception rules
2. built-in rule disable check for message-based rules
3. custom non-retryable rules
4. built-in non-retryable message rules
5. built-in retryable rules
6. custom retryable rules
7. `UNKNOWN`

This ordering keeps explicit safety behavior ahead of user-added retry rules while still allowing administrators to narrow behavior either by disabling a specific built-in rule or by tightening local policies.

Suggested future `retryOn` / `skipOn` semantics:

- apply after classification and after built-in/custom rules have produced a `FailureType`
- `skipOn` should override `retryOn` when both mention the same type
- these controls should be represented as typed allow/block lists rather than another free-form message-pattern layer

Suggested rule-id design:

- every built-in message rule should have a stable public id
- configuration should validate `disabledBuiltInRules` entries against the known built-in rule id set
- logs and build summary output should still mention a built-in rule id when it matches, and should indicate when a would-be match was suppressed by configuration if that is practical

## 10. Observability Design

## `SmartRetryRunAction`

Responsibilities:

- persist attempt records for a build
- expose retry summary to the UI

Suggested fields:

- `String profile`
- `List<AttemptRecord> attempts`
- `String finalOutcome`

UI scope for MVP:

- simple summary box on the build page
- no dashboard, charting, or trend view

## Console Logging

Suggested helper:

- a small logger utility or consistent message formatter

Output should include:

- attempt number
- failure type
- matched rule
- decision
- delay

If custom rules are added later, output should also include:

- a stable `custom-...` rule identifier
- a clear indication that the rule source was global configuration rather than built-in classifier logic

## 11. Jenkins Views and Resources

Likely resources to add:

- global config Jelly view
- build action summary Jelly view
- messages properties for user-facing strings

Keep the first UI minimal and utilitarian.

## 12. Test Strategy

## Unit Tests

Best candidates:

- `FailureClassifierTest`
- `RetryPolicyTest`
- `BackoffStrategyTest`

What they should verify:

- rule precedence
- retryable vs non-retryable classes
- profile behavior
- delay calculations

## Integration Tests

Best candidates:

- `SmartRetryStepTest`
- `SmartRetryGlobalConfigurationTest`

What they should verify:

- body executes normally on success
- transient failure retries and later succeeds
- non-retryable failure stops immediately
- default profile settings override correctly when the step omits `profile`
- custom rules, if enabled later, are surfaced clearly in logs and run-action data

## UI and Config Tests

Verify:

- config round-trip
- action data is persisted and readable

## 13. Plugin Skeleton Conversion Plan

The repository has already completed the initial generated-plugin cleanup and now uses Smart Retry-specific packages, tests, and plugin metadata.

Historical conversion steps:

1. remove `HelloWorldBuilder` and its resources
2. replace sample tests with Smart Retry tests
3. create the new package tree under `io.jenkins.plugins.smart_retry`
4. update plugin metadata in `pom.xml`
5. update `README.md`

This cleanup is complete and should stay complete as the repository evolves.

## 14. Dependency Direction

Current V1 already includes the dependencies needed for:

- the custom Pipeline body step
- global configuration
- build run action rendering
- Jenkins test coverage for the shipped behavior

Future dependency review should only be reopened if custom rule authoring or richer UI surfaces materially expand the plugin scope.

## 15. Implementation Sequence

Recommended order:

1. clean sample scaffold
2. create core model classes
3. create classifier and policy with unit tests
4. implement step descriptor and execution skeleton
5. wire retry loop
6. add global configuration
7. add build summary action
8. expand tests and docs

This sequence minimizes the amount of Jenkins-specific plumbing needed before the core logic is reliable.

## 16. Open Technical Questions

- Which exact Pipeline step base classes and callback patterns are the best fit for a body step in this plugin?
- What log context is realistically available from the failing body execution?
  - MVP answer: use a bounded tail of `Run` console log, scoped to the current attempt via a marker line (to support cases like `sh` where stderr does not appear in the thrown exception message).
- How should the plugin serialize attempt state across Jenkins restarts during in-flight Pipeline execution?
  - MVP answer: keep only primitive attempt state in `StepExecution` (attempt number and next-run timestamp), keep scheduled tasks transient, and reschedule in `onResume()`.
- Should persisted custom profile settings and message-pattern authoring land in the pilot-ready V1 line, or move to a cleaner V2 slice after validation of the current deterministic core?
- Should constrained global custom rules be enough, or will operators still need step-local `retryOn` and `skipOn` controls after real-world usage?

## 17. Recommended First Coding Slice

The best first implementation slice is:

- remove sample code
- create `FailureType`
- create `FailureClassification`
- create `RetryDecision`
- create `RetryPolicy`
- create tests for `conservative` and `infra`

Why:

- this establishes the plugin's behavioral core before dealing with Pipeline callback complexity
