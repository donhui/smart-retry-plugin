# AGENTS.md

This file applies to the entire repository.

## Project summary

- This repository contains the Jenkins plugin `smart-retry`.
- The plugin provides a Pipeline-first `smartRetry` step for transient CI infrastructure failures.
- The plugin is intentionally conservative and deterministic: no AI-based classification and no broad governance scope.

## Tech stack

- Java / JDK: 17+
- Maven: 3.9.6+ with Jenkins plugin packaging (`hpi`)
- Jenkins plugin parent + BOM from `pom.xml`
- Test framework: JUnit 5
- UI layer: Jelly views under `src/main/resources`
- Java package root: `io.jenkins.plugins.smart_retry`

## Repository map

- `src/main/java`: plugin runtime code
- `src/main/resources`: Jelly UI views and message bundles
- `src/test/java`: package-aligned tests
- `docs/mvp-prd.md`: product scope and behavior boundaries
- `docs/development-plan.md`: milestone sequencing
- `docs/implementation-plan.md`: technical implementation design

## Working rules

- Preserve deterministic behavior. Do not add AI-based classification unless explicitly requested.
- Keep default behavior conservative: retry only high-confidence transient failures.
- Keep `UNKNOWN` failures non-retryable by default.
- Keep `COMPILATION_FAILURE`, `PIPELINE_LOGIC_FAILURE`, and `USER_ABORT` non-retryable by default.
- Keep deployment/release-style behavior out of default retry logic unless explicitly requested.
- Keep implementation classes under `io.jenkins.plugins.smart_retry`; do not place plugin implementation directly under `io.jenkins.plugins`.
- Keep concerns separated: classification logic in classifier classes, retry decisions in policy classes, Pipeline flow in step execution classes, and UI as presentation-only.
- Prefer Jenkins-native binding and configuration patterns: use `StaplerRequest2` in new Stapler-bound code, use `@DataBoundConstructor` and `@DataBoundSetter` consistently, and keep `@Symbol` names stable and Pipeline-friendly.
- When adding mutating web endpoints, require `@POST` and enforce the least-powerful Jenkins permission that fits the action.
- Treat every Stapler web endpoint (`doCheck*`, `doFill*`, `doTest*`, `configure`, and similar HTTP-reachable methods) as security-sensitive by default: add an explicit least-privilege Jenkins permission check in the method body.
- For Stapler form validation and form fill endpoints, prefer explicit `@POST` as well when compatible with Jenkins UX and current Jenkins security scanning expectations.
- When adding or changing Stapler endpoints, add or update focused tests that verify the expected security annotation and/or permission gate.
- Keep logs explainable and stable so users can understand why retries did or did not happen.
- Send build-user diagnostics to `TaskListener`, form validation, or Jenkins logging as appropriate; do not use `System.out.println`.
- Prefer small focused classes, clear names, and minimal inline comments.
- Keep UI and Jelly Jenkins-native and CSP-friendly: no inline `<script>`, `<style>`, event handlers, or `style=` attributes; prefer static assets under `src/main/webapp`, keep `<?jelly escape-by-default='true'?>`, and keep Jelly `field=` bindings, Java getters/setters, and `help-*.html` files in sync.
- Prefer Jenkins form taglibs and built-in controls over custom UI widgets.
- When configuration exists at both global and step scope, keep override precedence explicit and test both defaulted and overridden paths.

## Testing expectations

- Add or update focused tests for every behavior change.
- Prefer unit tests for classification and policy logic, and integration tests for Pipeline step behavior.
- Validate both retry and non-retry paths.
- Use JenkinsRule / Jenkins test harness for Pipeline step behavior, descriptor validation, Jelly rendering, snippetizer-facing changes, and agent/workspace/remoting scenarios.
- For implementation changes, run `mvn spotless:apply` before finishing the task.
- Use smallest relevant test commands first, then broader coverage when needed:
  - `mvn -Dtest=... test`
  - `mvn test`

## Change guidance

- Keep failure taxonomy aligned with `docs/mvp-prd.md`.
- If behavior scope changes:
  - update `docs/mvp-prd.md` first
  - then update `docs/development-plan.md`
  - then update `docs/implementation-plan.md`
- For dependency changes in `pom.xml`, stay aligned with the Jenkins plugin parent and BOM.
- Keep `pom.xml` minimal and avoid re-declaring parent-provided defaults unless the repository intentionally overrides them.
- Avoid relying on undeclared transitive plugin dependencies.
- Keep plugin step behavior Pipeline-first unless a Freestyle requirement is explicitly added.

## Documentation rules

- Keep documentation responsibilities separated: `README.md` stays concise and user-facing, `docs/mvp-prd.md` defines product boundaries, `docs/development-plan.md` defines delivery order, and `docs/implementation-plan.md` defines code architecture.
- Prefer `current implementation`, `V1`, and `V2` wording over vague roadmap language.
- Do not duplicate content across documents. Each decision, rule, or list belongs in exactly one authoritative file; other files reference it instead of repeating it.
- `README.md` is a user manual only. Keep product-scope definitions, failure taxonomy, and design rationale in `docs/mvp-prd.md`, not in `README.md`.
- `docs/development-plan.md` describes milestone goals and exit criteria. Do not duplicate implementation architecture details into it.
- `docs/implementation-plan.md` describes active architecture. Compress or remove sections that describe completed one-time steps with no ongoing reference value.
