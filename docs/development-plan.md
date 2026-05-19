# Smart Retry Development Plan

## 1. Purpose

This document describes how to deliver the Smart Retry MVP from the current plugin skeleton to a usable first release.

That first release should make retries safer by default: retry the failures worth retrying and fail fast on deterministic errors.

It complements the product requirements in [`mvp-prd.md`](./mvp-prd.md):

- PRD defines what the plugin should do
- this document defines when and in what order work should happen

## 2. Relationship to the PRD

This document should not conflict with the PRD.

Use this change order when updates are needed:

1. update the PRD if scope or behavior changes
2. update this development plan to reflect delivery order
3. update the implementation plan if technical design must change

If a conflict appears, the PRD wins on product behavior.

## 3. Delivery Strategy

The MVP should be delivered in small, testable increments:

1. replace the sample plugin scaffold with Smart Retry plugin identity
2. introduce the Pipeline step skeleton
3. implement classification and retry policy in isolation
4. wire the step execution loop
5. add global configuration
6. add build summary and observability
7. harden with tests and documentation

This sequence keeps risk low and gives working checkpoints early.

## 4. Milestones

## Milestone 0: Repository Cleanup

Goal:

- remove sample-plugin leftovers and rename the project clearly

Tasks:

- update `pom.xml` plugin name and metadata
- replace sample package naming
- remove `HelloWorldBuilder` scaffold
- clean up sample resources and tests
- update `README.md` to reflect Smart Retry direction

Exit criteria:

- repository builds cleanly
- no sample builder artifacts remain

## Milestone 1: Pipeline Step Skeleton

Goal:

- establish the public Smart Retry API shape

Tasks:

- add `SmartRetryStep`
- add `SmartRetryStepExecution`
- define step parameters:
  - `profile`
  - `maxRetries`
  - `backoff`
  - `initialDelaySeconds`
- register the Pipeline step function name as `smartRetry`
- add a minimal Pipeline integration test that invokes the step

Exit criteria:

- a Pipeline can call `smartRetry { ... }`
- the step runs the body once even before retry logic is implemented

## Milestone 2: Failure Classification Core

Goal:

- classify failures into stable internal categories

Tasks:

- define `FailureClassification`
- define `FailureType` enum
- implement `FailureClassifier`
- implement initial message-pattern rules
- implement initial exception-based rules
- add unit tests for:
  - `AGENT_LOST`
  - `SCM_TRANSIENT`
  - `NETWORK_TRANSIENT`
  - `ARTIFACT_REPO_TRANSIENT`
  - `IDENTITY_PROVIDER_TRANSIENT`
  - `PIPELINE_LOGIC_FAILURE`
  - `COMPILATION_FAILURE`
  - `UNKNOWN`

Exit criteria:

- classifier is testable without running a full Jenkins Pipeline
- non-retryable classes have higher precedence than retryable ones
- ambiguous network-style messages are classified conservatively and use surrounding context before falling into SCM-specific buckets
- Maven partial-download failures (`.jar.part`) are recognized as artifact-repository transient failures rather than generic unknowns
- generic `503/504` and `tls handshake timeout` signals require artifact-specific context before they are allowed into `ARTIFACT_REPO_TRANSIENT`
- generic 5xx matching should prefer explicit HTTP-style phrases instead of bare numeric status codes
- generic `broken pipe` log text alone should not be enough to classify a failure as retryable
- `connection refused` should require explicit external-service context before it is classified as `NETWORK_TRANSIENT`
- `connection reset` should require explicit external-service context before it is classified as `NETWORK_TRANSIENT`
- LDAP identity-provider retries should require narrow reauthentication-specific context rather than treating generic `401` responses as transient
- Git transport interruption signals such as `curl 56`, `unexpected disconnect`, `early EOF`, and `index-pack failed` should require explicit SCM context before they are classified as `SCM_TRANSIENT`
- high-confidence TypeScript, Go, and C/C++ compiler diagnostics should classify as `COMPILATION_FAILURE`, while generic wrapper failures such as bare `npm ERR!` should remain unmatched

## Milestone 3: Retry Policy and Backoff

Goal:

- convert classification results into retry decisions

Tasks:

- define `RetryPolicy`
- define `RetryDecision`
- add built-in profiles:
  - `conservative`
  - `infra`
- support named custom profiles
- implement fixed and exponential backoff
- add unit tests for profile behavior and attempt limits

Exit criteria:

- classification and policy are separate
- policy decisions are deterministic and easy to test

## Milestone 4: Step Execution Loop

Goal:

- make the Pipeline step actually retry according to policy

Tasks:

- capture body failures from the step execution
- classify the failure
- capture bounded console log context per attempt for classifier matching (to support `sh`-style failures)
- query policy
- wait according to backoff
- rerun until success or exhaustion
- emit console messages for each decision

Exit criteria:

- retry flow works in integration tests
- max retry handling is correct

## Milestone 5: Global Configuration

Goal:

- support central administration and Jenkinsfile overrides

Tasks:

- add `SmartRetryGlobalConfiguration`
- add built-in rule disablement by stable rule id
- define shared retry defaults and named custom profiles in global config
- defer constrained custom message patterns to follow-up work unless pilot feedback proves they are required for MVP adoption
- merge global defaults with step parameters
- add config round-trip tests

Exit criteria:

- Jenkins admins can define defaults
- Jenkins admins can selectively disable specific built-in retryable rules without disabling an entire failure type
- Jenkinsfile values override global defaults where intended
- named custom profiles resolve predictably and fail fast when a requested profile does not exist
- the shipped configuration surface stays small, explainable, and safe for pilot users

## Milestone 6: Build Summary UX

Goal:

- make plugin behavior visible and explainable

Tasks:

- add `SmartRetryRunAction`
- store attempt history:
  - attempt number
  - failure type
  - retry decision
  - delay
  - final outcome
- expose a simple build-page summary

Exit criteria:

- a build page shows whether Smart Retry was used
- users can inspect why retries happened

## Milestone 7: Stabilization

Goal:

- prepare for external use

Tasks:

- expand integration test coverage
- review null-safety and serialization concerns
- tighten log messages
- validate default rules against sample failure logs
- document known limitations

Exit criteria:

- plugin is stable enough for pilot users

## 5. Workstreams

The work naturally splits into these parallel-friendly streams:

- plugin cleanup and identity
- Pipeline step and execution
- classification engine
- configuration and UI
- test coverage and documentation

Recommended order:

- do not start UI-heavy work before classification and step behavior are stable
- keep classifier and policy independently testable

## 6. Initial Task Breakdown

### Track A: Skeleton Conversion

- rename plugin metadata
- remove sample builder code
- align package structure with `io.jenkins.plugins.smart_retry`

### Track B: Runtime Model

- define failure types
- define retry policy model
- define attempt history model

### Track C: Pipeline Execution

- implement the step descriptor
- implement execution callback flow
- connect retry loop to policy engine

### Track D: Admin and UI

- add global configuration
- add build action summary
- add concise Jelly views only where needed

### Track E: Testing

- unit tests for classifier and policy
- Pipeline integration tests
- config round-trip tests

## 7. Risks

### Risk 1: Pipeline step callback complexity

Why it matters:

- Jenkins Pipeline step execution has callback and serialization constraints

Mitigation:

- keep the first version simple
- use Jenkins Pipeline step patterns already common in existing plugins
- verify behavior with integration tests early

### Risk 2: Over-classification

Why it matters:

- too many rules too early can create false-positive retries

Mitigation:

- keep the MVP taxonomy small
- prefer `UNKNOWN` over risky guesses

### Risk 3: UX overreach

Why it matters:

- complex config and dashboards can slow down delivery without improving MVP value

Mitigation:

- ship minimal but explainable logs and build summary first

### Risk 4: Confusing scope around flaky tests

Why it matters:

- flaky test retry is attractive but distinct from infra retry

Mitigation:

- keep test flake logic out of the MVP

## 8. Definition of Done for MVP

The MVP is done when:

- `smartRetry { ... }` works in Pipeline
- `conservative` and `infra` profiles work
- transient infra failures can be retried
- compilation and logic failures are not retried
- console logs explain retry decisions
- build summary data is visible
- tests cover the main retry and non-retry paths
- docs are sufficient for pilot use

## 9. Suggested Timeline

This is a reasonable sequence for a focused implementation effort:

### Phase 1

- Milestone 0
- Milestone 1

### Phase 2

- Milestone 2
- Milestone 3

### Phase 3

- Milestone 4
- Milestone 5

### Phase 4

- Milestone 6
- Milestone 7

The exact calendar can change, but the dependency order should stay roughly the same.

## 10. Follow-up After MVP

After the first usable release:

- gather real-world failure samples
- refine message-pattern rules
- evaluate whether constrained global custom rules reduce the need for `retryOn` and `skipOn`
- evaluate whether flaky-test support deserves a separate follow-on design

If `retryOn` and `skipOn` are added later, they should be profile-level `FailureType` allow/block controls rather than a second raw-pattern system.
