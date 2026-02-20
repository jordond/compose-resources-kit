package dev.jordond.composeresourceskit

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import javax.swing.Icon

private val RESOURCE_KEY_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*".toRegex()
private val DRAWABLE_EXTENSIONS = listOf("png", "jpg", "jpeg", "bmp", "webp", "xml", "svg")
private val FONT_EXTENSIONS = listOf("ttf", "otf")

sealed interface ResourceReference {
  val key: String

  data class XmlResource(
    override val key: String,
    val xmlTag: String,
  ) : ResourceReference

  data class FileResource(
    override val key: String,
    val dirPrefix: String,
    val extensions: List<String>,
  ) : ResourceReference
}

val RESOURCE_PREFIXES: List<Pair<String, (String) -> ResourceReference>> = listOf(
  "Res.string." to { key -> ResourceReference.XmlResource(key, "string") },
  "Res.array." to { key -> ResourceReference.XmlResource(key, "string-array") },
  "Res.plurals." to { key -> ResourceReference.XmlResource(key, "plurals") },
  "Res.drawable." to { key -> ResourceReference.FileResource(key, "drawable", DRAWABLE_EXTENSIONS) },
  "Res.font." to { key -> ResourceReference.FileResource(key, "font", FONT_EXTENSIONS) },
)

/**
 * Maps a Res type prefix (e.g. "string") to the Kotlin accessor prefix (e.g. "Res.string.").
 */
val XML_TAG_TO_RES_PREFIX: Map<String, String> = mapOf(
  "string" to "Res.string.",
  "string-array" to "Res.array.",
  "plurals" to "Res.plurals.",
)

object ResourceResolver {
  fun resolveResourceReference(element: PsiElement): ResourceReference? {
    // If it's a name reference (like 'app_name'), get its qualified expression (e.g. 'Res.string.app_name')
    if (element is KtNameReferenceExpression) {
      val qualified = element.getQualifiedExpressionForSelector()
      if (qualified != null) {
        val ref = extractResourceReference(qualified)
        if (ref != null) return ref
      }
    }

    // Try finding the closest dot qualified expression by walking up
    val dotQualified = generateSequence(element) { it.parent }
      .filterIsInstance<KtDotQualifiedExpression>()
      .firstOrNull()

    val ref = dotQualified?.let { extractResourceReference(it) }
    if (ref != null) return ref

    // Handle if the element is actually part of a name reference (e.g. the LeafPsiElement identifier)
    val parent = element.parent
    if (parent != null && parent != element && parent !is org.jetbrains.kotlin.psi.KtFile) {
      return resolveResourceReference(parent)
    }

    return null
  }

  fun extractResourceReference(expression: KtExpression): ResourceReference? {
    val text = expression.text
    return RESOURCE_PREFIXES.firstNotNullOfOrNull { (prefix, factory) ->
      if (text.startsWith(prefix)) {
        val key = text.substringAfterLast('.')
        if (key.isNotEmpty() && key.matches(RESOURCE_KEY_REGEX)) factory(key) else null
      } else {
        null
      }
    }
  }

  fun resolveResourceFromXml(element: PsiElement): ResourceReference? {
    val attributeValue = generateSequence(element) { it.parent }
      .filterIsInstance<XmlAttributeValue>()
      .firstOrNull() ?: return null

    val attribute = attributeValue.parent as? XmlAttribute ?: return null
    if (attribute.name != "name") return null

    val tag = attribute.parent
    if (tag !is XmlTag) return null
    if (tag.name !in XML_TAG_TO_RES_PREFIX) return null
    if (tag.containingFile?.virtualFile?.isInComposeResources() != true) return null

    val name = tag.getAttributeValue("name") ?: return null
    return ResourceReference.XmlResource(name, tag.name)
  }

  fun findXmlResources(
    project: Project,
    ref: ResourceReference.XmlResource,
  ): List<XmlTag> =
    FileTypeIndex
      .getFiles(XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project))
      .asSequence()
      .filter { it.isInComposeResources() }
      .filter { it.parent?.name?.startsWith("values") == true }
      .mapNotNull { (PsiManager.getInstance(project).findFile(it) as? XmlFile)?.rootTag }
      .flatMap { it.findSubTags(ref.xmlTag).asSequence() }
      .filter { it.getAttributeValue("name") == ref.key }
      .toList()

  fun findXmlResourceTargets(
    project: Project,
    ref: ResourceReference.XmlResource,
  ): List<PsiElement> =
    findXmlResources(project, ref)
      .map { it.getAttribute("name")?.valueElement ?: it }

  fun findFileResources(
    project: Project,
    ref: ResourceReference.FileResource,
  ): List<PsiElement> {
    val psiManager = PsiManager.getInstance(project)
    val scope = GlobalSearchScope.projectScope(project)

    fun VirtualFile.isInResourceDir(): Boolean =
      isInComposeResources() && parent?.name?.startsWith(ref.dirPrefix) == true

    val xmlMatches = FileTypeIndex
      .getFiles(XmlFileType.INSTANCE, scope)
      .asSequence()
      .filter { it.isInResourceDir() }
      .filter { it.nameWithoutExtension == ref.key && it.extension in ref.extensions }
      .mapNotNull { psiManager.findFile(it)?.firstChild }

    val fileMatches = ref.extensions
      .asSequence()
      .filter { it != "xml" }
      .flatMap { ext ->
        FilenameIndex
          .getVirtualFilesByName("${ref.key}.$ext", scope)
          .asSequence()
          .filter { it.isInResourceDir() }
          .mapNotNull { psiManager.findFile(it) }
      }

    return (xmlMatches + fileMatches).toList()
  }

  fun findUsages(
    project: Project,
    ref: ResourceReference,
  ): List<PsiElement> {
    val resPrefix = when (ref) {
      is ResourceReference.XmlResource -> XML_TAG_TO_RES_PREFIX[ref.xmlTag] ?: return emptyList()
      is ResourceReference.FileResource -> "Res.${ref.dirPrefix}."
    }
    val resourceName = ref.key
    val fullRef = "$resPrefix$resourceName"
    val scope = GlobalSearchScope.projectScope(project)
    val searchHelper = PsiSearchHelper.getInstance(project)

    val usages = mutableListOf<PsiElement>()
    searchHelper.processElementsWithWord(
      { element, _ ->
        val file = element.containingFile?.virtualFile
        if (file != null && (file.path.contains("/build/") || file.path.contains("/.gradle/"))) {
          return@processElementsWithWord true
        }

        if (element is KtSimpleNameExpression && element.text == resourceName) {
          val parent = element.parent
          if (parent is KtDotQualifiedExpression && parent.text.endsWith(fullRef)) {
            usages.add(ResourceUsageTarget(parent))
          }
        }
        true
      },
      scope,
      resourceName,
      UsageSearchContext.IN_CODE,
      true,
    )

    return usages
  }
}

/**
 * A wrapper for a resource usage element that provides a custom presentation in navigation lists.
 * Displays the full resource expression (e.g. Res.string.app_name) and includes the file name
 * alongside the module name in the location string.
 */
@Suppress("UnstableApiUsage")
private data class ResourceUsageTarget(
  val element: PsiElement,
) : PsiElement by element,
  NavigationItem {
  override fun getPresentation(): ItemPresentation {
    return object : ItemPresentation {
      override fun getPresentableText(): String = element.text

      override fun getLocationString(): String {
        val module = ModuleUtilCore.findModuleForPsiElement(element)?.name
        val fileName = element.containingFile?.name ?: ""
        return if (module != null) "[$module] $fileName" else fileName
      }

      override fun getIcon(unused: Boolean): Icon? = element.getIcon(0)
    }
  }

  override fun getTextRangeInParent(): TextRange = element.textRangeInParent

  override fun getNavigationElement(): PsiElement = element

  override fun getOriginalElement(): PsiElement = element.originalElement

  override fun getOwnDeclarations(): Collection<PsiSymbolDeclaration> = element.ownDeclarations

  override fun getOwnReferences(): Collection<PsiSymbolReference> = element.ownReferences

  override fun getName(): String? = (element as? NavigationItem)?.name
}

internal fun VirtualFile.isInComposeResources(): Boolean {
  var current: VirtualFile? = this
  var inComposeResources = false
  while (current != null) {
    if (current.name == "composeResources") {
      inComposeResources = true
    }
    if (current.name == "build") {
      return false
    }
    current = current.parent
  }
  return inComposeResources
}
