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
import dev.jordond.composeresourceskit.ComposeResourcesBundle
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

  override fun getDisplayName(): String = ComposeResourcesBundle.message("plugin.name")

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
          ComposeResourcesBundle.message("settings.paths.add.message"),
          ComposeResourcesBundle.message("settings.paths.add.title"),
          null,
        )
        if (!input.isNullOrBlank()) {
          val trimmed = input.trim()
          if (trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains("..")) {
            com.intellij.openapi.ui.Messages.showErrorDialog(
              project,
              ComposeResourcesBundle.message("settings.paths.add.error.message"),
              ComposeResourcesBundle.message("settings.paths.add.error.title"),
            )
          } else {
            listModel.addElement(trimmed)
          }
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
        if (textArea.isShowing) {
          refreshLogText(textArea, pluginLog)
        }
      }
    }
    logListener = listener
    pluginLog.addListener(listener)
    refreshLogText(textArea, pluginLog)

    val scrollPane = JBScrollPane(textArea).apply {
      preferredSize = java.awt.Dimension(600, 200)
    }

    val refreshDetectionButton = JButton(ComposeResourcesBundle.message("settings.button.refresh")).apply {
      addActionListener {
        ComposeDetector.getInstance(project).invalidateCache()
        pluginLog.info("--- Detection cache cleared by user ---")
        val service = ComposeResourcesService.getInstance(project)
        pluginLog.info("Current status: ${service.status}")
      }
    }

    val clearLogsButton = JButton(ComposeResourcesBundle.message("settings.button.clear.logs")).apply {
      addActionListener {
        pluginLog.clear()
      }
    }

    return panel {
      group(ComposeResourcesBundle.message("settings.group.general")) {
        row {
          enabledCheckBox = checkBox(ComposeResourcesBundle.message("settings.enabled"))
            .applyToComponent { isSelected = settings.enabled }
            .component
        }
        row(ComposeResourcesBundle.message("settings.debounce.label")) {
          debounceSpinner = spinner(500..10000, 100)
            .applyToComponent { value = settings.debounceMs }
            .component
        }
        row {
          notificationsCheckBox = checkBox(ComposeResourcesBundle.message("settings.notifications"))
            .applyToComponent { isSelected = settings.showNotifications }
            .component
        }
        row {
          loggingCheckBox = checkBox(ComposeResourcesBundle.message("settings.logging"))
            .applyToComponent { isSelected = settings.loggingEnabled }
            .component
        }
      }
      group(ComposeResourcesBundle.message("settings.group.custom.paths")) {
        row {
          comment(ComposeResourcesBundle.message("settings.paths.description"))
        }
        row {
          cell(pathsPanel)
            .align(Align.FILL)
        }
      }
      group(ComposeResourcesBundle.message("settings.group.info")) {
        row {
          comment(ComposeResourcesBundle.message("settings.info.description"))
        }
      }
      group(ComposeResourcesBundle.message("settings.group.diagnostics")) {
        visibleIf(loggingCheckBox!!.selected)
        row {
          comment(ComposeResourcesBundle.message("settings.diagnostics.description"))
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
        showNotifications = notificationsCheckBox?.isSelected ?: false,
        loggingEnabled = loggingCheckBox?.isSelected ?: false,
        additionalResourcePaths = paths,
      ),
    )

    ComposeResourcesService.getInstance(project).onSettingsChanged()
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
