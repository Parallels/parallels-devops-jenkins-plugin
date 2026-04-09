### Description

Implement the `PrlDevopsCloud` class that extends `hudson.slaves.Cloud` — the top-level Jenkins extension point for a Cloud provider. This class stores the global connection settings (DevOps Service URL, API token, connection mode) and exposes them through a Jelly-based configuration UI in Jenkins → Manage Jenkins → Clouds.

### User Story

As a **Jenkins administrator**, I want **to configure a Parallels DevOps Service connection in Jenkins' system settings** so that **Jenkins can dynamically provision Parallels VMs as build agents without any manual VM management**.

### Acceptance Criteria

- [x] **Class structure**: `PrlDevopsCloud` extends `hudson.slaves.Cloud` and is annotated with `@Extension` so Jenkins discovers it automatically.
- [x] **Fields persisted**: The following fields are stored (using Jenkins' `@DataBoundConstructor` / `@DataBoundSetter` pattern) and survive Jenkins restarts:
  - `serviceUrl` (String) — base URL of prl-devops-service.
  - `credentialsId` (String) — references a Jenkins `StringCredentials` or `UsernamePasswordCredentials` for the API token.
  - `connectionMode` (enum: `HOST` / `ORCHESTRATOR`).
  - `maxAgents` (int) — upper bound on simultaneously running VMs from this cloud.
- [x] **Jelly UI** (`config.jelly`): Renders form fields for all of the above in the Jenkins Clouds configuration page; uses standard Jenkins Jelly tags (`<f:textbox>`, `<f:select>`, `<f:credentialsSelect>`).
- [x] **Credentials binding**: API token is read via `CredentialsProvider.findById` — never stored in plain text in config.xml.
- [x] **Descriptor validation**: `DescriptorImpl` implements `doTestConnection()` form validation that calls `GET /api/v1/health` (or equivalent) and returns a green `FormValidation.ok("Connected")` or red error.
- [x] **`canProvision(Label label)`**: Returns `true` if a matching `AgentTemplate` exists for the given label; `false` otherwise.
- [x] **`provision()` stub**: Method signature is implemented and delegates to the template/provisioner (full logic covered in PRL-JNK-07); returns empty list for now.
- [x] **Unit tests**: `PrlDevopsCloud` can be instantiated, serialised to `config.xml`, and deserialised back with identical field values using `JenkinsRule`.

### Definition of Done

- [x] Code implemented following best practices.
- [x] Unit tests written and passing.
- [x] Code reviewed and approved.
- [x] Merged into the main branch.
- [x] Documentation updated (if applicable).
- [x] Deployed to staging/production environment.

### Assumptions and Constraints

- **Assumption 1**: Jenkins Credentials Plugin is already a declared `pom.xml` dependency.
- **Assumption 2**: The `Test Connection` button hits a known health/ping endpoint on the DevOps service — this endpoint must be confirmed.
- **Constraint 1**: No provisioning logic in this PBI; `provision()` is a stub only.
- **Constraint 2**: Fields must be `@DataBoundConstructor`-compatible so Jenkins can round-trip them through its configuration save/load cycle.


### Dependencies

_No response_

### Additional Notes

### Jelly UI snippet (illustrative)
```xml
<f:entry title="Service URL" field="serviceUrl">
  <f:textbox />
</f:entry>
<f:entry title="API Credentials" field="credentialsId">
  <c:select />
</f:entry>
<f:entry title="Connection Mode" field="connectionMode">
  <f:select />
</f:entry>
<f:entry title="Max Concurrent Agents" field="maxAgents">
  <f:number min="1" max="50" />
</f:entry>
<f:validateButton title="Test Connection" method="testConnection"
  with="serviceUrl,credentialsId" />