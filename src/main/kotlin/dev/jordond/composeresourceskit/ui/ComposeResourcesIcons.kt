package dev.jordond.composeresourceskit.ui

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object ComposeResourcesIcons {
  @JvmField
  val Base: Icon = IconLoader.getIcon("/icons/composeResources.svg", ComposeResourcesIcons::class.java)

  @JvmField
  val Success: Icon = IconLoader.getIcon("/icons/composeResourcesSuccess.svg", ComposeResourcesIcons::class.java)

  @JvmField
  val Running: Icon = IconLoader.getIcon("/icons/composeResourcesRunning.svg", ComposeResourcesIcons::class.java)

  @JvmField
  val Error: Icon = IconLoader.getIcon("/icons/composeResourcesError.svg", ComposeResourcesIcons::class.java)

  @JvmField
  val Disabled: Icon = IconLoader.getIcon("/icons/composeResourcesDisabled.svg", ComposeResourcesIcons::class.java)
}
