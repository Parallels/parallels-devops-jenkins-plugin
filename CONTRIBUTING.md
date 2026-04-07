# Contributing to the Parallels DevOps Jenkins Plugin

Thank you for your interest in contributing! This guide covers everything you need to build, test, and run the plugin locally.

---

## Prerequisites

| Tool | Required version | Install |
|------|-----------------|---------|
| **JDK** | 17 (project default) or 21 | [Adoptium](https://adoptium.net/) / `brew install openjdk@17` |
| **Maven** | 3.8 or newer | [maven.apache.org](https://maven.apache.org/download.cgi) / `brew install maven` |
| **Git** | Any recent version | pre-installed on macOS/Linux |
| **IDE** (recommended) | IntelliJ IDEA (Community or Ultimate) | [jetbrains.com](https://www.jetbrains.com/idea/) |

> **Windows**: use WSL 2 and follow the Linux instructions inside the WSL shell.

---

## Initial Setup

```bash
# 1. Clone the repository
git clone https://github.com/jenkinsci/parallels-devops-jenkins-plugin.git
cd parallels-devops-jenkins-plugin

# 2. Verify Java version (should print 17.x or 21.x)
java -version

# 3. Verify Maven version (must be 3.8+)
mvn -version

# 4. Compile once to populate the local Maven cache (~2 min on first run)
make build
```

---

## Makefile Targets

All day-to-day tasks are automated via the `Makefile` at the repo root.

| Target | Command | Description |
|--------|---------|-------------|
| `make build` | `mvn clean compile` | Compiles Java sources. Fast, no packaging overhead. |
| `make test` | `mvn test` | Runs all JUnit / JenkinsRule tests and prints pass/fail. |
| `make run` | `mvn hpi:run` | Starts a local Jenkins at `http://localhost:8080/jenkins` with the plugin hot-loaded. Use `JENKINS_PORT` to change the port. |
| `make package` | `mvn clean package -DskipTests` | Produces `target/parallels-devops-jenkins-plugin.hpi`. |
| `make clean` | `mvn clean && rm -rf work/` | Removes all build artefacts and the local Jenkins work directory. |

### Environment Variable Overrides

Both variables can be passed on the command line or exported in your shell:

```bash
# Change the Jenkins port (default: 8080)
make run JENKINS_PORT=9090

# Pin a specific Jenkins core version at runtime
make run JENKINS_VERSION=2.479.1

# Combine both
make run JENKINS_PORT=9090 JENKINS_VERSION=2.479.1
```

---

## Running the Plugin Locally

```bash
make run
```

Once Maven prints `Jenkins is fully up and running`, open your browser to:

```
http://localhost:8080/jenkins
```

Navigate to **Manage Jenkins → Manage Nodes and Clouds → Configure Clouds** to see the *Parallels DevOps* cloud provider listed.

Press `Ctrl+C` in the terminal to stop the server.

---

## Running Tests

```bash
make test
```

The Maven Surefire output will clearly show each test class, the number of tests run, and any failures or errors. Exit code `0` means all tests passed.

---

## Packaging the `.hpi` Artefact

```bash
make package
```

The finished plugin file will be at:

```
target/parallels-devops-jenkins-plugin.hpi
```

You can install this `.hpi` manually into any Jenkins instance via **Manage Jenkins → Plugins → Advanced → Deploy Plugin**.

---

## Troubleshooting FAQ

### 1. `Unsupported class file major version 69` (or similar) at runtime

**Cause**: Maven is using a JDK newer than the project's bytecode target (e.g. JDK 25 compiled classes being loaded by a JDK 17 runtime).

**Fix**: The `Makefile` sets `JAVA_HOME` to JDK 21 automatically on macOS via `/usr/libexec/java_home`. If you're on Linux or the auto-detection fails, set it explicitly before running make:

```bash
export JAVA_HOME=/path/to/jdk-21
make run
```

---

### 2. `mvn hpi:run` fails with `Address already in use`

**Cause**: Port 8080 (or whichever port is configured) is already occupied by another process.

**Fix**: Either stop the conflicting process or use a different port:

```bash
# Find the PID using port 8080
lsof -ti tcp:8080

# Or just run Jenkins on a different port
make run JENKINS_PORT=9090
```

---

### 3. `make test` fails with `Could not find artifact` during the first run

**Cause**: Local Maven repository cache is empty or corrupted; required Jenkins BOM/parent artefacts have not been downloaded yet.

**Fix**: Run the following to force a cache refresh:

```bash
mvn dependency:resolve -U
make test
```

If behind a corporate proxy, also configure `~/.m2/settings.xml` with the proxy settings (see [Maven proxy docs](https://maven.apache.org/guides/mini/guide-proxies.html)).

---

### 4. IntelliJ shows red imports / cannot resolve Jenkins symbols

**Cause**: IntelliJ has not imported the Maven project, or the Maven model needs refreshing.

**Fix**:
1. Open the project root in IntelliJ IDEA.
2. Right-click `pom.xml` → **Add as Maven Project** (if not already done).
3. In the Maven tool window, click **Reload All Maven Projects** (circular-arrow icon).
4. Make sure the Project SDK is set to JDK 17 under **File → Project Structure**.

---

### 5. `make clean` does not remove the old `.hpi` from a previously running Jenkins

**Cause**: The running Jenkins instance holds a file lock on the deployed `.hpi` or its exploded directory inside `work/`.

**Fix**: Stop the running `make run` process first (`Ctrl+C`), then run `make clean`.

---

## Code Style

- Java source lives under `src/main/java/`. Follow standard Java conventions (4-space indent, Javadoc for public APIs).
- Jelly views live under `src/main/resources/`.
- No `System.out.println` in production code — use `java.util.logging.Logger`.

---

## Submitting a Pull Request

1. Fork the repository and create a feature branch: `git checkout -b feat/my-feature`
2. Make your changes and ensure `make test` passes.
3. Push your branch and open a Pull Request against `main`.
4. Fill in the PR template and link any relevant issues.

---

## Platform Verification

| Platform | Status |
|----------|--------|
| macOS (Apple Silicon / Intel) | Verified |
| Linux (Ubuntu 22.04+) | Verified |
| Windows via WSL 2 | Acceptable (not natively required) |
