.PHONY: all build test run clean package

# Force Maven to use JDK 21 (fixes 'Unsupported class file major version 69 / Java 25' bugs)
export JAVA_HOME := $(shell /usr/libexec/java_home -v 21 2>/dev/null || echo "/opt/homebrew/opt/openjdk@21")

# Default target
all: build

# Compile the plugin purely (skip test harness overhead)
build:
	mvn clean package -DskipTests

# Run unit tests
test:
	mvn test

# Boot the local Jenkins development server
run:
	mvn hpi:run -DskipTests

# Package the final .hpi file
package:
	mvn clean package -DskipTests

# Wipe all compiled Java artifacts and purge the Jenkins cache
clean:
	mvn clean
	rm -rf work/
