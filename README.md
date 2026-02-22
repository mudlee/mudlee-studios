# Will see what it will be

## Requirements

- Java 17 Zulu

## Usage

```bash
# Run from command line:
$ ./gradlew run
```

```bash
# Package
$ ./gradlew jlink

# Run
$ ./sandbox/build/image/bin/sandbox-app
```

## Code Formatting

This project uses [palantir-java-format](https://github.com/palantir/palantir-java-format) (120 character line length) via the [Spotless](https://github.com/diffplug/spotless) Gradle plugin.

```bash
# Apply formatting to all Java source files in-place
$ ./gradlew spotlessApply

# Check formatting without modifying files (exits non-zero if anything is unformatted)
$ ./gradlew spotlessCheck
```