package dev.jordond.composeresourceskit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import dev.jordond.composeresourceskit.service.ComposeDetector
import dev.jordond.composeresourceskit.service.ComposeResourcesService
import dev.jordond.composeresourceskit.service.PluginLogger
import dev.jordond.composeresourceskit.settings
import java.awt.Font
import javax.swing.DefaultListModel
import javax.swing.JButton
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
  private var loggingCheckBox: JBCheckBox? = null
  private var pathsListModel: DefaultListModel<String>? = null
  private var logTextArea: JBTextArea? = null
  private var logListener: (() -> Unit)? = null

  private val settings get() = project.settings

  override fun getDisplayName(): String = "Compose Resources Kit"

  override fun createComponent(): JComponent {
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

    val textArea = JBTextArea().apply {
      isEditable = false
      font = Font(Font.MONOSPACED, Font.PLAIN, 11)
      lineWrap = true
      wrapStyleWord = false
      background = JBColor.background()
    }
    logTextArea = textArea

    val pluginLog = PluginLogger.getInstance(project)
    val listener: () -> Unit = {
      ApplicationManager.getApplication().invokeLater {
        refreshLogText(textArea, pluginLog)
      }
    }
    logListener = listener
    pluginLog.addListener(listener)
    refreshLogText(textArea, pluginLog)

    val scrollPane = JBScrollPane(textArea).apply {
      preferredSize = java.awt.Dimension(600, 200)
    }

    val refreshDetectionButton = JButton("Refresh Detection").apply {
      addActionListener {
        ComposeDetector.getInstance(project).invalidateCache()
        pluginLog.info("--- Detection cache cleared by user ---")
        val service = ComposeResourcesService.getInstance(project)
        pluginLog.info("Current status: ${service.status}")
      }
    }

    val clearLogsButton = JButton("Clear Logs").apply {
      addActionListener {
        pluginLog.clear()
      }
    }

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
        row {
          loggingCheckBox = checkBox("Enable logging")
            .applyToComponent { isSelected = settings.loggingEnabled }
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
      group("Diagnostics") {
        visibleIf(loggingCheckBox!!.selected)
        row {
          comment(
            "Live log of plugin activity. Edit a file in your composeResources directory " +
              "and watch for entries here. If the project is not detected, click 'Refresh Detection'.",
          )
        }
        row {
          cell(scrollPane).align(Align.FILL)
        }
        row {
          cell(refreshDetectionButton)
          cell(clearLogsButton)
        }
      }
    }
  }

  private fun refreshLogText(
    textArea: JBTextArea,
    pluginLog: PluginLogger,
  ) {
    val sb = StringBuilder()
    for (entry in pluginLog.getEntries()) {
      val prefix = when (entry.level) {
        PluginLogger.Entry.Level.INFO -> "   "
        PluginLogger.Entry.Level.WARN -> "[!]"
        PluginLogger.Entry.Level.ERROR -> "[E]"
      }
      sb.appendLine("${entry.time} $prefix ${entry.message}")
    }
    textArea.text = sb.toString()
    val doc = textArea.document
    if (doc.length > 0) {
      textArea.caretPosition = doc.length
    }
  }

  override fun isModified(): Boolean {
    if (enabledCheckBox?.isSelected != settings.enabled) return true
    if ((debounceSpinner?.value as? Int) != settings.debounceMs) return true
    if (notificationsCheckBox?.isSelected != settings.showNotifications) return true
    if (loggingCheckBox?.isSelected != settings.loggingEnabled) return true

    val currentPaths = pathsListModel?.let { model ->
      (0 until model.size()).map { model.getElementAt(it) }
    } ?: emptyList()

    return currentPaths != settings.additionalResourcePaths
  }

  override fun apply() {
    val paths = pathsListModel?.let { model ->
      (0 until model.size()).map { model.getElementAt(it) }.toMutableList()
    } ?: mutableListOf()

    settings.loadState(
      ComposeResourcesSettings.State(
        enabled = enabledCheckBox?.isSelected ?: true,
        debounceMs = (debounceSpinner?.value as? Int) ?: 2000,
        showNotifications = notificationsCheckBox?.isSelected ?: true,
        loggingEnabled = loggingCheckBox?.isSelected ?: false,
        additionalResourcePaths = paths,
      ),
    )
  }

  override fun reset() {
    enabledCheckBox?.isSelected = settings.enabled
    debounceSpinner?.value = settings.debounceMs
    notificationsCheckBox?.isSelected = settings.showNotifications
    loggingCheckBox?.isSelected = settings.loggingEnabled

    pathsListModel?.let { model ->
      model.clear()
      settings.additionalResourcePaths.forEach { model.addElement(it) }
    }
  }

  override fun disposeUIResources() {
    logListener?.let { PluginLogger.getInstance(project).removeListener(it) }
    enabledCheckBox = null
    debounceSpinner = null
    notificationsCheckBox = null
    loggingCheckBox = null
    pathsListModel = null
    logTextArea = null
    logListener = null
  }
}
