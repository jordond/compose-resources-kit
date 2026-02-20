package dev.jordond.composeresourceskit.service

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import dev.jordond.composeresourceskit.settings
import dev.jordond.composeresourceskit.ui.ComposeResourcesStatusBarWidgetFactory
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ComposeResourcesService(
  private val project: Project,
) : Disposable {
  private val log = Logger.getInstance(ComposeResourcesService::class.java)
  private val runningModules = ConcurrentHashMap.newKeySet<String>()

  @Volatile
  private var disposed = false

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
    if (disposed || project.isDisposed) return
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
    if (disposed || project.isDisposed) return
    val pluginLog = PluginLogger.getInstance(project)

    if (!isWatchedResourcePath(filePath)) {
      return
    }

    val modulePath = resolveModulePath(filePath)
    if (modulePath == null) {
      pluginLog.warn("  Could not resolve Gradle module for: $filePath")
      return
    }

    val sourceSet = extractSourceSet(filePath)

    pluginLog.info(
      "  Matched module=$modulePath sourceSet=$sourceSet — queuing task (debounce ${project.settings.debounceMs}ms)",
    )

    if (status == Status.ERROR) {
      updateStatus(Status.IDLE)
    }

    updateQueue.queue(
      object : Update("compose-resources-$modulePath-$sourceSet") {
        override fun run() {
          runGenerateTask(modulePath, sourceSet)
        }
      },
    )
  }

  fun onSettingsChanged() {
    updateQueue.setMergingTimeSpan(project.settings.debounceMs)
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
    val allModules = ModuleManager.getInstance(project).modules

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
        val module = allModules
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
    val composeResIdx = filePath.indexOf("/composeResources/")
    if (composeResIdx != -1) return filePath.substring(0, composeResIdx)
    if (filePath.endsWith("/composeResources")) return filePath.removeSuffix("/composeResources")

    val settings = project.settings
    for (customPath in settings.additionalResourcePaths) {
      val idx = filePath.indexOf("/$customPath/")
      if (idx != -1) return filePath.substring(0, idx)
      if (filePath.endsWith("/$customPath")) return filePath.removeSuffix("/$customPath")
    }
    return null
  }

  private fun extractSourceSet(filePath: String): String {
    val match = SOURCE_SET_PATTERN.find(filePath)
    return match?.groupValues?.get(1) ?: "commonMain"
  }

  private fun runGenerateTask(
    modulePath: String,
    sourceSet: String = "commonMain",
  ) {
    if (disposed || project.isDisposed) return

    if (!runningModules.add(modulePath)) {
      log.info("Generation already running for: $modulePath, skipping")
      return
    }

    val sourceSetCapitalized = sourceSet.replaceFirstChar { it.uppercase() }
    val taskName = if (modulePath.isEmpty()) {
      "generateResourceAccessorsFor$sourceSetCapitalized"
    } else {
      "$modulePath:generateResourceAccessorsFor$sourceSetCapitalized"
    }

    val pluginLog = PluginLogger.getInstance(project)
    log.info("Running Gradle task: $taskName")
    pluginLog.info("Running Gradle task: $taskName")
    updateStatus(Status.RUNNING)

    val basePath = project.basePath
    if (basePath == null) {
      runningModules.remove(modulePath)
      updateStatus(if (runningModules.isEmpty()) Status.IDLE else Status.RUNNING)
      pluginLog.error("basePath is null — cannot run task")
      return
    }

    val taskSettings = ExternalSystemTaskExecutionSettings().apply {
      externalProjectPath = basePath
      taskNames = listOf(taskName)
      vmOptions = ""
      externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }

    val showNotifications = project.settings.showNotifications
    val callback = object : TaskCallback {
      override fun onSuccess() {
        runningModules.remove(modulePath)
        if (disposed || project.isDisposed) return
        updateStatus(if (runningModules.isEmpty()) Status.IDLE else Status.RUNNING)
        pluginLog.info("Gradle task completed: $taskName")
        log.info("Gradle task completed: $taskName")
        if (showNotifications) {
          notify("Resource accessors generated for ${modulePath.ifEmpty { "root" }}")
        }
        refreshGeneratedSources(basePath, modulePath)
      }

      override fun onFailure() {
        runningModules.remove(modulePath)
        if (disposed || project.isDisposed) return
        updateStatus(Status.ERROR)
        pluginLog.error("Gradle task failed: $taskName")
        log.warn("Gradle task failed: $taskName")
        if (showNotifications) {
          notify("Failed to generate resources for ${modulePath.ifEmpty { "root" }}", NotificationType.ERROR)
        }
      }
    }

    try {
      ExternalSystemUtil.runTask(
        taskSettings,
        DefaultRunExecutor.EXECUTOR_ID,
        project,
        GradleConstants.SYSTEM_ID,
        callback,
        ProgressExecutionMode.IN_BACKGROUND_ASYNC,
        false,
      )
      pluginLog.info("Gradle task dispatched: $taskName")
      log.info("Gradle task dispatched: $taskName")
    } catch (e: Exception) {
      runningModules.remove(modulePath)
      if (disposed || project.isDisposed) return
      updateStatus(Status.ERROR)
      log.error("Failed to dispatch Gradle task: $taskName", e)
      pluginLog.error("Failed to dispatch Gradle task: $taskName — ${e.message}")
      if (showNotifications) {
        notify("Failed to run Gradle task: ${e.message}", NotificationType.ERROR)
      }
    }
  }

  private fun refreshGeneratedSources(
    basePath: String,
    modulePath: String,
  ) {
    val moduleRelPath = modulePath.trimStart(':').replace(':', '/')
    val moduleDir = if (moduleRelPath.isEmpty()) File(basePath) else File(basePath, moduleRelPath)
    val generatedDir = File(moduleDir, "build/generated")

    if (generatedDir.exists()) {
      val pluginLog = PluginLogger.getInstance(project)
      pluginLog.info("Refreshing VFS for generated sources: ${generatedDir.path}")
      VfsUtil.markDirtyAndRefresh(true, true, true, generatedDir)
    }
  }

  private fun updateStatus(newStatus: Status) {
    status = newStatus
    if (disposed || project.isDisposed) return
    WindowManager
      .getInstance()
      .getStatusBar(project)
      ?.updateWidget(ComposeResourcesStatusBarWidgetFactory.WIDGET_ID)
  }

  private fun notify(
    content: String,
    type: NotificationType = NotificationType.INFORMATION,
  ) {
    NotificationGroupManager
      .getInstance()
      .getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(content, type)
      .notify(project)
  }

  override fun dispose() {
    disposed = true
    runningModules.clear()
  }

  companion object {
    private const val NOTIFICATION_GROUP_ID = "Compose Resources Kit"
    private val SOURCE_SET_PATTERN = Regex("/src/([^/]+)/composeResources")

    fun getInstance(project: Project): ComposeResourcesService = project.getService(ComposeResourcesService::class.java)
  }
}
