package dev.jordond.composeresourceskit

import com.intellij.openapi.project.Project
import dev.jordond.composeresourceskit.settings.ComposeResourcesSettings

internal val Project.settings
  get() = ComposeResourcesSettings.getInstance(this)
