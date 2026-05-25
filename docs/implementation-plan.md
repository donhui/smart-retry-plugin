# Smart Retry Implementation Plan

## 1. Purpose

This document translates the Smart Retry MVP PRD into a concrete technical design for the Jenkins plugin codebase.

The implementation should preserve the core product promise: avoid blind retries, retry high-confidence transient failures, and fail fast on deterministic errors.

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

See [`mvp-prd.md §9`](./mvp-prd.md) for the full taxonomy, retryable/non-retryable classification, and V1 implementation notes.

Current V1 also includes `SCM_CONFIGURATION_FAILURE` for deterministic SCM target-selection errors such as a missing revision, branch, tag, or commit.

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

They should also apply relative to the resolved profile rather than replacing it outright:

- `retryOn` should add `FailureType` values to the active profile allowlist
- `skipOn` should remove `FailureType` values from the active profile allowlist

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

For the full set of per-signal classification rules and context requirements, see [`mvp-prd.md §10`](./mvp-prd.md).

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

For the authoritative list of non-retryable failure types, see [`mvp-prd.md §15`](./mvp-prd.md).

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

Current V1 shared default:

- `initialDelaySeconds = 10`

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

First implementation constraints: see [`mvp-prd.md §11.4`](./mvp-prd.md) for the full safety boundaries and allowed target types.

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

For the custom-rule evaluation order, see [`mvp-prd.md §14`](./mvp-prd.md).

Suggested future `retryOn` / `skipOn` semantics:

- apply after classification and after built-in/custom rules have produced a `FailureType`
- treat the selected profile as the base allowlist
- `retryOn` should add `FailureType` values to that allowlist
- `skipOn` should remove `FailureType` values from that allowlist
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

The generated-plugin cleanup is complete. The repository uses Smart Retry-specific packages, tests, and plugin metadata under `io.jenkins.plugins.smart_retry`.

## 14. Dependency Direction

Current V1 already includes the dependencies needed for:

- the custom Pipeline body step
- global configuration
- build run action rendering
- Jenkins test coverage for the shipped behavior

Future dependency review should only be reopened if custom rule authoring or richer UI surfaces materially expand the plugin scope.

## 15. Open Technical Questions

- Which exact Pipeline step base classes and callback patterns are the best fit for a body step in this plugin?
- What log context is realistically available from the failing body execution?
  - MVP answer: use a bounded tail of `Run` console log, scoped to the current attempt via a marker line (to support cases like `sh` where stderr does not appear in the thrown exception message).
- How should the plugin serialize attempt state across Jenkins restarts during in-flight Pipeline execution?
  - MVP answer: keep only primitive attempt state in `StepExecution` (attempt number and next-run timestamp), keep scheduled tasks transient, and reschedule in `onResume()`.
- After pilot feedback, is there any recurring need for step-local `retryOn` and `skipOn` controls beyond what named custom profiles can express?

Resolved technical questions:

- Persisted custom profile settings already ship in V1. Custom message-pattern rule authoring is deferred to V2 so the pilot release stays scoped to built-in classifier rules, `disabledBuiltInRules`, and named custom profile allowlists.
