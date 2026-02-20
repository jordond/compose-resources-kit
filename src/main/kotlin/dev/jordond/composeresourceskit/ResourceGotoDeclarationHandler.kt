package dev.jordond.composeresourceskit

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

class ResourceGotoDeclarationHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(
    sourceElement: PsiElement?,
    offset: Int,
    editor: Editor,
  ): Array<PsiElement>? {
    val project = sourceElement?.project ?: return null
    val ref = ResourceResolver.resolveResourceReference(sourceElement) ?: return null

    val targets = when (ref) {
      is ResourceReference.XmlResource -> ResourceResolver.findXmlResourceTargets(project, ref)
      is ResourceReference.FileResource -> ResourceResolver.findFileResources(project, ref)
    }

    return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
  }

  override fun getActionText(context: DataContext): String = "Go to Compose Resource"
}
