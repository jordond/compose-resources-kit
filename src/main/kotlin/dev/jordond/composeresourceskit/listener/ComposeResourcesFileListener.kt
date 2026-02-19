package dev.jordond.composeresourceskit.listener

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import dev.jordond.composeresourceskit.service.ComposeDetector
import dev.jordond.composeresourceskit.service.ComposeResourcesService
import dev.jordond.composeresourceskit.settings.ComposeResourcesSettings

class ComposeResourcesFileListener : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        val relevantEvents = events.filter { it.isRelevantEvent() }
        if (relevantEvents.isEmpty()) return

        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue

            val settings = ComposeResourcesSettings.getInstance(project)
            if (!settings.enabled) continue

            if (!ComposeDetector.getInstance(project).isComposeMultiplatformProject()) continue

            val basePath = project.basePath ?: continue
            val service = ComposeResourcesService.getInstance(project)

            for (event in relevantEvents) {
                val path = event.path
                if (path.startsWith(basePath)) {
                    service.onFileChanged(path)
                }
            }
        }
    }

    private fun VFileEvent.isRelevantEvent(): Boolean {
        return this is VFileContentChangeEvent
            || this is VFileCreateEvent
            || this is VFileDeleteEvent
            || this is VFileMoveEvent
            || (this is VFilePropertyChangeEvent && propertyName == "name")
    }
}
