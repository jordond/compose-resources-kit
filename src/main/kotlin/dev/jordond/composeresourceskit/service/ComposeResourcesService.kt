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
import dev.jordond.composeresourceskit.settings
import dev.jordond.composeresourceskit.settings.ComposeResourcesSettings
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ComposeResourcesService(
  private val project: Project,
) : Disposable {
  private val log = Logger.getInstance(ComposeResourcesService::class.java)
  private val runningModules = ConcurrentHashMap.newKeySet<String>()

  private val updateQueue = MergingUpdateQueue(
    name = "ComposeResourcesGeneration",
    mergingTimeSpan = project.settings.debounceMs,
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

  fun runGenerateForAllModules() {
    if (project.isDisposed) return
    val detector = ComposeDetector.getInstance(project)
    val pluginLog = PluginLogger.getInstance(project)

    val modulePaths = ModuleManager
      .getInstance(project)
      .modules
      .filter { detector.isComposeModule(it) }
      .groupBy { ExternalSystemApiUtil.getExternalProjectPath(it) }
      .mapNotNull { (_, modules) ->
        modules.minByOrNull {
          ExternalSystemApiUtil.getExternalProjectId(it)?.count { c -> c == ':' } ?: Int.MAX_VALUE
        }
      }.mapNotNull { module ->
        val gradlePath = ExternalSystemApiUtil.getExternalProjectId(module) ?: return@mapNotNull null
        when {
          gradlePath == ":" || gradlePath.isBlank() -> ""
          else -> gradlePath.trimEnd(':')
        }
      }

    if (modulePaths.isEmpty()) {
      pluginLog.warn("No Compose modules found — nothing to generate")
      return
    }

    pluginLog.info("Generating resources for ${modulePaths.size} module(s)")
    for (modulePath in modulePaths) {
      runGenerateTask(modulePath)
    }
  }

  fun onFileChanged(filePath: String) {
    if (project.isDisposed) return
    val pluginLog = PluginLogger.getInstance(project)

    if (!isWatchedResourcePath(filePath)) {
      return
    }

    val modulePath = resolveModulePath(filePath)
    if (modulePath == null) {
      pluginLog.warn("  Could not resolve Gradle module for: $filePath")
      return
    }

    pluginLog.info(
      "  Matched module=$modulePath — queuing task (debounce ${
        ComposeResourcesSettings.getInstance(
          project,
        ).debounceMs
      }ms)",
    )
    updateQueue.queue(
      object : Update("compose-resources-$modulePath") {
        override fun run() {
          runGenerateTask(modulePath)
        }
      },
    )
  }

  private fun isWatchedResourcePath(path: String): Boolean {
    if (path.contains("/build/")) return false
    if (path.contains("/composeResources/") || path.endsWith("/composeResources")) return true
    return project.settings.additionalResourcePaths.any { customPath ->
      path.contains("/$customPath/") || path.endsWith("/$customPath")
    }
  }

  private fun resolveModulePath(filePath: String): String? {
    val pathBeforeResources = extractPathBeforeResources(filePath) ?: return null
    val projectRoot = project.basePath ?: return null
    val detector = ComposeDetector.getInstance(project)

    // Walk up from the resource directory to find the owning Gradle module
    // by locating the nearest build.gradle(.kts) file.
    var dir = File(pathBeforeResources)
    val rootDir = File(projectRoot)
    while (dir.path.startsWith(rootDir.path)) {
      val hasBuildFile = dir.resolve("build.gradle.kts").exists() || dir.resolve("build.gradle").exists()
      if (hasBuildFile) {
        val dirPath = dir.path
        // Find the root Gradle module (not a source-set sub-module) at this path.
        // Source-set modules share the same externalProjectPath but have more ':' segments.
        val module = ModuleManager
          .getInstance(project)
          .modules
          .filter { ExternalSystemApiUtil.getExternalProjectPath(it) == dirPath }
          .filter { detector.isComposeModule(it) }
          .minByOrNull { ExternalSystemApiUtil.getExternalProjectId(it)?.count { c -> c == ':' } ?: Int.MAX_VALUE }

        if (module != null) {
          val gradlePath = ExternalSystemApiUtil.getExternalProjectId(module) ?: return null
          return when {
            gradlePath == ":" || gradlePath.isBlank() -> ""
            else -> gradlePath.trimEnd(':')
          }
        }
      }

      dir = dir.parentFile ?: break
    }
    return null
  }

  private fun extractPathBeforeResources(filePath: String): String? {
    val composeResIdx = filePath.indexOf("/composeResources")
    if (composeResIdx != -1) return filePath.substring(0, composeResIdx)

    val settings = project.settings
    for (customPath in settings.additionalResourcePaths) {
      val idx = filePath.indexOf("/$customPath/")
      if (idx != -1) return filePath.substring(0, idx)
      if (filePath.endsWith("/$customPath")) return filePath.removeSuffix("/$customPath")
    }
    return null
  }

  private fun runGenerateTask(modulePath: String) {
    if (!runningModules.add(modulePath)) {
      log.info("Generation already running for: $modulePath, skipping")
      return
    }

    val taskName = if (modulePath.isEmpty()) {
      "generateResourceAccessorsForCommonMain"
    } else {
      "$modulePath:generateResourceAccessorsForCommonMain"
    }

    val pluginLog = PluginLogger.getInstance(project)
    log.info("Running Gradle task: $taskName")
    pluginLog.info("Running Gradle task: $taskName")
    updateStatus(Status.RUNNING)

    val basePath = project.basePath
    if (basePath == null) {
      runningModules.remove(modulePath)
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

    val showNotifications = project.settings.showNotifications
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

      runningModules.remove(modulePath)
      updateStatus(if (runningModules.isEmpty()) Status.IDLE else Status.RUNNING)
      pluginLog.info("Gradle task dispatched successfully: $taskName")
      log.info("Gradle task dispatched: $taskName")
      if (showNotifications) {
        notify("Resource accessors generated for ${modulePath.ifEmpty { "root" }}")
      }
    } catch (e: Exception) {
      runningModules.remove(modulePath)
      updateStatus(Status.ERROR)
      log.error("Failed to run Gradle task: $taskName", e)
      pluginLog.error("Failed to run Gradle task: $taskName — ${e.message}")
      if (showNotifications) {
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
