package dev.jordond.composeresourceskit.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JSpinner

class ComposeResourcesConfigurableProvider(
  private val project: Project,
) : ConfigurableProvider() {
  override fun createConfigurable(): Configurable = ComposeResourcesConfigurable(project)
}

class ComposeResourcesConfigurable(
  private val project: Project,
) : Configurable {
  private var enabledCheckBox: JBCheckBox? = null
  private var debounceSpinner: JSpinner? = null
  private var notificationsCheckBox: JBCheckBox? = null
  private var pathsListModel: DefaultListModel<String>? = null

  override fun getDisplayName(): String = "Compose Resources Kit"

  override fun createComponent(): JComponent {
    val settings = ComposeResourcesSettings.getInstance(project)

    val listModel = DefaultListModel<String>()
    settings.additionalResourcePaths.forEach { listModel.addElement(it) }
    pathsListModel = listModel

    val pathsList = JBList(listModel)

    val pathsPanel = ToolbarDecorator
      .createDecorator(pathsList)
      .setAddAction {
        val input = com.intellij.openapi.ui.Messages.showInputDialog(
          project,
          "Enter the resource directory name (e.g., desktopResources):",
          "Add Custom Resource Directory",
          null,
        )
        if (!input.isNullOrBlank()) {
          listModel.addElement(input.trim())
        }
      }.setRemoveAction {
        val selected = pathsList.selectedIndex
        if (selected >= 0) {
          listModel.remove(selected)
        }
      }.disableUpDownActions()
      .createPanel()

    return panel {
      group("General") {
        row {
          enabledCheckBox = checkBox("Enable automatic resource accessor generation")
            .applyToComponent { isSelected = settings.enabled }
            .component
        }
        row("Debounce delay (ms):") {
          debounceSpinner = spinner(500..10000, 100)
            .applyToComponent { value = settings.debounceMs }
            .component
        }
        row {
          notificationsCheckBox = checkBox("Show notifications when generation runs")
            .applyToComponent { isSelected = settings.showNotifications }
            .component
        }
      }
      group("Custom Resource Directories") {
        row {
          comment(
            "Add directory names used with compose.resources { customDirectory(...) }. " +
              "For example, if your config uses directoryProvider for 'desktopResources', " +
              "add 'desktopResources' here.",
          )
        }
        row {
          cell(pathsPanel)
            .align(Align.FILL)
        }
      }
      group("Info") {
        row {
          comment(
            "Watches for changes in composeResources directories (and any custom directories above) " +
              "then automatically runs the Gradle generateResourceAccessors task for the affected module. " +
              "The plugin only activates for projects using the org.jetbrains.compose Gradle plugin.",
          )
        }
      }
    }
  }

  override fun isModified(): Boolean {
    val settings = ComposeResourcesSettings.getInstance(project)
    if (enabledCheckBox?.isSelected != settings.enabled) return true
    if ((debounceSpinner?.value as? Int) != settings.debounceMs) return true
    if (notificationsCheckBox?.isSelected != settings.showNotifications) return true

    val currentPaths = pathsListModel?.let { model ->
      (0 until model.size()).map { model.getElementAt(it) }
    } ?: emptyList()
    if (currentPaths != settings.additionalResourcePaths) return true

    return false
  }

  override fun apply() {
    val settings = ComposeResourcesSettings.getInstance(project)
    val paths = pathsListModel?.let { model ->
      (0 until model.size()).map { model.getElementAt(it) }.toMutableList()
    } ?: mutableListOf()

    settings.loadState(
      ComposeResourcesSettings.State(
        enabled = enabledCheckBox?.isSelected ?: true,
        debounceMs = (debounceSpinner?.value as? Int) ?: 2000,
        showNotifications = notificationsCheckBox?.isSelected ?: true,
        additionalResourcePaths = paths,
      ),
    )
  }

  override fun reset() {
    val settings = ComposeResourcesSettings.getInstance(project)
    enabledCheckBox?.isSelected = settings.enabled
    debounceSpinner?.value = settings.debounceMs
    notificationsCheckBox?.isSelected = settings.showNotifications

    pathsListModel?.let { model ->
      model.clear()
      settings.additionalResourcePaths.forEach { model.addElement(it) }
    }
  }

  override fun disposeUIResources() {
    enabledCheckBox = null
    debounceSpinner = null
    notificationsCheckBox = null
    pathsListModel = null
  }
}
