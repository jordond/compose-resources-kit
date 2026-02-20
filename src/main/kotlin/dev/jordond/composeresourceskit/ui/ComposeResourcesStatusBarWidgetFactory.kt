package dev.jordond.composeresourceskit.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Consumer
import dev.jordond.composeresourceskit.ComposeResourcesBundle
import dev.jordond.composeresourceskit.service.ComposeDetector
import dev.jordond.composeresourceskit.service.ComposeResourcesService
import dev.jordond.composeresourceskit.settings.ComposeResourcesConfigurable
import dev.jordond.composeresourceskit.settings.ComposeResourcesSettings
import java.awt.event.MouseEvent
import javax.swing.Icon

class ComposeResourcesStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = WIDGET_ID

  override fun getDisplayName(): String = ComposeResourcesBundle.message("plugin.name")

  override fun isAvailable(project: Project): Boolean =
    ComposeDetector.getInstance(project).isComposeMultiplatformProject()

  override fun createWidget(project: Project): StatusBarWidget = ComposeResourcesStatusBarWidget(project)

  override fun disposeWidget(widget: StatusBarWidget) {
    Disposer.dispose(widget)
  }

  companion object {
    const val WIDGET_ID: String = "ComposeResourcesWidget"
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
      return ComposeResourcesIcons.Disabled
    }
    return when (ComposeResourcesService.getInstance(project).status) {
      ComposeResourcesService.Status.IDLE -> ComposeResourcesIcons.Success
      ComposeResourcesService.Status.RUNNING -> ComposeResourcesIcons.Running
      ComposeResourcesService.Status.ERROR -> ComposeResourcesIcons.Error
    }
  }

  override fun getTooltipText(): String {
    val settings = ComposeResourcesSettings.getInstance(project)
    if (!settings.enabled) return ComposeResourcesBundle.message("widget.tooltip.disabled")
    return when (ComposeResourcesService.getInstance(project).status) {
      ComposeResourcesService.Status.IDLE -> ComposeResourcesBundle.message("widget.tooltip.watching")
      ComposeResourcesService.Status.RUNNING -> ComposeResourcesBundle.message("widget.tooltip.generating")
      ComposeResourcesService.Status.ERROR -> ComposeResourcesBundle.message("widget.tooltip.error")
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
        ComposeResourcesBundle.message("plugin.name"),
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

    val toggleText = if (settings.enabled) {
      ComposeResourcesBundle.message("action.toggle.disable")
    } else {
      ComposeResourcesBundle.message("action.toggle.enable")
    }
    val toggleIcon = if (settings.enabled) AllIcons.Actions.Suspend else AllIcons.Actions.Resume
    group.add(
      object : AnAction(
        toggleText,
        ComposeResourcesBundle.message("action.toggle.description"),
        toggleIcon,
      ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

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
        ComposeResourcesBundle.message("action.generate.text"),
        ComposeResourcesBundle.message("action.generate.description"),
        AllIcons.Actions.Refresh,
      ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

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
        ComposeResourcesBundle.message("action.refresh.text"),
        ComposeResourcesBundle.message("action.refresh.description"),
        AllIcons.Actions.ForceRefresh,
      ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
          ComposeDetector.getInstance(project).invalidateCache()
          WindowManager
            .getInstance()
            .getStatusBar(project)
            ?.updateWidget(ComposeResourcesStatusBarWidgetFactory.WIDGET_ID)
        }
      },
    )

    group.add(
      object : AnAction(
        ComposeResourcesBundle.message("action.settings.text"),
        ComposeResourcesBundle.message("action.settings.description"),
        AllIcons.General.Settings,
      ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

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
