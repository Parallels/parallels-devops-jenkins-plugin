# Local Development Environment Setup

To compile, test, and package the Parallels DevOps Jenkins plugin, you must configure your local macOS development environment with the foundational Java toolchains.

## 1. Install Homebrew (macOS Package Manager)
If you do not have Homebrew installed, run this command in your terminal:
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

## 2. Install OpenJDK 21
Jenkins currently targets Java 21 (or 17/11) to compile modern plugins cleanly.
```bash
brew install openjdk@21
```

Next, link the JDK so the macOS system wrappers can find it natively, and add it to your `~/.zshrc`:
```bash
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```
*Verify success by running: `java -version`*

## 3. Install Apache Maven
Maven is the essential lifecycle build tool for Jenkins plugins. It compiles the Java files, registers the Jelly UI fragments, and dynamically packages the final `.hpi` artifact.
```bash
brew install maven
```
*Verify success by running: `mvn -version`*

## 4. Install an IDE (IntelliJ IDEA Recommended)
IntelliJ is the absolute gold standard for Jenkins plugin development because it natively understands XML Jelly files and Maven architectures.
```bash
brew install --cask intellij-idea-ce
```
**Pro Tip**: Once installed, search for the free **"Jenkins Development Support"** plugin inside IntelliJ. It provides syntax highlighting and auto-complete exclusively for Jenkins Jelly UI files!

## Next Steps
Once your `mvn` command works globally, the Maven Archetype can properly generate the Java boilerplate code, and we can begin connecting to the `prl-devops-service`!
