package dev.jordond.composeresourceskit

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import dev.jordond.composeresourceskit.listener.ComposeResourcesFileListener
import dev.jordond.composeresourceskit.service.ComposeDetector

class ComposeResourcesStartupActivity : ProjectActivity {
  private val log = Logger.getInstance(ComposeResourcesStartupActivity::class.java)

  override suspend fun execute(project: Project) {
    val detector = ComposeDetector.getInstance(project)

    ApplicationManager
      .getApplication()
      .messageBus
      .connect(detector)
      .subscribe(VirtualFileManager.VFS_CHANGES, ComposeResourcesFileListener(project))

    project.messageBus.connect(detector).subscribe(
      ProjectDataImportListener.TOPIC,
      object : ProjectDataImportListener {
        override fun onImportFinished(projectPath: String?) {
          log.info("Gradle sync finished — re-evaluating Compose detection")
          detector.invalidateCache()
          ApplicationManager
            .getApplication()
            .getServiceIfCreated(StatusBarWidgetsManager::class.java)
            ?.updateAllWidgets()
        }
      },
    )
  }
}
