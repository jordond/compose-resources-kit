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

    // Handle Kotlin -> XML
    val kotlinRef = ResourceResolver.resolveResourceReference(sourceElement)
    if (kotlinRef != null) {
      val targets = when (kotlinRef) {
        is ResourceReference.XmlResource -> ResourceResolver.findXmlResourceTargets(project, kotlinRef)
        is ResourceReference.FileResource -> ResourceResolver.findFileResources(project, kotlinRef)
      }
      return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    // Handle XML -> Kotlin (Find Usages)
    val xmlRef = ResourceResolver.resolveResourceFromXml(sourceElement)
    if (xmlRef != null) {
      val targets = ResourceResolver.findUsages(project, xmlRef)
      return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    return null
  }

  override fun getActionText(context: DataContext): String = "Go to Compose Resource"
}
