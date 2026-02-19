package dev.jordond.composeresourceskit.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import dev.jordond.composeresourceskit.service.ComposeDetector
import dev.jordond.composeresourceskit.service.ComposeResourcesService
import dev.jordond.composeresourceskit.settings.ComposeResourcesConfigurable
import dev.jordond.composeresourceskit.settings.ComposeResourcesSettings
import java.awt.event.MouseEvent
import javax.swing.Icon

class ComposeResourcesStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = "ComposeResourcesWidget"

    override fun getDisplayName(): String = "Compose Resources Kit"

    override fun isAvailable(project: Project): Boolean {
        return ComposeDetector.getInstance(project).isComposeMultiplatformProject()
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return ComposeResourcesStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {}
}

private class ComposeResourcesStatusBarWidget(
    private val project: Project,
) : StatusBarWidget, StatusBarWidget.IconPresentation {

    private var statusBar: StatusBar? = null

    override fun ID(): String = "ComposeResourcesWidget"

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
            ComposeResourcesService.Status.ERROR -> "Compose Resources Kit: Error (click for settings)"
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent> {
        return Consumer {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, ComposeResourcesConfigurable::class.java)
        }
    }
}
