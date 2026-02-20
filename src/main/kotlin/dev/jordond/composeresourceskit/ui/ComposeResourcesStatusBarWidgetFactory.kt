package dev.jordond.composeresourceskit.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.util.Consumer
import dev.jordond.composeresourceskit.service.ComposeDetector
import dev.jordond.composeresourceskit.service.ComposeResourcesService
import dev.jordond.composeresourceskit.settings.ComposeResourcesConfigurable
import dev.jordond.composeresourceskit.settings.ComposeResourcesSettings
import java.awt.event.MouseEvent
import javax.swing.Icon

class ComposeResourcesStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = WIDGET_ID

  override fun getDisplayName(): String = "Compose Resources Kit"

  override fun isAvailable(project: Project): Boolean =
    ComposeDetector.getInstance(project).isComposeMultiplatformProject()

  override fun createWidget(project: Project): StatusBarWidget = ComposeResourcesStatusBarWidget(project)

  override fun disposeWidget(widget: StatusBarWidget) {}

  companion object {
    const val WIDGET_ID = "ComposeResourcesWidget"
  }
}

private class ComposeResourcesStatusBarWidget(
  private val project: Project,
) : StatusBarWidget,
  StatusBarWidget.IconPresentation {
  private var statusBar: StatusBar? = null

  override fun ID(): String = ComposeResourcesStatusBarWidgetFactory.WIDGET_ID

  override fun install(statusBar: StatusBar) {
    this.statusBar = statusBar
  }

  override fun dispose() {
    statusBar = null
  }

  override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

  override fun getIcon(): Icon {
    if (!ComposeResourcesSettings.getInstance(project).enabled) {
      return AllIcons.Actions.Suspend
    }
    return when (ComposeResourcesService.getInstance(project).status) {
      ComposeResourcesService.Status.IDLE -> AllIcons.Actions.Checked
      ComposeResourcesService.Status.RUNNING -> AllIcons.Actions.Execute
      ComposeResourcesService.Status.ERROR -> AllIcons.General.Warning
    }
  }

  override fun getTooltipText(): String {
    val settings = ComposeResourcesSettings.getInstance(project)
    if (!settings.enabled) return "Compose Resources Kit: Disabled"
    return when (ComposeResourcesService.getInstance(project).status) {
      ComposeResourcesService.Status.IDLE -> "Compose Resources Kit: Watching"
      ComposeResourcesService.Status.RUNNING -> "Compose Resources Kit: Generating..."
      ComposeResourcesService.Status.ERROR -> "Compose Resources Kit: Error"
    }
  }

  override fun getClickConsumer(): Consumer<MouseEvent> =
    Consumer { e ->
      val component = e.component ?: return@Consumer
      val group = createActionGroup()
      val dataContext = DataContext { dataId ->
        when (dataId) {
          CommonDataKeys.PROJECT.name -> project
          else -> null
        }
      }
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(
        null,
        group,
        dataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
      )
      popup.showUnderneathOf(component)
    }

  private fun createActionGroup(): DefaultActionGroup {
    val settings = ComposeResourcesSettings.getInstance(project)
    val group = DefaultActionGroup()

    val toggleText = if (settings.enabled) "Disable" else "Enable"
    val toggleIcon = if (settings.enabled) AllIcons.Actions.Suspend else AllIcons.Actions.Resume
    group.add(
      object : AnAction(toggleText, "Toggle automatic resource generation", toggleIcon) {
        override fun actionPerformed(e: AnActionEvent) {
          settings.loadState(
            ComposeResourcesSettings.State(
              enabled = !settings.enabled,
              debounceMs = settings.debounceMs,
              showNotifications = settings.showNotifications,
              loggingEnabled = settings.loggingEnabled,
              additionalResourcePaths = settings.additionalResourcePaths.toMutableList(),
            ),
          )
          statusBar?.updateWidget(ComposeResourcesStatusBarWidgetFactory.WIDGET_ID)
        }
      },
    )

    group.add(
      object : AnAction(
        "Generate Now",
        "Run resource accessor generation for all Compose modules",
        AllIcons.Actions.Refresh,
      ) {
        override fun actionPerformed(e: AnActionEvent) {
          ComposeResourcesService.getInstance(project).runGenerateForAllModules()
        }

        override fun update(e: AnActionEvent) {
          e.presentation.isEnabled = settings.enabled
        }
      },
    )

    group.add(Separator.getInstance())

    group.add(
      object : AnAction(
        "Refresh Detection",
        "Redetect Compose multiplatform modules",
        AllIcons.Actions.ForceRefresh,
      ) {
        override fun actionPerformed(e: AnActionEvent) {
          ComposeDetector.getInstance(project).invalidateCache()
          ApplicationManager
            .getApplication()
            .getServiceIfCreated(StatusBarWidgetsManager::class.java)
            ?.updateAllWidgets()
        }
      },
    )

    group.add(
      object : AnAction("Settings...", "Open compose resources kit settings", AllIcons.General.Settings) {
        override fun actionPerformed(e: AnActionEvent) {
          ShowSettingsUtil
            .getInstance()
            .showSettingsDialog(project, ComposeResourcesConfigurable::class.java)
        }
      },
    )

    return group
  }
}
