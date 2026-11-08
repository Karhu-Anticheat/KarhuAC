# KarhuAC

**Karhu AntiCheat — Public Source Release**

KarhuAC is a Minecraft anti-cheat project focused on detecting cheating behaviour and maintaining a fair competitive environment.

This repository contains a public source release of Karhu, intended for developers, contributors, and anyone interested in exploring or improving its implementation.

## Overview

- Publicly available source code.
- Maven-based build system.
- Java-based implementation.
- PacketEvents integration.
- SQLite and MongoDB dependencies.
- Community-driven development and maintenance.

## Requirements

The project currently uses the following build dependencies:

| Requirement | Version |
|---|---|
| Java | 8 target |
| Maven | 3.x |
| Spigot API | 1.18.2 |
| PacketEvents | 2.14.0 |

Additional dependencies are managed through the project's `pom.xml`.

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/Karhu-Anticheat/KarhuAC.git
```

```bash
cd KarhuAC
```

### 2. Prepare dependencies

Make sure all required dependencies are available.

In particular, the project references a local API library:

```text
libraries/KarhuAPI.jar
```

Ensure this file is present before attempting to build.

### 3. Build the project

Run the following Maven command:

```bash
mvn clean package
```

The generated JAR should be located in:

```text
target/
```

**Note:** A successful compilation does not mean the plugin is ready to run. The original loader and other runtime requirements may still need to be addressed.

## Project Structure

```text
KarhuAC/
├── libraries/
│   └── KarhuAPI.jar
├── src/
│   └── main/
├── pom.xml
├── LICENSE
└── README.md
```

## Development

Contributions are welcome!

If you are interested in helping improve KarhuAC, you can contribute by:

- Fixing existing issues.
- Improving source code quality.
- Working on compatibility.
- Resolving build and runtime problems.
- Improving documentation.
- Helping make the project independently runnable.

Please test your changes carefully before submitting a pull request.

## Current Status

KarhuAC is currently a source release rather than a ready-to-use anti-cheat plugin.

The main development goals are:

- Removing the dependency on the original loader.
- Resolving outstanding build issues.
- Improving compatibility.
- Restoring standalone functionality.
- Making the project easier to maintain and contribute to.

## License

This project is licensed under the **MIT License**.

See [LICENSE](LICENSE) for more information.

## Contributing

Community contributions are appreciated. Feel free to fork the repository, experiment with the code, report issues, and submit pull requests.

Every contribution helps move the project forward.
---

<p align="center">
  Made with ❤️ by the KarhuAC community
</p>
