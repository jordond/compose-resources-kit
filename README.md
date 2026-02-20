# Compose Resources Kit

An IntelliJ plugin that automatically
regenerates [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) resource accessors when files in
`composeResources` directories are modified.

No more manually running `generateResourceAccessorsForCommonMain` after editing strings, drawables, or other resource
files.

## Features

- Watches `composeResources` directories and automatically runs the `generateResourceAccessors` Gradle task when files
  change
- Multi-module and source-set aware
- Status bar widget with quick actions
- Configurable via **Settings > Tools > Compose Resources Kit**

## Installation

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com) or from **Settings > Plugins > Marketplace** and
search for **Compose Resources Kit**.

## Settings

**Settings > Tools > Compose Resources Kit**

| Setting                     | Default | Description                                                           |
|-----------------------------|---------|-----------------------------------------------------------------------|
| Enable automatic generation | On      | Toggle the file watcher on/off                                        |
| Debounce delay (ms)         | 2000    | How long to wait after the last file change before running generation |
| Show notifications          | Off     | Display balloon notifications on generation success/failure           |
| Enable logging              | Off     | Show a diagnostics log panel in the settings page                     |
| Custom resource directories | —       | Additional directory names to watch (e.g. `desktopResources`)         |

## Status Bar

The widget in the bottom status bar shows the plugin state:

- **Watching** — idle, waiting for file changes
- **Generating...** — a Gradle task is running
- **Error** — the last generation failed
- **Disabled** — automatic generation is turned off

Click the widget to toggle enable/disable, trigger a manual generation for all modules, refresh project detection, or
open settings.

## Requirements

- IntelliJ IDEA 2024.1+ (or any JetBrains IDE based on the same platform)
- A project using the `org.jetbrains.compose` Gradle plugin
- Gradle integration enabled in the IDE

## License

[MIT](LICENSE.md)
