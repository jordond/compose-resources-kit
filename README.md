<div align="center">

<img src="src/main/resources/META-INF/pluginIcon.svg" width="84" alt="Compose Resources Kit icon"/>

# Compose Resources Kit

A collection of tools for working with Compose Multiplatform resources. Automated resource accessor generation, resource
navigation, inspections, and management.

[![Version](https://img.shields.io/github/v/release/jordond/compose-resources-kit?label=Version&logo=github)](https://github.com/jordond/compose-resources-kit/releases)
[![Marketplace](https://img.shields.io/jetbrains/plugin/v/30280-compose-resources-kit?label=Marketplace&logo=jetbrains)](https://plugins.jetbrains.com/plugin/30280-compose-resources-kit)
[![CI](https://img.shields.io/github/actions/workflow/status/jordond/compose-resources-kit/ci.yml?label=CI&logo=github)](https://github.com/jordond/compose-resources-kit/actions/workflows/ci.yml)
[![Verify](https://img.shields.io/github/actions/workflow/status/jordond/compose-resources-kit/verify.yml?label=CI&logo=github)](https://github.com/jordond/compose-resources-kit/actions/workflows/verify.yml)
[![License](https://img.shields.io/github/license/jordond/compose-resources-kit)](LICENSE.md)

<img width="1200" alt="Demo" src="https://github.com/user-attachments/assets/213b30b4-9925-4f10-8283-bdfe6b2b4366" />

</div>

## Features

- **Automatic Resource Accessors**: Watches your `composeResources` directories and automatically runs
  `generateResourceAccessors` on file changes.
- **Unused Resource Detection**: Highlights unused XML resources (strings, plurals, etc.) and provides a quick fix to
  remove them.
- **Resource Navigation**: Go to declaration support from Kotlin to XML and vice versa.
- **Format Specifier Validation**: Detects invalid or unpositioned format specifiers in string and plural resources, with
  quick fixes to convert them to positional format.
- **Argument Count Validation**: Checks `stringResource()` and `pluralStringResource()` calls to ensure the correct
  number of format arguments are passed.
- **Unnecessary Escape Detection**: Warns about Android-style backslash escapes that are not needed in Compose
  Multiplatform.

### Automatic Resource Accessors

**Ever had this problem?**

You add a string to your `strings.xml`, switch back to your Kotlin file, try to use it, and get
`Unresolved reference 'new_string'`. So you manually trigger a build, wait for it to finish, and
*then* finally get back to what you were doing.

**This plugin fixes that.** It watches your `composeResources` directories and automatically runs
`generateResourceAccessors` whenever a file changes. Make an edit, wait a couple seconds, and the
accessors are there - Compose Previews re-render and everything.

### Unused Resource Detection

The plugin automatically identifies resources in your XML files that are not being used in your Kotlin code. These
resources are highlighted in the editor, and you can use the **Remove unused resource** quick fix (Alt+Enter) to delete
them safely.

Currently supports:

- `string`
- `plurals`
- `string-array`

### Resource Navigation

Navigate between your resources and code with ease:

- **Kotlin to XML**: Command/Ctrl + Click on a resource reference in Kotlin (e.g. `Res.string.my_string`) to go directly
  to its XML definition.
- **XML to Kotlin**: Command/Ctrl + Click on a resource `name` attribute in XML to find its usages in your project's
  Kotlin code.

### Format Specifier Validation

Validates format specifiers in your string and plural resources. Compose Multiplatform requires positional format
specifiers (`%1$s`, `%2$d`), not the unpositioned format (`%s`, `%d`) that Android allows. This inspection catches:

- Unpositioned specifiers like `%s` or `%d` (with a quick fix to convert them)
- Invalid format specifier syntax
- Duplicate positional indices
- Gaps in positional numbering

### Argument Count Validation

Inspects your Kotlin code to ensure `stringResource()` and `pluralStringResource()` calls pass the correct number of
format arguments. If your string expects 2 arguments (`%1$s` and `%2$d`) but you only pass 1, the inspection will flag
the mismatch.

### Unnecessary Escape Detection

Developers migrating from Android often carry over escape sequences that are not needed in Compose Multiplatform. This
inspection warns about unnecessary backslash escapes:

- `\'` — apostrophe does not need escaping
- `\"` — double quote does not need escaping
- `\@` — `@` is not treated as a resource reference
- `\?` — `?` is not treated as a theme attribute

A quick fix is available to remove the unnecessary backslash. Valid escapes like `\n`, `\t`, `\\`, and `\uXXXX` are not
affected.

## Getting Started

1. **Install the plugin**: Get it from
   the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30280-compose-resources-kit) or search for **Compose
   Resources Kit** in **Settings > Plugins > Marketplace**.
2. **Open your project**: Open any project using the `org.jetbrains.compose` Gradle plugin. Detection is automatic.
3. **Automatic Features**:
    - **Resource Watcher**: Check the status bar for the **Compose Resources Kit** widget. It should show **Watching**.
      Editing any file in `composeResources` will trigger a background generation of accessors.
    - **Navigation**: Start using **Command/Ctrl + Click** (or **Go to Declaration**) on resource references in Kotlin
      to jump to XML, and on `name` attributes in XML to find usages in Kotlin.
    - **Unused Detection**: Open any resource XML file (e.g., `strings.xml`). Unused resources will be highlighted
      automatically with a warning.
4. **(Optional) Configure**: Go to **Settings > Tools > Compose Resources Kit** to adjust the watcher delay, toggle
   features, or add custom resource directories.

## Demo

https://github.com/user-attachments/assets/5fdd6b35-e3e0-4755-8683-d1a2b257b9f9

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
