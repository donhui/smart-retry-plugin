# Smart Retry

> A conservative, explainable retry step for transient Jenkins infrastructure failures.

## Introduction

Smart Retry is a Jenkins Pipeline plugin focused on one problem: some build failures are caused by transient infrastructure issues, and blindly rerunning everything is wasteful and risky.

The plugin is intended to retry failures that look like:

- lost agents or evicted Kubernetes pods
- temporary SCM checkout failures
- short-lived network instability
- transient artifact repository outages

The MVP is designed to stay conservative by default:

- retry likely infrastructure failures
- do not retry compilation failures by default
- do not retry Pipeline logic failures by default
- make retry decisions visible in logs and build UI

## Pipeline Usage

```groovy
smartRetry(profile: 'infra', maxRetries: 2, backoff: 'exponential') {
  sh 'mvn -B test'
}
```

## Project Docs

- [MVP PRD](./docs/mvp-prd.md)
- [Development Plan](./docs/development-plan.md)
- [Implementation Plan](./docs/implementation-plan.md)
- [Task Tracker](./docs/task-tracker.md)

## Status

This repository now contains a working V1 implementation of the `smartRetry` Pipeline step.

Current implementation highlights:

- executes the body block and retries according to deterministic policy decisions
- supports step-level overrides for `profile`, `maxRetries`, `backoff`, and `initialDelaySeconds`
- ships fixed built-in profiles (`conservative`, `infra`) plus named custom profiles
- records attempt history on the build page through a dedicated Smart Retry run action
- exposes a Jenkins documentation page that explains failure types and matched rules
- supports Jenkins global configuration for the default profile, shared retry defaults, named custom profiles, built-in rule disabling, and console-log context depth used during classification

Note: some failures (especially from `sh`) put the most useful error text only in console output. Smart Retry uses a bounded, attempt-scoped fragment of the build log as additional classification context in these cases.

Current V1 classification is intentionally narrower than the full taxonomy. The classifier actively emits high-confidence transient infrastructure categories, explicit `PIPELINE_LOGIC_FAILURE`, `COMPILATION_FAILURE`, and `TEST_ASSERTION_FAILURE` matches, plus `USER_ABORT` and `UNKNOWN`; some other taxonomy entries are still reserved for future explicit rules rather than guessed today.

## Contributing

Refer to the Jenkins project [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md).

## License

Licensed under MIT, see [LICENSE.md](./LICENSE.md).
