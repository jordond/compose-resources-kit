package dev.jordond.composeresourceskit.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ComposeDetector(
  private val project: Project,
) : Disposable {
  private val log = Logger.getInstance(ComposeDetector::class.java)
  private val moduleCache = ConcurrentHashMap<String, Boolean>()

  @Volatile
  private var projectCache: Boolean? = null

  fun isComposeMultiplatformProject(): Boolean {
    projectCache?.let { return it }

    val pluginLog = PluginLogger.getInstance(project)
    val modules = ModuleManager.getInstance(project).modules
    pluginLog.info("Detection: checking ${modules.size} module(s)...")

    val result = modules.any { isComposeModule(it) }
    projectCache = result
    val msg =
      if (result) {
        "Project IS Compose Multiplatform"
      } else {
        "Project is NOT Compose Multiplatform — plugin will not react to file changes"
      }
    log.info(msg)
    pluginLog.info(msg)
    return result
  }

  fun isComposeModule(module: Module): Boolean {
    val moduleId = module.name
    moduleCache[moduleId]?.let { return it }

    val pluginLog = PluginLogger.getInstance(project)

    val viaGradle = checkViaGradleTasks(module)
    pluginLog.info("  [${module.name}] via Gradle tasks: $viaGradle")

    val viaExtensions = if (viaGradle == null) {
      checkViaGradleExtensions(module).also {
        pluginLog.info("  [${module.name}] via Gradle extensions: $it")
      }
    } else {
      null
    }

    val viaDirs = if (viaGradle == null && viaExtensions == null) {
      checkViaResourceDirectories(module).also {
        pluginLog.info("  [${module.name}] via resource dirs: $it")
      }
    } else {
      null
    }

    val result = viaGradle ?: viaExtensions ?: viaDirs ?: false
    moduleCache[moduleId] = result
    if (result) pluginLog.info("  [${module.name}] -> COMPOSE MODULE")
    return result
  }

  private fun checkViaGradleTasks(module: Module): Boolean? {
    val projectPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null

    val projectInfo = ProjectDataManager
      .getInstance()
      .getExternalProjectData(project, GradleConstants.SYSTEM_ID, project.basePath ?: return null)
      ?: return null

    val projectStructure = projectInfo.externalProjectStructure ?: return null

    val allTasks = ExternalSystemApiUtil.findAll(projectStructure, ProjectKeys.TASK)
    val moduleTasks = allTasks.filter { taskNode ->
      val linkedPath = taskNode.data.linkedExternalProjectPath
      linkedPath == projectPath
    }

    val hasResourceTask = moduleTasks.any {
      it.data.name.startsWith("generateResourceAccessorsFor")
    }

    return if (moduleTasks.isNotEmpty()) hasResourceTask else null
  }

  private fun checkViaGradleExtensions(module: Module): Boolean? {
    val settings = GradleExtensionsSettings.getInstance(project)
    val extensionsData = settings.getExtensionsFor(module) ?: return null
    val isCompose = extensionsData.extensions.values.any { ext ->
      ext.typeFqn == "org.jetbrains.compose.ComposeExtension" ||
        ext.typeFqn.startsWith("org.jetbrains.compose.")
    }
    return if (isCompose) true else null
  }

  private fun checkViaResourceDirectories(module: Module): Boolean? {
    val modulePath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
    val srcDir = File(modulePath, "src")
    if (!srcDir.isDirectory) return null

    val sourceSets = srcDir.listFiles()?.filter { it.isDirectory } ?: return null
    val hasComposeResources = sourceSets.any { sourceSet ->
      File(sourceSet, "composeResources").isDirectory
    }

    return if (hasComposeResources) true else null
  }

  fun invalidateCache() {
    moduleCache.clear()
    projectCache = null
    val msg = "Detection cache invalidated — will re-check on next file event"
    log.info(msg)
    PluginLogger.getInstance(project).info(msg)
  }

  override fun dispose() {
    moduleCache.clear()
    projectCache = null
  }

  companion object {
    fun getInstance(project: Project): ComposeDetector = project.getService(ComposeDetector::class.java)
  }
}
