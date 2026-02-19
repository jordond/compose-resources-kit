package dev.jordond.composeresourceskit.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ComposeDetector(private val project: Project) : Disposable {

    private val log = Logger.getInstance(ComposeDetector::class.java)
    private val moduleCache = ConcurrentHashMap<String, Boolean>()

    @Volatile
    private var projectCache: Boolean? = null

    fun isComposeMultiplatformProject(): Boolean {
        projectCache?.let { return it }

        val result = ModuleManager.getInstance(project).modules.any { isComposeModule(it) }
        projectCache = result
        log.info("Compose Multiplatform project detection: $result")
        return result
    }

    fun isComposeModule(module: Module): Boolean {
        val moduleId = module.name
        moduleCache[moduleId]?.let { return it }

        val result = checkViaGradleTasks(module)
            ?: checkViaBuildScript(module)
            ?: checkViaResourceDirectories(module)
            ?: false

        moduleCache[moduleId] = result
        return result
    }

    private fun checkViaGradleTasks(module: Module): Boolean? {
        val projectPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null

        val projectInfo = ProjectDataManager.getInstance()
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

    private fun checkViaBuildScript(module: Module): Boolean? {
        val modulePath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
        val moduleDir = File(modulePath)

        val buildFile = File(moduleDir, "build.gradle.kts").takeIf { it.exists() }
            ?: File(moduleDir, "build.gradle").takeIf { it.exists() }
            ?: return null

        return try {
            val content = buildFile.readText()
            content.contains("org.jetbrains.compose") ||
                content.contains("org.jetbrains.kotlin.plugin.compose")
        } catch (e: Exception) {
            log.debug("Failed to read build script: ${buildFile.path}", e)
            null
        }
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
        log.info("Compose detection cache invalidated")
    }

    override fun dispose() {
        moduleCache.clear()
        projectCache = null
    }

    companion object {
        fun getInstance(project: Project): ComposeDetector {
            return project.getService(ComposeDetector::class.java)
        }
    }
}
