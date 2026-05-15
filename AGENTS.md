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

## Repository map

- `src/main/java`: plugin runtime code
- `src/main/resources`: Jelly UI views and message bundles
- `src/test/java`: package-aligned tests
- `docs/mvp-prd.md`: product scope and behavior boundaries
- `docs/development-plan.md`: milestone sequencing and execution plan
- `docs/implementation-plan.md`: technical implementation design
- `docs/task-tracker.md`: active delivery tracking and status updates

## Working rules

- Preserve deterministic behavior. Do not add AI-based classification unless explicitly requested.
- Keep default behavior conservative: retry only high-confidence transient failures.
- Keep `UNKNOWN` failures non-retryable by default.
- Keep `COMPILATION_FAILURE`, `PIPELINE_LOGIC_FAILURE`, and `USER_ABORT` non-retryable by default.
- Keep deployment/release-style behavior out of default retry logic unless explicitly requested.
- Keep concerns separated:
  - classification logic in classifier classes
  - retry decision logic in policy classes
  - Pipeline execution flow in step execution classes
  - UI as presentation-only
- Keep logs explainable and stable so users can understand why retries did or did not happen.
- Prefer small focused classes, clear names, and minimal inline comments.
- Keep UI markup CSP-friendly:
  - no inline `<script>` or `<style>`
  - no inline event handlers
  - no `style=` attributes
  - prefer static assets under `src/main/webapp` when needed

## Testing expectations

- Add or update focused tests for every behavior change.
- Prefer unit tests for classification and policy logic, and integration tests for Pipeline step behavior.
- Validate both retry and non-retry paths.
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
  - finally update `docs/task-tracker.md`
- For dependency changes in `pom.xml`, stay aligned with the Jenkins plugin parent and BOM.
- Avoid relying on undeclared transitive plugin dependencies.
- Keep plugin step behavior Pipeline-first unless a Freestyle requirement is explicitly added.

## Documentation rules

- Keep responsibilities separated across docs:
  - `README.md` is concise and user-facing
  - `docs/mvp-prd.md` defines product intent and boundaries
  - `docs/development-plan.md` defines delivery order and milestones
  - `docs/implementation-plan.md` defines code architecture and implementation details
  - `docs/task-tracker.md` tracks active execution status
- Keep status updates checklist-driven in `docs/task-tracker.md` instead of embedding long status prose in other docs.
- Prefer `current implementation`, `V1`, and `V2` wording over vague roadmap language.
