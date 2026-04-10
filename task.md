### Description

Define the `AgentTemplate` model — the per-VM-type configuration (base image name, labels, SSH credentials) — and implement `PrlDevopsSlave` extending `AbstractCloudSlave`, which represents a live cloned VM registered as a Jenkins node. Together these two classes form the data model for "what kind of VM to spin up and how to manage it".

> **MVP scope**: Retention is ONE_SHOT only — every VM is destroyed immediately after its build completes. No idle-timeout configuration is required in this release; that is deferred to a future iteration.

### User Story

As a **Jenkins administrator**, I want **to define one or more VM templates (e.g., macOS-Sonoma, Ubuntu-22)** so that **Jenkins can match queued jobs to the correct VM type and clone it on demand**.

### Acceptance Criteria

- [ ] **`AgentTemplate` model**: A `@DataBoundConstructor`-annotated class with at minimum:
  - `templateLabel` (String) — Jenkins node label used for job routing (e.g., `macos-sonoma`).
  - `baseVmName` (String) — name or catalog ID of the base VM in `prl-devops-service`.
  - `sshCredentialsId` (String) — Jenkins credentials ID for SSH into the cloned VM.
  - `remoteFs` (String) — working directory on the agent (e.g., `/Users/parallels/jenkins-agent`).
  - `numExecutors` (int, default 1).
  - *(No retention policy field — ONE_SHOT is the fixed, non-configurable strategy for MVP.)*
- [ ] **Template UI**: A `config.jelly` for `AgentTemplate` renders all fields; templates are added as a repeatable list inside `PrlDevopsCloud`'s config UI (using `<f:repeatableProperty>`).
- [ ] **Template matching**: `AgentTemplate.matches(Label label)` returns `true` when the template's labels satisfy the Jenkins `Label` expression.
- [ ] **`PrlDevopsSlave`**: Extends `AbstractCloudSlave`; constructed with a reference to its `AgentTemplate` and the cloned VM's ID and IP address.
  - `getNumExecutors()` delegates to the template.
  - `getLabelString()` delegates to the template.
  - `terminate()` is implemented as a stub (full logic in PRL-JNK-09).
- [ ] **Serialisation test**: `AgentTemplate` round-trips through Jenkins XML config without data loss (JenkinsRule).
- [ ] **Cloud-template wiring**: `PrlDevopsCloud` holds a `List<AgentTemplate>` and exposes a `getTemplateForLabel(Label)` helper.

### Definition of Done

- [x] Code implemented following best practices.
- [x] Unit tests written and passing.
- [x] Code reviewed and approved.
- [x] Merged into the main branch.
- [x] Documentation updated (if applicable).
- [x] Deployed to staging/production environment.

### Assumptions and Constraints

- **Assumption 1**: One template = one VM type. A single template will not clone different base images based on runtime conditions.
- **Assumption 2**: `numExecutors` defaults to 1 for all cloned VMs (one job per VM) to keep isolation clean; admins can override per template.
- **Constraint 1**: `PrlDevopsSlave.terminate()` must be a safe no-op stub in this PBI; it must not call the API until PRL-JNK-09 wires up the full lifecycle.
- **Constraint 2**: Template label expressions must support Jenkins' standard label syntax (AND, OR, NOT) via `Label.parseExpression()`.

### Dependencies

_No response_

### Additional Notes

### Template list in Cloud Jelly (illustrative)
```xml
<f:repeatableProperty field="templates" minimum="1">
  <f:entry title="">
    <div class="template-block">
      <!-- AgentTemplate fields rendered here -->
    </div>
  </f:entry>
</f:repeatableProperty>
```