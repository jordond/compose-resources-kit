package dev.jordond.composeresourceskit.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import dev.jordond.composeresourceskit.isInComposeResources

class ComposeResourceFormatInspection : LocalInspectionTool() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
  ): PsiElementVisitor {
    return object : XmlElementVisitor() {
      override fun visitXmlTag(tag: XmlTag) {
        val virtualFile = tag.containingFile?.virtualFile ?: return
        if (!virtualFile.isInComposeResources()) return

        val parentDir = virtualFile.parent?.name ?: return
        if (!parentDir.startsWith("values")) return

        when (tag.name) {
          "string" -> validateStringTag(tag, holder)
          "plurals" -> validatePluralsTag(tag, holder)
        }
      }
    }
  }

  private fun validateStringTag(
    tag: XmlTag,
    holder: ProblemsHolder,
  ) {
    val textContent = tag.value.text
    if (textContent.isBlank()) return

    val issues = FormatSpecifierParser.validate(textContent)
    reportIssues(tag, issues, holder)
  }

  private fun validatePluralsTag(
    tag: XmlTag,
    holder: ProblemsHolder,
  ) {
    val itemTags = tag.findSubTags("item")
    if (itemTags.isEmpty()) return

    // Validate each item individually
    for (itemTag in itemTags) {
      val textContent = itemTag.value.text
      if (textContent.isBlank()) continue

      val issues = FormatSpecifierParser.validate(textContent)
      reportIssues(itemTag, issues, holder)
    }

    // Validate consistency across variants
    val variants = itemTags
      .mapNotNull { itemTag ->
        val quantity = itemTag.getAttributeValue("quantity") ?: return@mapNotNull null
        val text = itemTag.value.text
        quantity to text
      }.toMap()

    val consistencyIssues = FormatSpecifierParser.validatePluralsConsistency(variants)
    for (issue in consistencyIssues) {
      if (issue is FormatSpecifierParser.ValidationResult.InconsistentPluralsSpecifiers) {
        val itemTag = itemTags.firstOrNull {
          it.getAttributeValue("quantity") == issue.variant
        } ?: continue
        holder.registerProblem(
          itemTag,
          "Plural variant '${issue.variant}' has ${issue.variantCount} format " +
            "specifier(s), but other variants have ${issue.expectedCount}",
          ProblemHighlightType.WARNING,
        )
      }
    }
  }

  private fun reportIssues(
    tag: XmlTag,
    issues: List<FormatSpecifierParser.ValidationResult>,
    holder: ProblemsHolder,
  ) {
    for (issue in issues) {
      when (issue) {
        is FormatSpecifierParser.ValidationResult.InvalidSpecifier -> {
          val textElement = findTextElementContaining(tag, issue.range)
          val target = textElement ?: tag
          holder.registerProblem(
            target,
            "Invalid format specifier '${issue.specifier}'. " +
              "Compose resources only support %N\$s and %N\$d (e.g., %1\$s, %2\$d)",
            ProblemHighlightType.ERROR,
            ConvertToPositionalSpecifierQuickFix(issue.specifier),
          )
        }

        is FormatSpecifierParser.ValidationResult.NonSequentialNumbering -> {
          holder.registerProblem(
            tag,
            "Format specifier positions are not sequential. " +
              "Expected ${issue.expected}, found ${issue.actual}",
            ProblemHighlightType.WARNING,
          )
        }

        is FormatSpecifierParser.ValidationResult.Valid,
        is FormatSpecifierParser.ValidationResult.InconsistentPluralsSpecifiers,
        -> Unit
      }
    }
  }

  /**
   * Find the XmlText child of a tag that contains the given character range.
   */
  private fun findTextElementContaining(
    tag: XmlTag,
    range: IntRange,
  ): XmlText? {
    var offset = 0
    for (child in tag.value.children) {
      if (child is XmlText) {
        val childLength = child.text.length
        if (range.first >= offset && range.first < offset + childLength) {
          return child
        }
        offset += childLength
      }
    }
    return null
  }
}

private class ConvertToPositionalSpecifierQuickFix(
  private val invalidSpecifier: String,
) : LocalQuickFix {
  override fun getName(): String = "Convert '$invalidSpecifier' to positional specifier"

  override fun getFamilyName(): String = "Convert to positional format specifier"

  override fun applyFix(
    project: Project,
    descriptor: ProblemDescriptor,
  ) {
    val element = descriptor.psiElement ?: return
    val tag = generateSequence(element) { it.parent }
      .filterIsInstance<XmlTag>()
      .firstOrNull() ?: return

    val oldText = tag.value.text

    // Collect existing positional specifiers to determine next position
    val existingSpecifiers = FormatSpecifierParser.extractSpecifiers(oldText)
    val maxPosition = existingSpecifiers.maxOfOrNull { it.position } ?: 0

    // Replace unpositioned specifiers with positional ones
    var nextPosition = maxPosition + 1
    val newText = oldText.replace(Regex("""%([sd])""")) { match ->
      val type = match.groupValues[1]
      val replacement = "%${nextPosition}\$$type"
      nextPosition++
      replacement
    }

    if (newText != oldText) {
      tag.value.text = newText
    }
  }
}
