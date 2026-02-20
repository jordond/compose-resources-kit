package dev.jordond.composeresourceskit.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.xml.XmlTag
import dev.jordond.composeresourceskit.XML_TAG_TO_RES_PREFIX
import dev.jordond.composeresourceskit.isInComposeResources
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class UnusedComposeResourceInspection : LocalInspectionTool() {
  override fun getDisplayName(): String = "Unused Compose resource"

  override fun getGroupDisplayName(): String = "Compose Resources Kit"

  override fun getShortName(): String = "UnusedComposeResource"

  override fun isEnabledByDefault(): Boolean = true

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
  ): PsiElementVisitor {
    return object : XmlElementVisitor() {
      override fun visitXmlTag(tag: XmlTag) {
        if (!isComposeResourceTag(tag)) return

        val name = tag.getAttributeValue("name") ?: return
        val resPrefix = XML_TAG_TO_RES_PREFIX[tag.name] ?: return

        if (!hasUsages(tag.project, name, resPrefix)) {
          val nameAttr = tag.getAttribute("name")?.valueElement ?: tag
          holder.registerProblem(
            nameAttr,
            "Unused Compose resource '$name'",
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            RemoveResourceQuickFix(),
          )
        }
      }
    }
  }

  private fun isComposeResourceTag(tag: XmlTag): Boolean {
    if (tag.name !in XML_TAG_TO_RES_PREFIX) return false

    val virtualFile = tag.containingFile?.virtualFile ?: return false
    if (!virtualFile.isInComposeResources()) return false

    val parentDir = virtualFile.parent?.name ?: return false
    return parentDir.startsWith("values")
  }

  private fun hasUsages(
    project: Project,
    resourceName: String,
    resPrefix: String,
  ): Boolean {
    val fullRef = "$resPrefix$resourceName"
    val scope = GlobalSearchScope.projectScope(project)
    val searchHelper = PsiSearchHelper.getInstance(project)

    var found = false
    searchHelper.processElementsWithWord(
      { element, _ ->
        val file = element.containingFile?.virtualFile
        if (file != null && (file.path.contains("/build/") || file.path.contains("/.gradle/"))) {
          return@processElementsWithWord true
        }

        if (element is KtSimpleNameExpression && element.text == resourceName) {
          val parent = element.parent
          if (parent != null && parent.text.startsWith(resPrefix)) {
            if (parent.text == fullRef) {
              found = true
              return@processElementsWithWord false // Stop searching
            }
          }
        }
        true
      },
      scope,
      resourceName,
      UsageSearchContext.IN_CODE,
      true,
    )

    return found
  }
}

private class RemoveResourceQuickFix : LocalQuickFix {
  override fun getName(): String = "Remove unused resource"

  override fun getFamilyName(): String = name

  override fun applyFix(
    project: Project,
    descriptor: ProblemDescriptor,
  ) {
    val element = descriptor.psiElement ?: return
    // Walk up to the XmlTag from the attribute value element
    val tag = generateSequence(element) { it.parent }
      .filterIsInstance<XmlTag>()
      .firstOrNull() ?: return
    tag.delete()
  }
}
