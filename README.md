# Parallels Desktop Plugin for Jenkins

This plugin integrates Jenkins with Parallels Desktop (via the Parallels DevOps Service engine), allowing Jenkins to dynamically provision, manage, and destroy macOS, Windows, or Linux virtual machines as temporary build agents on demand.

## Features
- **Dynamic Node Provisioning:** Automatically requests cloned VMs from the prl-devops-service when the Jenkins queue is full.
- **Orchestrator Mode Support:** Integrates with the Parallels Catalog service to cache golden images and balance resources across a farm of Parallels Desktop VM hosts.
- **Auto Cleanup:** Automatically destroys the VMs using Jenkins Retention Strategies when the job finishes.

## Developer Documentation
See the `docs/` folder for architectural analysis.

### Initial Setup & Build Instructions
Before you begin, ensure you have correctly configured your machine dependencies by following our officially documented [Initial Setup Guide](docs/setup-guide.md).

**To explicitly compile and verify the codebase:**
```bash
mvn clean verify
```

**To run a local Jenkins instance for UI testing:**
```bash
mvn hpi:run
```
*(Jenkins will automatically start up and be accessible locally at `http://localhost:8080/jenkins`)*
