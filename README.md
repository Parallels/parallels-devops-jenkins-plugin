# Parallels DevOps Jenkins Plugin

[![CI](https://github.com/Parallels/parallels-devops-jenkins-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/Parallels/parallels-devops-jenkins-plugin/actions/workflows/ci.yml)
[![Release](https://github.com/Parallels/parallels-devops-jenkins-plugin/actions/workflows/release.yml/badge.svg)](https://github.com/Parallels/parallels-devops-jenkins-plugin/actions/workflows/release.yml)

This plugin integrates Jenkins with [Parallels DevOps Service](https://parallels.github.io/prl-devops-service/quick-start/), allowing Jenkins to dynamically provision, manage, and destroy macOS, Windows, or Linux virtual machines as temporary build agents on demand.

## Features

- **Dynamic Node Provisioning:** Automatically requests cloned VMs from the prl-devops-service when the Jenkins queue is full.
- **Orchestrator Mode Support:** Integrates with the Parallels Catalog service to cache golden images and balance resources across a farm of Parallels DevOps VM hosts.
- **Auto Cleanup:** Automatically destroys the VMs after each build completes.
- **Pipeline Support:** Use the `parallelsDevopsCommand` step directly in your Jenkinsfile.

## Usage

### Cloud Configuration

1. Navigate to **Manage Jenkins → Clouds → New Cloud** and select **Parallels DevOps Cloud**.
2. Enter your **Service URL** and **API Credentials** (Secret text bearer token, or Username+Password).
3. Choose the **Connection Mode**: `HOST` for a single Parallels Desktop host, or `ORCHESTRATOR` for a federated multi-host setup.
4. Add one or more **VM Templates**, each with a label, SSH credentials, and a provisioning config:
   - **Clone existing VM** — clone a named VM registered in the host.
   - **Create from catalog** — pull a golden image from the Parallels DevOps catalog.

### Pipeline Usage

```groovy
node('macos-sonoma') {
    parallelsDevopsCommand command: 'sw_vers'
}
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for build instructions, Makefile targets, and contribution guidelines.
