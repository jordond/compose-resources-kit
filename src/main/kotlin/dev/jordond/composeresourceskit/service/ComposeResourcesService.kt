package dev.jordond.composeresourceskit.service

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import dev.jordond.composeresourceskit.settings.ComposeResourcesSettings
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ComposeResourcesService(
  private val project: Project,
) : Disposable {
  private val log = Logger.getInstance(ComposeResourcesService::class.java)
  private val runningModules = ConcurrentHashMap.newKeySet<String>()

  private val updateQueue = MergingUpdateQueue(
    name = "ComposeResourcesGeneration",
    mergingTimeSpan = ComposeResourcesSettings.getInstance(project).debounceMs,
    isActive = true,
    modalityStateComponent = null,
    parent = this,
    activationComponent = null,
    thread = Alarm.ThreadToUse.POOLED_THREAD,
  )

  enum class Status { IDLE, RUNNING, ERROR }

  @Volatile
  var status: Status = Status.IDLE
    private set

  private data class ResourceMatch(
    val gradleModulePath: String,
    val sourceSet: String,
  )

  fun onFileChanged(filePath: String) {
    if (project.isDisposed) return
    val pluginLog = PluginLogger.getInstance(project)

    if (!isWatchedResourcePath(filePath)) {
      pluginLog.info("  Skipped (not a watched resource path): $filePath")
      return
    }

    val match = resolveResourceMatch(filePath)
    if (match == null) {
      pluginLog.warn("  Could not resolve Gradle module for: $filePath")
      return
    }

    pluginLog.info(
      "  Matched module=${match.gradleModulePath} sourceSet=${match.sourceSet} — queuing task (debounce ${
        ComposeResourcesSettings.getInstance(
          project,
        ).debounceMs
      }ms)",
    )
    val updateKey = "${match.gradleModulePath}:${match.sourceSet}"
    updateQueue.queue(
      object : Update("compose-resources-$updateKey") {
        override fun run() {
          runGenerateTask(match)
        }
      },
    )
  }

  private fun isWatchedResourcePath(path: String): Boolean {
    if (path.contains("/composeResources/") || path.endsWith("/composeResources")) return true

    val settings = ComposeResourcesSettings.getInstance(project)
    return settings.additionalResourcePaths.any { customPath ->
      path.contains("/$customPath/") || path.endsWith("/$customPath")
    }
  }

  private fun resolveResourceMatch(filePath: String): ResourceMatch? {
    val composeResIdx = filePath.indexOf("/composeResources")
    if (composeResIdx != -1) {
      val pathBeforeResources = filePath.substring(0, composeResIdx)
      val sourceSet = detectSourceSet(filePath, composeResIdx) ?: "commonMain"
      val modulePath = resolveModulePath(pathBeforeResources) ?: return null
      return ResourceMatch(modulePath, sourceSet)
    }

    val settings = ComposeResourcesSettings.getInstance(project)
    for (customPath in settings.additionalResourcePaths) {
      val pattern = "/$customPath/"
      val idx = filePath.indexOf(pattern)
      val endPattern = "/$customPath"
      if (idx != -1) {
        val pathBefore = filePath.substring(0, idx)
        val sourceSet = detectSourceSetFromCustomPath(filePath, customPath) ?: "commonMain"
        val modulePath = resolveModulePath(pathBefore) ?: return null
        return ResourceMatch(modulePath, sourceSet)
      } else if (filePath.endsWith(endPattern)) {
        val pathBefore = filePath.substring(0, filePath.length - endPattern.length)
        val modulePath = resolveModulePath(pathBefore) ?: return null
        return ResourceMatch(modulePath, "commonMain")
      }
    }

    return null
  }

  private fun detectSourceSet(
    filePath: String,
    composeResIdx: Int,
  ): String? {
    val srcIdx = filePath.lastIndexOf("/src/", composeResIdx)
    if (srcIdx == -1) return null
    val between = filePath.substring(srcIdx + 5, composeResIdx)
    return if (!between.contains("/")) between else null
  }

  private fun detectSourceSetFromCustomPath(
    filePath: String,
    customPath: String,
  ): String? {
    val customIdx = filePath.indexOf("/$customPath")
    if (customIdx == -1) return null
    val srcIdx = filePath.lastIndexOf("/src/", customIdx)
    if (srcIdx == -1) return null
    val between = filePath.substring(srcIdx + 5, customIdx)
    return if (!between.contains("/")) between else null
  }

  private fun resolveModulePath(pathBeforeResources: String): String? {
    val detector = ComposeDetector.getInstance(project)

    for (module in ModuleManager.getInstance(project).modules) {
      val externalPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: continue
      if (pathBeforeResources.startsWith(externalPath)) {
        if (!detector.isComposeModule(module)) continue

        val gradlePath = ExternalSystemApiUtil.getExternalProjectId(module)
        if (gradlePath != null && gradlePath != ":" && gradlePath.isNotBlank()) {
          return gradlePath.trimEnd(':')
        }
        return ""
      }
    }

    return null
  }

  private fun runGenerateTask(match: ResourceMatch) {
    val moduleKey = "${match.gradleModulePath}:${match.sourceSet}"
    if (!runningModules.add(moduleKey)) {
      log.info("Generation already running for: $moduleKey, skipping")
      return
    }

    val capitalizedSourceSet = match.sourceSet.replaceFirstChar { it.uppercase() }
    val taskName = if (match.gradleModulePath.isEmpty()) {
      "generateResourceAccessorsFor$capitalizedSourceSet"
    } else {
      "${match.gradleModulePath}:generateResourceAccessorsFor$capitalizedSourceSet"
    }

    val pluginLog = PluginLogger.getInstance(project)
    log.info("Running Gradle task: $taskName")
    pluginLog.info("Running Gradle task: $taskName")
    updateStatus(Status.RUNNING)

    val basePath = project.basePath
    if (basePath == null) {
      runningModules.remove(moduleKey)
      updateStatus(Status.IDLE)
      pluginLog.error("basePath is null — cannot run task")
      return
    }

    val taskSettings = ExternalSystemTaskExecutionSettings().apply {
      externalProjectPath = basePath
      taskNames = listOf(taskName)
      vmOptions = ""
      externalSystemIdString = GRADLE_SYSTEM_ID.id
    }

    try {
      ExternalSystemUtil.runTask(
        taskSettings,
        DefaultRunExecutor.EXECUTOR_ID,
        project,
        GRADLE_SYSTEM_ID,
        null,
        ProgressExecutionMode.IN_BACKGROUND_ASYNC,
        false,
      )

      runningModules.remove(moduleKey)
      updateStatus(if (runningModules.isEmpty()) Status.IDLE else Status.RUNNING)
      pluginLog.info("Gradle task dispatched successfully: $taskName")
      log.info("Gradle task dispatched: $taskName")
      val settings = ComposeResourcesSettings.getInstance(project)
      if (settings.showNotifications) {
        val moduleLabel = match.gradleModulePath.ifEmpty { "root" }
        notify("Resource accessors generated for $moduleLabel (${match.sourceSet})")
      }
    } catch (e: Exception) {
      runningModules.remove(moduleKey)
      updateStatus(Status.ERROR)
      log.error("Failed to run Gradle task: $taskName", e)
      pluginLog.error("Failed to run Gradle task: $taskName — ${e.message}")
      val settings = ComposeResourcesSettings.getInstance(project)
      if (settings.showNotifications) {
        notify("Failed to run Gradle task: ${e.message}", NotificationType.ERROR)
      }
    }
  }

  private fun updateStatus(newStatus: Status) {
    status = newStatus
    WindowManager
      .getInstance()
      .getStatusBar(project)
      ?.updateWidget("ComposeResourcesWidget")
  }

  private fun notify(
    content: String,
    type: NotificationType = NotificationType.INFORMATION,
  ) {
    NotificationGroupManager
      .getInstance()
      .getNotificationGroup("Compose Resources Kit")
      .createNotification(content, type)
      .notify(project)
  }

  override fun dispose() {
    runningModules.clear()
  }

  companion object {
    private val GRADLE_SYSTEM_ID = ProjectSystemId("GRADLE")

    fun getInstance(project: Project): ComposeResourcesService = project.getService(ComposeResourcesService::class.java)
  }
}
