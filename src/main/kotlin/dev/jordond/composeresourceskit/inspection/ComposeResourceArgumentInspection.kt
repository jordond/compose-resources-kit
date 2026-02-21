package dev.jordond.composeresourceskit.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import dev.jordond.composeresourceskit.RESOURCE_PREFIXES
import dev.jordond.composeresourceskit.ResourceReference
import dev.jordond.composeresourceskit.ResourceResolver
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

private const val COMPOSE_RESOURCES_PACKAGE = "org.jetbrains.compose.resources"

private val TARGET_FUNCTIONS = mapOf(
  "stringResource" to FunctionKind.STRING_RESOURCE,
  "pluralStringResource" to FunctionKind.PLURAL_STRING_RESOURCE,
)

private enum class FunctionKind(
  /** Number of leading non-format arguments (resource ref, quantity, etc.) */
  val leadingArgCount: Int,
  val displayName: String,
) {
  STRING_RESOURCE(leadingArgCount = 1, displayName = "stringResource"),
  PLURAL_STRING_RESOURCE(leadingArgCount = 2, displayName = "pluralStringResource"),
}

class ComposeResourceArgumentInspection : LocalInspectionTool() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
  ): PsiElementVisitor {
    return object : KtVisitorVoid() {
      override fun visitCallExpression(expression: KtCallExpression) {
        val calleeText = expression.calleeExpression?.text ?: return
        if (calleeText !in TARGET_FUNCTIONS) return

        // Quick PSI-level check: does the first argument look like a resource reference?
        val args = expression.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0].getArgumentExpression() ?: return
        val isResourceRef = RESOURCE_PREFIXES.any { (prefix, _) ->
          firstArg.text.startsWith(prefix)
        }
        if (!isResourceRef) return

        // Resolve via K2 Analysis API to confirm it's the Compose Resources function
        val functionKind = resolveFunction(expression) ?: return

        // Extract the resource reference
        val resourceRef = extractResourceRef(firstArg) ?: return
        if (resourceRef !is ResourceReference.XmlResource) return

        // Look up the XML resource to count format specifiers
        val xmlTags = ResourceResolver.findXmlResources(expression.project, resourceRef)
        if (xmlTags.isEmpty()) return

        // Get the max format argument count across all matching tags (handles qualifiers)
        val expectedArgCount = xmlTags.maxOf { tag ->
          when (tag.name) {
            "plurals" -> {
              tag.findSubTags("item").maxOfOrNull { item ->
                FormatSpecifierParser.countFormatArguments(item.value.text)
              } ?: 0
            }
            else -> FormatSpecifierParser.countFormatArguments(tag.value.text)
          }
        }

        // Count format arguments at the call site
        val totalArgs = args.size
        val formatArgCount = (totalArgs - functionKind.leadingArgCount).coerceAtLeast(0)

        if (formatArgCount != expectedArgCount) {
          val message = buildMessage(functionKind, expectedArgCount, formatArgCount, resourceRef)
          holder.registerProblem(expression, message)
        }
      }
    }
  }

  /**
   * Uses K2 Analysis API to resolve the call and confirm it targets
   * a Compose Resources function (`stringResource` or `pluralStringResource`).
   *
   * Returns the [FunctionKind] if confirmed, null otherwise.
   */
  private fun resolveFunction(expression: KtCallExpression): FunctionKind? {
    return try {
      analyze(expression) {
        val call = expression
          .resolveToCall()
          ?.successfulFunctionCallOrNull() ?: return@analyze null

        val symbol = call.partiallyAppliedSymbol.symbol
        val callableId = symbol.callableId ?: return@analyze null

        if (callableId.className != null) return@analyze null
        if (callableId.packageName.asString() != COMPOSE_RESOURCES_PACKAGE) return@analyze null

        TARGET_FUNCTIONS[callableId.callableName.asString()]
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun extractResourceRef(expression: org.jetbrains.kotlin.psi.KtExpression): ResourceReference? {
    if (expression is KtDotQualifiedExpression) {
      return ResourceResolver.extractResourceReference(expression)
    }
    return null
  }

  private fun buildMessage(
    kind: FunctionKind,
    expected: Int,
    actual: Int,
    ref: ResourceReference.XmlResource,
  ): String {
    val base = "${kind.displayName}(Res.${resTypePrefix(ref)}${ref.key}) " +
      "expects $expected format argument${if (expected != 1) "s" else ""}, " +
      "but $actual ${if (actual != 1) "were" else "was"} provided"

    if (kind == FunctionKind.PLURAL_STRING_RESOURCE && actual == 0 && expected > 0) {
      return "$base. Note: the quantity parameter is NOT a format argument — " +
        "you likely need to pass it again as a format argument"
    }

    return base
  }

  private fun resTypePrefix(ref: ResourceReference.XmlResource): String =
    when (ref.xmlTag) {
      "string" -> "string."
      "string-array" -> "array."
      "plurals" -> "plurals."
      else -> "${ref.xmlTag}."
    }
}
