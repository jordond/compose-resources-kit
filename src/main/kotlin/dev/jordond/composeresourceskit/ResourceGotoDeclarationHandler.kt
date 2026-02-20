package dev.jordond.composeresourceskit

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

private val RESOURCE_KEY_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*".toRegex()
private val DRAWABLE_EXTENSIONS = listOf("png", "jpg", "jpeg", "bmp", "webp", "xml", "svg")
private val FONT_EXTENSIONS = listOf("ttf", "otf")

private sealed interface ResourceReference {
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

private val RESOURCE_PREFIXES: List<Pair<String, (String) -> ResourceReference>> = listOf(
  "Res.string." to { key -> ResourceReference.XmlResource(key, "string") },
  "Res.array." to { key -> ResourceReference.XmlResource(key, "string-array") },
  "Res.plurals." to { key -> ResourceReference.XmlResource(key, "plurals") },
  "Res.drawable." to { key -> ResourceReference.FileResource(key, "drawable", DRAWABLE_EXTENSIONS) },
  "Res.font." to { key -> ResourceReference.FileResource(key, "font", FONT_EXTENSIONS) },
)

class ResourceGotoDeclarationHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(
    sourceElement: PsiElement?,
    offset: Int,
    editor: Editor,
  ): Array<PsiElement>? {
    val project = sourceElement?.project ?: return null
    val ref = resolveResourceReference(sourceElement) ?: return null

    val targets = when (ref) {
      is ResourceReference.XmlResource -> findXmlResources(project, ref)
      is ResourceReference.FileResource -> findFileResources(project, ref)
    }

    return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
  }

  override fun getActionText(context: DataContext): String = "Go to Compose Resource"

  private fun resolveResourceReference(element: PsiElement): ResourceReference? {
    if (element is KtNameReferenceExpression) {
      val qualified = element.getQualifiedExpressionForSelector()
      if (qualified != null) return extractResourceReference(qualified)
    }

    val dotQualified = generateSequence(element) { it.parent }
      .filterIsInstance<KtDotQualifiedExpression>()
      .firstOrNull()

    return dotQualified?.let { extractResourceReference(it) }
  }

  private fun extractResourceReference(expression: KtExpression): ResourceReference? {
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

  private fun findXmlResources(
    project: Project,
    ref: ResourceReference.XmlResource,
  ): List<PsiElement> {
    val psiManager = PsiManager.getInstance(project)
    return FileTypeIndex
      .getFiles(XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project))
      .asSequence()
      .filter { it.isInComposeResources() }
      .filter { it.parent?.name?.startsWith("values") == true }
      .mapNotNull { (psiManager.findFile(it) as? XmlFile)?.rootTag }
      .flatMap { it.findSubTags(ref.xmlTag).asSequence() }
      .filter { it.getAttributeValue("name") == ref.key }
      .map { it.getAttribute("name")?.valueElement ?: it }
      .toList()
  }

  private fun findFileResources(
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

  private fun VirtualFile.isInComposeResources(): Boolean {
    val path = this.path
    return path.contains("/composeResources/") && !path.contains("/build/")
  }
}
