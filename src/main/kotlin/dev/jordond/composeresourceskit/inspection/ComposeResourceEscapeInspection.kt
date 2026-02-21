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

/**
 * Detects unnecessary Android-style backslash escapes in Compose Multiplatform resource strings.
 *
 * Unlike Android, Compose Multiplatform does not require escaping `'`, `"`, `@`, or `?`.
 * Using these escapes can cause the backslash to appear literally in the rendered string.
 */
class ComposeResourceEscapeInspection : LocalInspectionTool() {
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
          "string" -> checkTag(tag, holder)
          "plurals" -> {
            for (itemTag in tag.findSubTags("item")) {
              checkTag(itemTag, holder)
            }
          }
        }
      }
    }
  }

  private fun checkTag(
    tag: XmlTag,
    holder: ProblemsHolder,
  ) {
    val textContent = tag.value.text
    if (textContent.isEmpty()) return

    val escapes = findUnnecessaryEscapes(textContent)
    if (escapes.isEmpty()) return

    for (escape in escapes) {
      val textElement = findTextElementContaining(tag, escape.position)
      val target = textElement ?: tag
      holder.registerProblem(
        target,
        "Unnecessary Android escape '\\${escape.character}'. " +
          "Compose Multiplatform does not require escaping '${escape.character}'",
        ProblemHighlightType.WARNING,
        RemoveUnnecessaryEscapeQuickFix(escape.character),
      )
    }

    if (escapes.size > 1) {
      val textElement = findTextElementContaining(tag, escapes.first().position)
      val target = textElement ?: tag
      holder.registerProblem(
        target,
        "Contains ${escapes.size} unnecessary Android escapes",
        ProblemHighlightType.WARNING,
        RemoveAllUnnecessaryEscapesQuickFix(),
      )
    }
  }

  private fun findTextElementContaining(
    tag: XmlTag,
    position: Int,
  ): XmlText? {
    var offset = 0
    for (child in tag.value.children) {
      if (child is XmlText) {
        val childLength = child.text.length
        if (position >= offset && position < offset + childLength) {
          return child
        }
        offset += childLength
      }
    }
    return null
  }

  companion object {
    private val UNNECESSARY_ESCAPES = charArrayOf('\'', '"', '@', '?')

    data class UnnecessaryEscape(
      val position: Int,
      val character: Char,
    )

    fun findUnnecessaryEscapes(text: String): List<UnnecessaryEscape> {
      val results = mutableListOf<UnnecessaryEscape>()
      var i = 0
      while (i < text.length - 1) {
        if (text[i] == '\\') {
          val next = text[i + 1]
          when {
            next == '\\' -> i += 2 // escaped backslash, skip both
            next == 'n' || next == 't' -> i += 2 // valid escape
            next == 'u' &&
              i + 5 < text.length &&
              text.substring(i + 2, i + 6).all { it.isHexDigit() } -> i += 6 // unicode

            next in UNNECESSARY_ESCAPES -> {
              results.add(UnnecessaryEscape(i, next))
              i += 2
            }

            else -> i += 2 // unknown escape, skip
          }
        } else {
          i++
        }
      }
      return results
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
  }
}

private class RemoveUnnecessaryEscapeQuickFix(
  private val escapedChar: Char,
) : LocalQuickFix {
  override fun getName(): String = "Remove unnecessary escape '\\$escapedChar'"

  override fun getFamilyName(): String = "Remove unnecessary Android escape"

  override fun applyFix(
    project: Project,
    descriptor: ProblemDescriptor,
  ) {
    val element = descriptor.psiElement ?: return
    val tag = generateSequence(element) { it.parent }
      .filterIsInstance<XmlTag>()
      .firstOrNull() ?: return

    val oldText = tag.value.text
    val newText = removeEscape(oldText, escapedChar)
    if (newText != oldText) {
      tag.value.text = newText
    }
  }

  private fun removeEscape(
    text: String,
    char: Char,
  ): String {
    val sb = StringBuilder()
    var i = 0
    var removed = false
    while (i < text.length) {
      if (!removed && i < text.length - 1 && text[i] == '\\' && text[i + 1] == char) {
        // Check this backslash isn't itself escaped
        if (!isPrecededByOddBackslashes(text, i)) {
          sb.append(char)
          i += 2
          removed = true
          continue
        }
      }
      sb.append(text[i])
      i++
    }
    return sb.toString()
  }

  private fun isPrecededByOddBackslashes(
    text: String,
    index: Int,
  ): Boolean {
    var count = 0
    var j = index - 1
    while (j >= 0 && text[j] == '\\') {
      count++
      j--
    }
    return count % 2 == 1
  }
}

private class RemoveAllUnnecessaryEscapesQuickFix : LocalQuickFix {
  override fun getName(): String = "Remove all unnecessary Android escapes"

  override fun getFamilyName(): String = "Remove unnecessary Android escape"

  override fun applyFix(
    project: Project,
    descriptor: ProblemDescriptor,
  ) {
    val element = descriptor.psiElement ?: return
    val tag = generateSequence(element) { it.parent }
      .filterIsInstance<XmlTag>()
      .firstOrNull() ?: return

    val oldText = tag.value.text
    val newText = removeAllEscapes(oldText)
    if (newText != oldText) {
      tag.value.text = newText
    }
  }

  private fun removeAllEscapes(text: String): String {
    val unnecessaryChars = charArrayOf('\'', '"', '@', '?')
    val sb = StringBuilder()
    var i = 0
    while (i < text.length) {
      if (i < text.length - 1 && text[i] == '\\' && text[i + 1] in unnecessaryChars) {
        if (!isPrecededByOddBackslashes(text, i)) {
          sb.append(text[i + 1])
          i += 2
          continue
        }
      }
      sb.append(text[i])
      i++
    }
    return sb.toString()
  }

  private fun isPrecededByOddBackslashes(
    text: String,
    index: Int,
  ): Boolean {
    var count = 0
    var j = index - 1
    while (j >= 0 && text[j] == '\\') {
      count++
      j--
    }
    return count % 2 == 1
  }
}
