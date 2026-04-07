# API Client Implementation Approach

> **Status**: Analysis & Design — no code written yet.

---

## 1. What the API Actually Looks Like (jq findings)

All endpoints live under `/api/v1/`. Bearer token is passed as `Authorization: Bearer <token>`.

### Host Mode — the three operations we need

| Operation | Method | Path |
|---|---|---|
| Clone VM | `PUT` | `/api/v1/machines/{id}/clone` |
| Get VM status | `GET` | `/api/v1/machines/{id}/status` |
| Get full VM detail | `GET` | `/api/v1/machines/{id}` |
| Delete VM | `DELETE` | `/api/v1/machines/{id}` |

> **Correction from task.md**: The task listed `POST /api/v1/machines/clone`.  
> The Postman collection confirms it is `PUT /api/v1/machines/{id}/clone` —  
> i.e. you clone **an existing** source VM by its ID, not a standalone create. The clone request body carries optional `clone_name` and `destination_path`.

### Orchestrator Mode — same operations, different path prefix

In orchestrator mode every machine endpoint is scoped under a **host**:

| Operation | Method | Path |
|---|---|---|
| Clone VM | `PUT` | `/api/v1/orchestrator/hosts/{hostId}/machines/{vmId}/clone` |
| Get VM status | `GET` | `/api/v1/orchestrator/hosts/{hostId}/machines/{vmId}/status` |
| Get full VM detail | `GET` | `/api/v1/orchestrator/hosts/{hostId}/machines/{vmId}` |
| Delete VM | `DELETE` | `/api/v1/orchestrator/hosts/{hostId}/machines/{vmId}` |

This means `ConnectionMode.ORCHESTRATOR` needs a `hostId` at construction time in addition to the base URL.

---

## 2. Confirmed DTO Shapes (from Postman response examples)

### `CloneRequest` (request body for PUT clone)
```json
{
  "clone_name": "<string>",
  "destination_path": "<string>"
}
```
Both fields are optional in the API; `destination_path` will default to `null` / omitted.

### `CloneResponse` (200 OK from PUT clone)
```json
{
  "id":     "<string>",
  "status": "<string>",
  "error":  "<string>"
}
```
The `id` is the **new** VM's ID. `status` at clone time is likely an operation status (not the running state). `error` is non-empty on partial failure.

### `VmStatusResponse` (200 OK from GET status)
```json
{
  "id":            "<string>",
  "ip_configured": "<string>",
  "status":        "<string>"
}
```
The `status` string is what the polling loop inspects. Observed values from the service source need to be confirmed, but the task state machine defines: `pending → starting → running → error`.

### `VmDetail` (200 OK from GET machines/{id}) — subset we care about
```json
{
  "ID":    "<string>",
  "Name":  "<string>",
  "State": "<string>",
  "host_id": "<string>",
  "host_url": "<string>",
  "internal_ip_address": "<string>"
}
```
`VmDetail` is only needed if richer data is required beyond what the `/status` endpoint returns.

### Error response (4xx / 5xx)
```json
{
  "code":    123,
  "message": "<string>",
  "stack": [
    { "code": 0, "description": "<string>", "error": "<string>", "path": "<string>" }
  ]
}
```

---

## 3. Package & Class Layout

```
com.parallels.jenkins/
  api/
    PrlDevopsApiClient.java          ← interface
    PrlDevopsHttpClient.java         ← JDK HttpClient implementation
    ConnectionMode.java              ← enum: HOST | ORCHESTRATOR
    dto/
      CloneRequest.java
      CloneResponse.java
      VmStatusResponse.java
      ApiErrorResponse.java          ← for deserialising error bodies
    exception/
      PrlApiException.java           ← wraps HTTP status + error body
      PrlApiTimeoutException.java    ← thrown by waitForVmReady on timeout
```

---

## 4. Interface Design

```java
public interface PrlDevopsApiClient {

    /** Clone an existing VM identified by sourceVmId. */
    CloneResponse cloneVm(String sourceVmId, CloneRequest request) throws PrlApiException;

    /** Returns the lightweight status for vmId. */
    VmStatusResponse getVmStatus(String vmId) throws PrlApiException;

    /** Deletes vmId; returns void (API returns 202 Accepted, no body). */
    void deleteVm(String vmId) throws PrlApiException;

    /**
     * Polls getVmStatus until status == RUNNING or timeout expires.
     * Throws PrlApiException immediately on ERROR status.
     * Throws PrlApiTimeoutException if timeout passes without RUNNING.
     */
    VmStatusResponse waitForVmReady(String vmId, Duration timeout, Duration interval)
            throws PrlApiException, PrlApiTimeoutException;
}
```

> **Note on `cloneVm` signature**: The task.md interface shows `cloneVm(CloneRequest)` but the actual API path is `PUT /machines/{id}/clone`, so `sourceVmId` must be a separate parameter (or embedded inside `CloneRequest`). To keep `CloneRequest` as a pure body DTO, `sourceVmId` is a separate argument here.

---

## 5. URL Building Strategy

`PrlDevopsHttpClient` is constructed with:
- `baseUrl` — e.g. `https://host:8080` (no trailing slash)
- `ConnectionMode mode`
- `String hostId` — only used (and required) when `mode == ORCHESTRATOR`
- `String bearerToken`

Path building:
```
HOST mode:
  /api/v1/machines/{vmId}/clone
  /api/v1/machines/{vmId}/status
  /api/v1/machines/{vmId}

ORCHESTRATOR mode:
  /api/v1/orchestrator/hosts/{hostId}/machines/{vmId}/clone
  /api/v1/orchestrator/hosts/{hostId}/machines/{vmId}/status
  /api/v1/orchestrator/hosts/{hostId}/machines/{vmId}
```

A private `buildPath(String vmId, String... suffix)` helper will produce the correct path based on `mode`.

---

## 6. HTTP Client Design

- Use `java.net.http.HttpClient` (JDK 11+, already available, no third-party lib).
- One `HttpClient` instance per `PrlDevopsHttpClient` instance (thread-safe, reusable).
- `Content-Type: application/json` + `Authorization: Bearer <token>` set on every request.
- Request bodies serialised with `ObjectMapper` (Jackson); responses deserialised with `ObjectMapper`.
- Response handling:
  - `2xx` → deserialise body (or ignore for DELETE 202).
  - `4xx` / `5xx` → read body, deserialise as `ApiErrorResponse`, throw `PrlApiException(statusCode, errorResponse)`.
  - Network errors → wrap `IOException` in `PrlApiException`.

---

## 7. Exception Hierarchy

```
PrlApiException (checked)
  fields: int httpStatus, String message, ApiErrorResponse detail
  factory: PrlApiException.fromResponse(int status, String body, ObjectMapper om)

PrlApiTimeoutException extends PrlApiException
  factory: PrlApiTimeoutException(String vmId, Duration timeout)
```

Callers can `catch (PrlApiException e) { if (e.getHttpStatus() == 404) ... }`.

---

## 8. Polling State Machine (`waitForVmReady`)

```
loop (until deadline):
    response = getVmStatus(vmId)   // GET /machines/{id}/status
    switch response.status:
        "running"  → return response     ✔ success
        "error"    → throw PrlApiException
        "pending"  │
        "starting" → sleep(interval), continue
        other      → throw PrlApiException("unexpected state")
    if now > deadline → throw PrlApiTimeoutException
```

Status string comparison: **case-insensitive** to guard against the API returning `"Running"` vs `"running"`.

---

## 9. Jackson Setup

- Add `jackson-databind` to `pom.xml` (Jenkins BOM should already provide it via `jenkins-core`; declare `<scope>provided</scope>` to avoid bundling a conflicting version).
- Use `@JsonProperty("clone_name")` etc. to map snake_case JSON to camelCase Java fields.
- Configure `ObjectMapper` with `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false` so future API additions don't break the client.
- DTOs are plain POJOs (no Spring, no Android, no special classloader requirements).

---

## 10. Dependencies to Add (`pom.xml`)

| Artifact | Scope | Reason |
|---|---|---|
| `com.fasterxml.jackson.core:jackson-databind` | `provided` | JSON serialisation (already in Jenkins) |
| `com.squareup.okhttp3:mockwebserver` | `test` | Mock HTTP server for unit tests |
| `org.junit.jupiter:junit-jupiter` | `test` | JUnit 5 (Jenkins 2.479 BOM provides it) |

`java.net.http.HttpClient` is in the JDK — no extra dependency.

---

## 11. Testing Strategy

- **MockWebServer** (OkHttp) stands up a real local HTTP server per test.
- Tests enqueue canned responses (`MockResponse`) and assert the outgoing request (method, path, headers, body).
- Coverage targets:
  - Happy path for all three operations (`cloneVm`, `getVmStatus`, `deleteVm`).
  - `waitForVmReady`: RUNNING on first poll, RUNNING after N polls, timeout, ERROR status.
  - HTTP 4xx (expect `PrlApiException` with correct `httpStatus`).
  - HTTP 5xx.
  - Network error (server closed early).
  - HOST vs ORCHESTRATOR URL paths (two `@ParameterizedTest` variants).

---

## 12. Key Decisions & Risks

| Decision | Rationale |
|---|---|
| `PUT` not `POST` for clone | Confirmed from Postman — source VM ID is in the path |
| `sourceVmId` as separate arg | Keeps `CloneRequest` as a pure body DTO and makes the interface honest about needing an existing VM |
| `hostId` at construction, not per-call | For a Jenkins cloud instance the orchestrator host is fixed at config time; per-call `hostId` would complicate the interface unnecessarily |
| `Jackson provided scope` | Jenkins ships Jackson; bundling a second version causes classloader conflicts |
| Status comparison case-insensitive | The Postman spec shows lowercase strings, but the Go service may differ — defensive comparison costs nothing |
| `waitForVmReady` in the interface | The task spec requires it; keeping it on the interface makes it mockable in upstream tests |

---

## 13. File Creation Order (implementation sequence)

1. `ConnectionMode.java` (enum, trivial)
2. `ApiErrorResponse.java`, `CloneRequest.java`, `CloneResponse.java`, `VmStatusResponse.java` (DTOs)
3. `PrlApiException.java`, `PrlApiTimeoutException.java` (exceptions)
4. `PrlDevopsApiClient.java` (interface)
5. `PrlDevopsHttpClient.java` (implementation)
6. `PrlDevopsHttpClientTest.java` (tests with MockWebServer)
7. `pom.xml` — add `mockwebserver` test dependency
