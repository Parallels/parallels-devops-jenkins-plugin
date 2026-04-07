# Parallels Devops Jenkins Plugin

This plugin integrates Jenkins with Parallels Devops Service (via the Parallels DevOps Service engine), allowing Jenkins to dynamically provision, manage, and destroy macOS, Windows, or Linux virtual machines as temporary build agents on demand.

## Features
- **Dynamic Node Provisioning:** Automatically requests cloned VMs from the prl-devops-service when the Jenkins queue is full.
- **Orchestrator Mode Support:** Integrates with the Parallels Catalog service to cache golden images and balance resources across a farm of Parallels Devops VM hosts.
- **Auto Cleanup:** Automatically destroys the VMs using Jenkins Retention Strategies when the job finishes.

## Developer Documentation
See the `docs/` folder for architectural analysis.

### Initial Setup & Build Instructions
Before you begin, ensure you have correctly configured your machine dependencies by following our officially documented [Initial Setup Guide](docs/setup-guide.md).

**We have explicitly written a `Makefile` to automatically streamline compilation processes for you.**

**To securely compile and verify the codebase:**
```bash
make build
```

**To boot the local Jenkins development server:**
```bash
make run
```
*(Jenkins will automatically spin up and listen at `http://localhost:8080/jenkins`)*
