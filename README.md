# Smart Retry

> Retry only the failures worth retrying.

`smartRetry` helps Jenkins avoid blind retries by retrying high-confidence transient failures and failing fast on deterministic errors.

Good fit for:

- lost agents or evicted Kubernetes pods
- temporary Git / SCM failures
- short-lived network or artifact repository outages

Fails fast on:

- compilation failures
- Pipeline logic errors
- stable test failures
- unknown failures without a high-confidence match

## Quick Start

Use the default conservative behavior:

```groovy
smartRetry {
  sh 'mvn -B test'
}
```

Use the broader `infra` profile when your CI environment is more dependent on external services:

```groovy
smartRetry(profile: 'infra', maxRetries: 2, backoff: 'exponential') {
  sh 'mvn -B test'
}
```

Available step parameters:

- `profile`
- `maxRetries`
- `backoff`
- `initialDelaySeconds`

## Install

Install the plugin in Jenkins, then restart Jenkins if your controller asks for it.

After installation:

1. Open `Manage Jenkins` -> `System`.
2. Find the `Smart Retry` section.
3. Choose a default profile and shared retry defaults.
4. Save the configuration.

## Choose A Profile

`conservative`

- best default for first rollout
- retries only the narrowest high-confidence infrastructure failures
- currently focuses on `AGENT_LOST` and `SCM_TRANSIENT`

`infra`

- better for cloud CI environments with more external dependency volatility
- also retries `NETWORK_TRANSIENT`, `ARTIFACT_REPO_TRANSIENT`, and `IDENTITY_PROVIDER_TRANSIENT`

named custom profiles

- defined by Jenkins administrators in global configuration
- let teams opt into a controlled allowlist of retryable `FailureType` values

## Common Examples

Retry only the narrowest infrastructure failures:

```groovy
smartRetry {
  git branch: 'main',
      credentialsId: 'scm-creds',
      url: 'https://gitlab.example.com/your-group/your-repo.git'
}
```

Retry a build step with broader infra coverage:

```groovy
smartRetry(profile: 'infra', maxRetries: 2, backoff: 'fixed', initialDelaySeconds: 15) {
  sh 'mvn -B verify'
}
```

Use a team-specific custom profile defined by an administrator:

```groovy
smartRetry(profile: 'release') {
  sh './gradlew publishToMavenLocal'
}
```

## Complete Pipeline Example

Declarative Pipeline:

```groovy
pipeline {
  agent any

  stages {
    stage('Checkout') {
      steps {
        smartRetry {
          git branch: 'main',
              credentialsId: 'scm-creds',
              url: 'https://gitlab.example.com/your-group/your-repo.git'
        }
      }
    }

    stage('Build') {
      steps {
        smartRetry(profile: 'infra', maxRetries: 2, backoff: 'fixed', initialDelaySeconds: 15) {
          sh 'mvn -B verify'
        }
      }
    }
  }
}
```

This is a good starting pattern:

- use `conservative` or the default profile around checkout-like steps
- use `infra` only where broader transient dependency failures are common
- keep the wrapped block as small and idempotent as practical

## What You See In Builds

Smart Retry is designed to stay explainable.

In the console log, it prints:

- the start of each attempt
- the classified failure type
- whether it will retry or stop
- the reason for that decision
- retry scheduling details
- a final success or final failure summary line

Each build also gets a dedicated Smart Retry page that shows:

- the active profile
- recorded retry-triggering attempts
- the matched rule for the last recorded failure
- whether the build recovered or failed

## Jenkins Configuration

The plugin supports Jenkins global configuration for:

- default profile selection
- shared retry defaults
- named custom profiles
- built-in rule disabling by stable rule id
- bounded console-log context depth used during classification

Note: some steps, especially `sh`, often put the most useful error text only in console output. Smart Retry can use a bounded, attempt-scoped fragment of the build log as additional classification context.

## When To Use It

Smart Retry works best around idempotent CI steps such as:

- checkout and dependency download
- build and test stages affected by flaky infrastructure
- container/image pulls
- calls to external services that sometimes fail transiently

It is not meant to be a blanket replacement for every retry in every pipeline.

Avoid wrapping steps that are not safe to run again unless you are confident they are idempotent.

## Troubleshooting

If Smart Retry did not retry when you expected:

- check the console log for the classified `FailureType` and final reason
- confirm the selected profile allows that `FailureType`
- confirm the failure text is present in the exception or recent console output

If Smart Retry retried too broadly:

- switch from `infra` to `conservative`
- narrow behavior with a named custom profile
- disable a built-in retryable rule by stable rule id in global configuration

## FAQ

### 1. Does Smart Retry replace Jenkins `retry {}`?

Not exactly. The two steps solve different problems.

Jenkins `retry {}` is unconditional: if the wrapped block fails, Jenkins reruns it without asking why it failed.

Smart Retry is failure-aware: it first classifies the failure, then retries only when the active profile allows that `FailureType`.

In practice:

- use `retry {}` when you already know a step is safe to rerun blindly
- use `smartRetry {}` when you want safer defaults and do not want compilation failures, test failures, or Pipeline logic errors retried automatically

Example:

```groovy
retry(2) {
  sh 'mvn -B test'
}
```

This retries on any failure, including deterministic ones.

```groovy
smartRetry(profile: 'infra', maxRetries: 2) {
  sh 'mvn -B test'
}
```

This retries only when the failure matches a high-confidence transient category that the selected profile allows.

### 2. Should I wrap an entire pipeline stage?

Usually no. It is better to wrap the smallest idempotent block that is most exposed to transient infrastructure failures.

### 3. Why was a failure not retried?

The most common reasons are:

- the failure was classified as a non-retryable `FailureType`
- the selected profile does not allow retries for that `FailureType`
- retry attempts were already exhausted
- the failure did not match a high-confidence rule and stayed `UNKNOWN`

### 4. Can I tune behavior for different teams?

Yes. Administrators can define named custom profiles in Jenkins global configuration and let teams opt into them from their Jenkinsfiles.

## Learn More

- [MVP PRD](./docs/mvp-prd.md)
- [Development Plan](./docs/development-plan.md)
- [Implementation Plan](./docs/implementation-plan.md)
- [Task Tracker](./docs/task-tracker.md)

## Contributing

Refer to the Jenkins project [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md).

## License

Licensed under MIT, see [LICENSE.md](./LICENSE.md).
