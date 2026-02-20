package dev.jordond.composeresourceskit

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag

class ComposeResourceDocumentationProvider : AbstractDocumentationProvider() {
  override fun getQuickNavigateInfo(
    element: PsiElement,
    originalElement: PsiElement?,
  ): String? {
    val original = originalElement ?: return null
    val ref = ResourceResolver.resolveResourceReference(original) ?: return null
    if (ref !is ResourceReference.XmlResource) return null

    val project = original.project
    val tags = ResourceResolver.findXmlResources(project, ref)
    if (tags.isEmpty()) return null

    val value = extractDisplayValue(tags.first(), ref)
    val fileName = tags.first().containingFile?.name ?: "unknown"
    return "\"$value\" [$fileName]"
  }

  override fun generateDoc(
    element: PsiElement,
    originalElement: PsiElement?,
  ): String? {
    val original = originalElement ?: return null
    val ref = ResourceResolver.resolveResourceReference(original) ?: return null
    if (ref !is ResourceReference.XmlResource) return null

    val project = original.project
    val tags = ResourceResolver.findXmlResources(project, ref)
    if (tags.isEmpty()) return null

    return buildDocHtml(ref, tags)
  }

  override fun generateHoverDoc(
    element: PsiElement,
    originalElement: PsiElement?,
  ): String? = generateDoc(element, originalElement)

  private fun buildDocHtml(
    ref: ResourceReference.XmlResource,
    tags: List<XmlTag>,
  ): String {
    val sb = StringBuilder()

    // Definition header
    val typeLabel = when (ref.xmlTag) {
      "string" -> "String"
      "string-array" -> "String Array"
      "plurals" -> "Plurals"
      else -> ref.xmlTag
    }
    sb.append(DocumentationMarkup.DEFINITION_START)
    sb.append("$typeLabel Resource: <b>${escape(ref.key)}</b>")
    sb.append(DocumentationMarkup.DEFINITION_END)

    // Content: primary value
    sb.append(DocumentationMarkup.CONTENT_START)
    val firstTag = tags.first()
    when (ref.xmlTag) {
      "string" -> {
        sb.append("<p>${escape(firstTag.value.text)}</p>")
      }

      "string-array" -> {
        val items = firstTag.findSubTags("item")
        sb.append("<ul>")
        for (item in items) {
          sb.append("<li>${escape(item.value.text)}</li>")
        }
        sb.append("</ul>")
      }

      "plurals" -> {
        val items = firstTag.findSubTags("item")
        sb.append("<table>")
        for (item in items) {
          val quantity = item.getAttributeValue("quantity") ?: "?"
          sb.append("<tr>")
          sb.append("<td><code>$quantity</code>&nbsp;&nbsp;</td>")
          sb.append("<td>${escape(item.value.text)}</td>")
          sb.append("</tr>")
        }
        sb.append("</table>")
      }
    }
    sb.append(DocumentationMarkup.CONTENT_END)

    // Sections: file and locale info
    sb.append(DocumentationMarkup.SECTIONS_START)

    val localeEntries = tags.mapNotNull { tag ->
      val file = tag.containingFile?.virtualFile ?: return@mapNotNull null
      val dirName = file.parent?.name ?: return@mapNotNull null
      val locale = if (dirName == "values") "default" else dirName.removePrefix("values-")
      locale to file.name
    }

    if (localeEntries.size == 1) {
      val (locale, fileName) = localeEntries.first()
      sb.appendSection("File", fileName)
      if (locale != "default") {
        sb.appendSection("Locale", locale)
      }
    } else if (localeEntries.size > 1) {
      sb.appendSection("Locales", localeEntries.joinToString(", ") { it.first })

      // Show values per locale for strings
      if (ref.xmlTag == "string" && tags.size > 1) {
        val localeValues = tags.mapNotNull { tag ->
          val file = tag.containingFile?.virtualFile ?: return@mapNotNull null
          val dirName = file.parent?.name ?: return@mapNotNull null
          val locale = if (dirName == "values") "default" else dirName.removePrefix("values-")
          locale to tag.value.text
        }
        val valuesHtml = localeValues.joinToString("<br>") { (locale, value) ->
          "<code>$locale</code>: ${escape(value)}"
        }
        sb.appendSection("Translations", valuesHtml)
      }
    }

    sb.append(DocumentationMarkup.SECTIONS_END)
    return sb.toString()
  }

  private fun extractDisplayValue(
    tag: XmlTag,
    ref: ResourceReference.XmlResource,
  ): String =
    when (ref.xmlTag) {
      "string" -> tag.value.text
      "string-array" -> {
        val items = tag.findSubTags("item")
        "[${items.joinToString(", ") { it.value.text }}]"
      }
      "plurals" -> {
        val items = tag.findSubTags("item")
        items.joinToString(" | ") { item ->
          val q = item.getAttributeValue("quantity") ?: "?"
          "$q: ${item.value.text}"
        }
      }
      else -> tag.value.text
    }

  private fun StringBuilder.appendSection(
    header: String,
    content: String,
  ) {
    append(DocumentationMarkup.SECTION_HEADER_START)
    append("$header:")
    append(DocumentationMarkup.SECTION_SEPARATOR)
    append("<p>$content</p>")
    append(DocumentationMarkup.SECTION_END)
  }

  private fun escape(text: String): String = StringUtil.escapeXmlEntities(text)
}
