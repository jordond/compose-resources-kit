package dev.jordond.composeresourceskit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.WindowManager
import dev.jordond.composeresourceskit.listener.ComposeResourcesFileListener
import dev.jordond.composeresourceskit.service.ComposeDetector
import dev.jordond.composeresourceskit.ui.ComposeResourcesStatusBarWidgetFactory

class ComposeResourcesStartupActivity : ProjectActivity {
  private val log = Logger.getInstance(ComposeResourcesStartupActivity::class.java)

  override suspend fun execute(project: Project) {
    val detector = ComposeDetector.getInstance(project)

    project.messageBus
      .connect(detector)
      .subscribe(VirtualFileManager.VFS_CHANGES, ComposeResourcesFileListener(project))

    project.messageBus.connect(detector).subscribe(
      ProjectDataImportListener.TOPIC,
      object : ProjectDataImportListener {
        override fun onImportFinished(projectPath: String?) {
          if (project.isDisposed) return
          log.info("Gradle sync finished — re-evaluating Compose detection")
          detector.invalidateCache()
          WindowManager
            .getInstance()
            .getStatusBar(project)
            ?.updateWidget(ComposeResourcesStatusBarWidgetFactory.WIDGET_ID)
        }
      },
    )
  }
}
