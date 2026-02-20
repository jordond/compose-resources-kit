<div align="center">

<img src="src/main/resources/META-INF/pluginIcon.svg" width="84" alt="Compose Resources Kit icon"/>

# Compose Resources Kit

**Automatic resource accessor generation for Compose Multiplatform**

[![Version](https://img.shields.io/github/v/release/jordond/compose-resources-kit?label=Version&logo=github)](https://github.com/jordond/compose-resources-kit/releases)
[![Marketplace](https://img.shields.io/jetbrains/plugin/v/30280-compose-resources-kit?label=Marketplace&logo=jetbrains)](https://plugins.jetbrains.com/plugin/30280-compose-resources-kit)
[![CI](https://img.shields.io/github/actions/workflow/status/jordond/compose-resources-kit/ci.yml?label=CI&logo=github)](https://github.com/jordond/compose-resources-kit/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/jordond/compose-resources-kit)](LICENSE.md)

</div>

### Ever had this problem?

You add a string to your `strings.xml`, switch back to your Kotlin file, try to use it, and get
`Unresolved reference 'new_string'`. So you manually trigger a build, wait for it to finish, and
*then* finally get back to what you were doing.

**This plugin fixes that.** It watches your `composeResources` directories and automatically runs
`generateResourceAccessors` whenever a file changes. Make an edit, wait a couple seconds, and the
accessors are there - Compose Previews re-render and everything.

## Features

- Automatic resource accessor generation on file changes
- Multi-module and source-set aware
- Status bar widget with quick actions
- Configurable via **Settings > Tools > Compose Resources Kit**

## Getting Started

1. Install the plugin from the
   [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30280-compose-resources-kit) or
   from **Settings > Plugins > Marketplace** and search for **Compose Resources Kit**.
2. Open a project that uses the `org.jetbrains.compose` Gradle plugin. The plugin will
   automatically detect it.
3. Look for the **Compose Resources Kit** widget in the bottom status bar - it should show
   **Watching**.
4. Edit any file in a `composeResources` directory (e.g. add a string to `strings.xml`). Resource
   accessors will regenerate automatically after a short delay.
5. (Optional) Adjust settings under **Settings > Tools > Compose Resources Kit** - debounce delay,
   notifications, and custom resource directories.

## Settings

**Settings > Tools > Compose Resources Kit**

| Setting                     | Default | Description                                                           |
|-----------------------------|---------|-----------------------------------------------------------------------|
| Enable automatic generation | On      | Toggle the file watcher on/off                                        |
| Debounce delay (ms)         | 2000    | How long to wait after the last file change before running generation |
| Show notifications          | Off     | Display balloon notifications on generation success/failure           |
| Enable logging              | Off     | Show a diagnostics log panel in the settings page                     |
| Custom resource directories |         | Additional directory names to watch (e.g. `desktopResources`)         |

## Status Bar

The widget in the bottom status bar shows the plugin state:

- **Watching** - idle, waiting for file changes
- **Generating...** - a Gradle task is running
- **Error** - the last generation failed
- **Disabled** - automatic generation is turned off

Click the widget to toggle enable/disable, trigger a manual generation for all modules, refresh
project detection, or open settings.

## Requirements

- IntelliJ IDEA 2024.1+ (Android Studio too)
- A project using the `org.jetbrains.compose` Gradle plugin
- Gradle integration enabled in the IDE

## Developing

```bash
# Clone the repo
git clone https://github.com/jordond/compose-resources-kit.git
cd compose-resources-kit

# Install the Spotless git pre-push hook
./gradlew spotlessInstallGitPrePushHook

# Build the plugin
./gradlew buildPlugin

# Run tests
./gradlew test

# Run a sandboxed IDE instance with the plugin loaded
./gradlew runIde
```

## License

[MIT](LICENSE.md)
