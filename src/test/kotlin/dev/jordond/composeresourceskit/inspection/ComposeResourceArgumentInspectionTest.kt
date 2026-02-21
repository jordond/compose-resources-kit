package dev.jordond.composeresourceskit.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ComposeResourceArgumentInspectionTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ComposeResourceArgumentInspection())
    addComposeResourcesStubs()
  }

  /**
   * Adds source stubs for the Compose Resources library types and functions
   * so that K2 Analysis API can resolve calls in test fixtures.
   */
  private fun addComposeResourcesStubs() {
    myFixture.addFileToProject(
      "stubs/StringResource.kt",
      """
      package org.jetbrains.compose.resources

      class StringResource(val id: String)
      class PluralStringResource(val id: String)

      fun stringResource(resource: StringResource): String = ""
      fun stringResource(resource: StringResource, vararg formatArgs: Any): String = ""
      fun pluralStringResource(resource: PluralStringResource, quantity: Int): String = ""
      fun pluralStringResource(resource: PluralStringResource, quantity: Int, vararg formatArgs: Any): String = ""
      """.trimIndent(),
    )
  }

  /**
   * Adds a stub Res object with typed resource accessors for the given resource names.
   */
  private fun addResStub(
    strings: List<String> = emptyList(),
    plurals: List<String> = emptyList(),
  ) {
    val stringFields = strings.joinToString("\n        ") {
      "val $it = org.jetbrains.compose.resources.StringResource(\"$it\")"
    }
    val pluralFields = plurals.joinToString("\n        ") {
      "val $it = org.jetbrains.compose.resources.PluralStringResource(\"$it\")"
    }

    myFixture.addFileToProject(
      "stubs/Res.kt",
      """
      object Res {
          object string {
              $stringFields
          }
          object plurals {
              $pluralFields
          }
      }
      """.trimIndent(),
    )
  }

  private fun addComposeResource(
    path: String,
    content: String,
  ) {
    myFixture.addFileToProject("composeResources/$path", content)
  }

  // -- stringResource: correct argument counts --

  fun testStringResourceNoArgsNeededNonePassed() {
    addResStub(strings = listOf("no_args"))
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="no_args">Hello world</string>
      </resources>
      """.trimIndent(),
    )

    myFixture.configureByText(
      "Test.kt",
      """
      import org.jetbrains.compose.resources.stringResource

      fun test() {
          stringResource(Res.string.no_args)
      }
      """.trimIndent(),
    )

    val highlights = myFixture.doHighlighting()
    val argMismatches = highlights.filter {
      it.description?.contains("format argument") == true
    }

    assertTrue("No args needed, none passed should be OK", argMismatches.isEmpty())
  }

  fun testStringResourceOneArgNeededOnePassed() {
    addResStub(strings = listOf("one_arg"))
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="one_arg">Hello %1${'$'}s</string>
      </resources>
      """.trimIndent(),
    )

    myFixture.configureByText(
      "Test.kt",
      """
      import org.jetbrains.compose.resources.stringResource

      fun test() {
          stringResource(Res.string.one_arg, "world")
      }
      """.trimIndent(),
    )

    val highlights = myFixture.doHighlighting()
    val argMismatches = highlights.filter {
      it.description?.contains("format argument") == true
    }

    assertTrue("1 arg needed, 1 passed should be OK", argMismatches.isEmpty())
  }

  // -- stringResource: argument mismatches --

  fun testStringResourceOneArgNeededNonePassed() {
    addResStub(strings = listOf("one_arg"))
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="one_arg">Hello %1${'$'}s</string>
      </resources>
      """.trimIndent(),
    )

    myFixture.configureByText(
      "Test.kt",
      """
      import org.jetbrains.compose.resources.stringResource

      fun test() {
          stringResource(Res.string.one_arg)
      }
      """.trimIndent(),
    )

    val highlights = myFixture.doHighlighting()
    val argMismatches = highlights.filter {
      it.description?.contains("format argument") == true
    }

    assertFalse("1 arg needed, 0 passed should be flagged", argMismatches.isEmpty())
  }

  fun testStringResourceTwoArgsNeededOnePassed() {
    addResStub(strings = listOf("two_args"))
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="two_args">Hello %1${'$'}s, you have %2${'$'}d items</string>
      </resources>
      """.trimIndent(),
    )

    myFixture.configureByText(
      "Test.kt",
      """
      import org.jetbrains.compose.resources.stringResource

      fun test() {
          stringResource(Res.string.two_args, "world")
      }
      """.trimIndent(),
    )

    val highlights = myFixture.doHighlighting()
    val argMismatches = highlights.filter {
      it.description?.contains("format argument") == true
    }

    assertFalse("2 args needed, 1 passed should be flagged", argMismatches.isEmpty())
  }

  // -- pluralStringResource --

  fun testPluralStringResourceCorrect() {
    addResStub(plurals = listOf("items"))
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <plurals name="items">
              <item quantity="one">%1${'$'}d item</item>
              <item quantity="other">%1${'$'}d items</item>
          </plurals>
      </resources>
      """.trimIndent(),
    )

    myFixture.configureByText(
      "Test.kt",
      """
      import org.jetbrains.compose.resources.pluralStringResource

      fun test() {
          val count = 5
          pluralStringResource(Res.plurals.items, count, count)
      }
      """.trimIndent(),
    )

    val highlights = myFixture.doHighlighting()
    val argMismatches = highlights.filter {
      it.description?.contains("format argument") == true
    }

    assertTrue("Correct plural args should be OK", argMismatches.isEmpty())
  }

  fun testPluralStringResourceMissingFormatArg() {
    addResStub(plurals = listOf("items"))
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <plurals name="items">
              <item quantity="one">%1${'$'}d item</item>
              <item quantity="other">%1${'$'}d items</item>
          </plurals>
      </resources>
      """.trimIndent(),
    )

    myFixture.configureByText(
      "Test.kt",
      """
      import org.jetbrains.compose.resources.pluralStringResource

      fun test() {
          val count = 5
          pluralStringResource(Res.plurals.items, count)
      }
      """.trimIndent(),
    )

    val highlights = myFixture.doHighlighting()
    val argMismatches = highlights.filter {
      it.description?.contains("format argument") == true
    }

    assertFalse(
      "Missing format arg (only quantity passed) should be flagged",
      argMismatches.isEmpty(),
    )
  }

  fun testPluralStringResourceQuantityHint() {
    addResStub(plurals = listOf("items"))
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <plurals name="items">
              <item quantity="one">%1${'$'}d item</item>
              <item quantity="other">%1${'$'}d items</item>
          </plurals>
      </resources>
      """.trimIndent(),
    )

    myFixture.configureByText(
      "Test.kt",
      """
      import org.jetbrains.compose.resources.pluralStringResource

      fun test() {
          val count = 5
          pluralStringResource(Res.plurals.items, count)
      }
      """.trimIndent(),
    )

    val highlights = myFixture.doHighlighting()
    val quantityHints = highlights.filter {
      it.description?.contains("quantity parameter is NOT a format argument") == true
    }

    assertFalse(
      "Should include hint about quantity not being a format arg",
      quantityHints.isEmpty(),
    )
  }

  // -- Non-resource calls should be ignored --

  fun testIgnoresNonResourceCalls() {
    myFixture.configureByText(
      "Test.kt",
      """
      fun stringResource(key: String): String = key
      fun test() {
          stringResource("hello")
      }
      """.trimIndent(),
    )

    val highlights = myFixture.doHighlighting()
    val argMismatches = highlights.filter {
      it.description?.contains("format argument") == true
    }

    assertTrue("Non-compose stringResource calls should be ignored", argMismatches.isEmpty())
  }
}
