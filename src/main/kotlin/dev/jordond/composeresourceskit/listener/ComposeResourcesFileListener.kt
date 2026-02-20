package dev.jordond.composeresourceskit.listener

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import dev.jordond.composeresourceskit.service.ComposeResourcesService
import dev.jordond.composeresourceskit.service.PluginLogger
import dev.jordond.composeresourceskit.settings

class ComposeResourcesFileListener(
  private val project: Project,
) : BulkFileListener {
  override fun after(events: MutableList<out VFileEvent>) {
    if (project.isDisposed) return

    val relevantEvents = events.filter { it.isRelevantEvent() }
    if (relevantEvents.isEmpty()) return

    val log = PluginLogger.getInstance(project)

    if (!project.settings.enabled) {
      log.warn("Plugin disabled — ignoring ${relevantEvents.size} event(s)")
      return
    }

    val basePath = project.basePath ?: return
    val service = ComposeResourcesService.getInstance(project)

    val changeListManager = ChangeListManager.getInstance(project)

    for (event in relevantEvents) {
      val path = event.path
      if (!path.startsWith(basePath)) continue

      val file = event.file
      if (file != null && file.isValid && changeListManager.isIgnoredFile(file)) continue

      val eventType = event.javaClass.simpleName
        .removePrefix("VFile")
        .removeSuffix("Event")
      log.info("[$eventType] $path")
      service.onFileChanged(path)
    }
  }

  private fun VFileEvent.isRelevantEvent(): Boolean =
    this is VFileContentChangeEvent ||
      this is VFileCreateEvent ||
      this is VFileDeleteEvent ||
      this is VFileMoveEvent ||
      (this is VFilePropertyChangeEvent && propertyName == "name")
}
