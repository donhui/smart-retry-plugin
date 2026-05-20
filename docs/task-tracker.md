# Smart Retry Task Tracker

## Purpose

This document is the working tracker for active delivery.

It is intentionally separate from:

- [`mvp-prd.md`](./mvp-prd.md): product scope and behavior
- [`development-plan.md`](./development-plan.md): milestone and sequencing plan
- [`implementation-plan.md`](./implementation-plan.md): technical design and code structure

Update this file frequently as implementation moves forward.

## Status Legend

- `[ ]` not started
- `[~]` in progress
- `[x]` done
- `[!]` blocked

## Current Focus

- Current milestone: `Milestone 7 - Stabilization`
- Current objective: keep the pilot-ready Smart Retry experience stable, documented, and easy to adopt

## Current Decisions

- Smart Retry is Pipeline-first for the MVP
- The public Pipeline step name should be `smartRetry`
- The user-facing product message should stay centered on avoiding blind retries and retrying only failures worth retrying
- The default retry profile should be `conservative`
- The current global configuration surface now includes `defaultProfile`, `consoleContextLines`, shared retry defaults, named custom profiles, and `disabledBuiltInRules`
- `conservative` and `infra` are fixed built-in profiles, while custom profiles are named allowlists over the retryable transient `FailureType` values
- Unknown profile names should fail fast instead of silently falling back to `conservative`
- Future custom rules should be additive and safety-bounded: built-in hard-stop behavior stays authoritative
- Future configuration should also support `disabledBuiltInRules` so operators can turn off one built-in retryable rule without disabling the whole `FailureType`
- Future custom retryable rules should only target `AGENT_LOST`, `SCM_TRANSIENT`, `NETWORK_TRANSIENT`, `ARTIFACT_REPO_TRANSIENT`, or `IDENTITY_PROVIDER_TRANSIENT`
- Constrained custom classification-rule authoring is deferred to post-MVP V2 so the pilot release stays scoped to built-in classifier rules, `disabledBuiltInRules`, and named custom profiles
- Future `retryOn` and `skipOn` controls should be profile-level `FailureType` policy settings, not raw text matching
- Named custom profiles should remain the primary MVP policy mechanism; `retryOn` and `skipOn` are follow-up options only if pilot feedback shows a real need for step-local policy deltas
- If `retryOn` and `skipOn` are added later, they should apply as deltas on top of the selected profile allowlist rather than bypassing profile resolution
- Compilation failures are non-retryable by default, including explicit Gradle compile-task failures with deterministic `Compilation failed/error` output
- Gradle toolchain provisioning or dependency-resolution failures around `compile*` tasks should remain `UNKNOWN` unless a narrower built-in transient rule matches explicit network/service signals
- High-confidence test assertion exception types and test-runner failure/error summaries, including pytest and Gradle failure summaries, should classify as `TEST_ASSERTION_FAILURE`
- Explicit compilation-failure wording, `cannot find symbol`, and high-confidence TypeScript, Go, and C/C++ compiler diagnostics should classify as `COMPILATION_FAILURE`
- Generic wrapper failures such as bare `npm ERR!` should remain `UNKNOWN` unless the log also contains a high-confidence compiler diagnostic
- `UNKNOWN` failures are non-retryable by default
- Missing DSL methods, missing properties, script-security rejections, and WorkflowScript/Jenkinsfile startup failures should classify as `PIPELINE_LOGIC_FAILURE`
- Generic `could not resolve host` signals should classify as `NETWORK_TRANSIENT` unless explicit SCM context is present
- Maven `.jar.part` partial-download failures should classify as `ARTIFACT_REPO_TRANSIENT`
- Generic `503/504` and `tls handshake timeout` signals should classify as `NETWORK_TRANSIENT` unless explicit artifact-repository context is present
- Generic 5xx matching should prefer explicit HTTP-style phrases rather than bare numeric status codes
- `connection refused` should classify as `NETWORK_TRANSIENT` only when explicit external-service context is present
- `connection reset` should classify as `NETWORK_TRANSIENT` only when explicit external-service context is present
- Generic `broken pipe` log text alone should stay non-retryable unless exception context makes the transport failure explicit
- LDAP reauthentication failures should use a dedicated `IDENTITY_PROVIDER_TRANSIENT` class, while generic `401` responses remain non-retryable
- Git transport interruption failures should classify as `SCM_TRANSIENT` only when explicit clone/fetch/checkout context is present
- The initial target package should be `io.jenkins.plugins.smart_retry`

## Milestone Tracker

### Milestone 0: Repository Cleanup

- [x] Update plugin name and metadata in `pom.xml`
- [x] Replace sample package naming with Smart Retry package naming
- [x] Remove `HelloWorldBuilder` sample code
- [x] Remove sample resources tied to `HelloWorldBuilder`
- [x] Replace sample tests with Smart Retry-oriented tests
- [x] Update `README.md` to describe Smart Retry
- [x] Confirm repository builds cleanly after sample cleanup

### Milestone 1: Pipeline Step Skeleton

- [x] Add `SmartRetryStep`
- [x] Add `SmartRetryStepExecution`
- [x] Define initial step parameters
- [x] Register Pipeline function name `smartRetry`
- [x] Add a minimal Pipeline integration test for body execution

### Milestone 2: Failure Classification Core

- [x] Add `FailureType`
- [x] Add `FailureClassification`
- [x] Add `FailureClassifier`
- [x] Implement exception-based classification rules
- [x] Implement message-pattern classification rules
- [x] Add unit tests for initial failure taxonomy

### Milestone 3: Retry Policy and Backoff

- [x] Add `RetryPolicy`
- [x] Add `RetryDecision`
- [x] Add built-in profiles: `conservative`, `infra`
- [x] Add named custom profiles
- [x] Add fixed backoff support
- [x] Add exponential backoff support
- [x] Add unit tests for retry policy behavior

### Milestone 4: Step Execution Loop

- [x] Wire classifier into step execution
- [x] Wire retry policy into step execution
- [x] Implement retry attempt loop
- [x] Add console logging for retry decisions
- [x] Add integration tests for retry success and retry exhaustion

### Milestone 5: Global Configuration

- [x] Add `SmartRetryGlobalConfiguration`
- [x] Add default profile configuration
- [x] Confirm constrained custom classification rules are post-MVP V2 follow-up work
- [x] Merge global defaults with step-level overrides
- [x] Add configuration round-trip tests

### Milestone 6: Build Summary UX

- [x] Add `SmartRetryRunAction`
- [x] Add attempt history model
- [x] Add build summary view
- [x] Expose retry reasons and final outcome

### Milestone 7: Stabilization

- [x] Expand test coverage
- [x] Tighten log messages
- [x] Validate initial rules against sample failure messages
- [x] Document known limitations
- [x] Prepare pilot-ready README/docs pass
- [x] Polish Snippet Generator step configuration and defaults behavior

## Immediate Next Slice

Recommended next coding slice:

1. Collect pilot feedback on profile usability, classifier gaps, and whether any additional high-confidence transient rules are still needed for V1
2. Validate whether built-in classifier coverage plus named custom profiles leave any repeatable gaps that justify constrained custom classification rules in V2
3. Keep `retryOn` and `skipOn` out of the MVP unless pilot users show a repeatable need for step-local policy deltas beyond named custom profiles

## Open Questions

- Is the default `consoleContextLines = 200` a good balance between classifier signal and noisy log capture for typical Pipeline failures?
- Should disabled built-in rules apply only to retryable message rules in the first version, or is there a safe case for allowing some non-retryable message rules to be suppressed too?

## Blockers

- None at the moment

## Change Log

### 2026-05-12

- [x] Added product requirements document at `docs/mvp-prd.md`
- [x] Added development plan at `docs/development-plan.md`
- [x] Added implementation plan at `docs/implementation-plan.md`
- [x] Initialized delivery tracker at `docs/task-tracker.md`
- [x] Removed generated sample plugin code and resources
- [x] Added initial model classes for failure classification and retry decisions
- [x] Added the initial `smartRetry` Pipeline body step skeleton and integration coverage
- [x] Added attempt-scoped console log context capture for `sh`-style failures
- [x] Synced README and design docs with the current V1 implementation boundary and remaining stabilization work

### 2026-05-13

- [x] Added explicit classifier rules for `PIPELINE_LOGIC_FAILURE` and broadened `COMPILATION_FAILURE` coverage for Java, TypeScript, Go, and C/C++ diagnostics
- [x] Added focused classifier coverage for deterministic non-retryable Pipeline and compiler failures, while keeping bare wrapper errors such as `npm ERR!` unmatched
- [x] Added conservative `TEST_ASSERTION_FAILURE` rules for explicit assertion exceptions and test-runner summaries
- [x] Prioritized remoting channel closure and agent offline initialization failures into `AGENT_LOST` instead of generic `EOFException` network handling
- [x] Expanded `AGENT_LOST` coverage for common Kubernetes pod deleted/not-found and eviction/resource-pressure signals
- [x] Removed `TOOLCHAIN_TRANSIENT` from the active V1 taxonomy and built-in profile surface
- [x] Captured a constrained additive design for future custom rules so built-in hard-stop semantics remain protected
- [x] Clarified that future `retryOn` and `skipOn` should operate on classified `FailureType` values rather than raw error text
- [x] Added a design for `disabledBuiltInRules` so operators can suppress one built-in rule without disabling an entire `FailureType`

### 2026-05-14

- [x] Added deterministic `COMPILATION_FAILURE` rules for TypeScript compiler diagnostics such as `error TS2322:`
- [x] Added deterministic `COMPILATION_FAILURE` rules for Go compiler diagnostics such as `file.go:line:column: undefined:`
- [x] Added deterministic `COMPILATION_FAILURE` rules for C/C++ compiler and linker diagnostics such as file-scoped `fatal error:` and `undefined reference to`
- [x] Added deterministic `COMPILATION_FAILURE` rules for high-confidence Java and Kotlin compiler diagnostics while keeping Gradle toolchain provisioning failures unmatched
- [x] Added a conservative `TEST_ASSERTION_FAILURE` rule for pytest short summaries and terminal failure totals
- [x] Added conservative Gradle rules for compile-task `Compilation failed/error` output and test-task failure summaries
- [x] Kept generic wrapper failures such as bare `npm ERR! code ELIFECYCLE` unmatched so non-compiler build wrappers do not get overclassified
- [x] Synced classifier docs and tracker notes with the expanded non-retryable compilation coverage
- [x] Verified the expanded classifier coverage with focused classifier tests, `mvn spotless:apply`, and full `mvn test`
- [x] Refactored profile configuration to fixed built-ins plus multiple named custom profiles with shared retry defaults
- [x] Made unknown profile names fail fast during step startup instead of silently falling back
- [x] Added focused configuration and Pipeline coverage for named custom profiles and unknown-profile failures

### 2026-05-15

- [x] Simplified the global Smart Retry system configuration UI by restructuring the page around a default-profile-first flow, moving operator-only settings into `Advanced`, converting default profile selection to a constrained dropdown, upgrading custom profile failure-type selection from free-text to checkboxes, removing redundant help/add-button clutter, and adding focused configuration coverage for the updated form behavior

### 2026-05-18

- [x] Completed explicit system-configuration form validation by adding field-level checks for custom profile names and numeric settings, enforcing duplicate-name/reserved-name/empty-selection failures during custom profile submission, and extending focused configuration tests for the new validation paths

### 2026-05-19

- [x] Ensured direct-success Smart Retry runs still create a build-page run action and record a terminal success outcome
- [x] Corrected build-page success narratives so retry-then-success runs no longer present the last failure as a terminal failure summary
- [x] Added restart recovery coverage for `onResume()` and delayed retry rescheduling across Jenkins restarts
- [x] Added curated classifier corpus tests with representative transient, deterministic, and conservative-negative sample logs
- [x] Clarified that named custom profiles remain the primary MVP policy mechanism and that any future `retryOn` and `skipOn` controls should be profile-relative `FailureType` deltas rather than raw-pattern overrides

### 2026-05-20

- [x] Polished `smartRetry` authoring UX in Snippet Generator by adding a dedicated step form, field help, and descriptor-backed dropdowns/validation for built-in and custom profile selection
- [x] Fixed default-value handling for optional step arguments so blank `profile` and `backoff` selections no longer persist as empty-string overrides when Jenkins should use global defaults
- [x] Added focused step-configuration coverage and verified the Snippet Generator-related behavior, including the real `pipeline-syntax/generateSnippet` endpoint, with `mvn spotless:apply` and `mvn -Dtest=SmartRetryStepTest test`
- [x] Closed the remaining MVP documentation evaluation item by deciding that constrained custom classification-rule authoring is post-MVP V2 work rather than part of the pilot V1 line
