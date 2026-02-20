# Compose Resources Kit

Ever had this problem?

You add a string to your `strings.xml`, switch back to your Kotlin file, try to use it, and get
`Unresolved reference 'new_string'`. So you manually trigger a build, wait for it to finish, and
*then* finally get back to what you were doing.

**This plugin fixes that.** It watches your `composeResources` directories and automatically runs
`generateResourceAccessors` whenever a file changes. Make an edit, wait a couple seconds, and the
accessors are there — Compose Previews re-render and everything.

## Features

- Automatic resource accessor generation on file changes
- Multi-module and source-set aware
- Status bar widget with quick actions
- Configurable via **Settings > Tools > Compose Resources Kit**

## Installation

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com) or from
**Settings > Plugins > Marketplace** and search for **Compose Resources Kit**.

## Settings

**Settings > Tools > Compose Resources Kit**

| Setting                     | Default | Description                                                          |
|-----------------------------|---------|----------------------------------------------------------------------|
| Enable automatic generation | On      | Toggle the file watcher on/off                                       |
| Debounce delay (ms)         | 2000    | How long to wait after the last file change before running generation |
| Show notifications          | Off     | Display balloon notifications on generation success/failure          |
| Enable logging              | Off     | Show a diagnostics log panel in the settings page                    |
| Custom resource directories | —       | Additional directory names to watch (e.g. `desktopResources`)        |

## Status Bar

The widget in the bottom status bar shows the plugin state:

- **Watching** — idle, waiting for file changes
- **Generating...** — a Gradle task is running
- **Error** — the last generation failed
- **Disabled** — automatic generation is turned off

Click the widget to toggle enable/disable, trigger a manual generation for all modules, refresh
project detection, or open settings.

## Requirements

- IntelliJ IDEA 2024.1+ (or any JetBrains IDE based on the same platform)
- A project using the `org.jetbrains.compose` Gradle plugin
- Gradle integration enabled in the IDE

## License

[MIT](LICENSE.md)
