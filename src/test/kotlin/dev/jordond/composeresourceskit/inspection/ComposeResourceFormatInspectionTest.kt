package dev.jordond.composeresourceskit.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ComposeResourceFormatInspectionTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ComposeResourceFormatInspection())
  }

  private fun addComposeResource(
    path: String,
    content: String,
  ) {
    myFixture.addFileToProject("composeResources/$path", content)
  }

  fun testValidPositionalSpecifiers() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="greeting">Hello %1${'$'}s, you have %2${'$'}d items</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val formatErrors = highlights.filter {
      it.description?.contains("format specifier") == true ||
        it.description?.contains("Format specifier") == true
    }

    assertTrue("Valid specifiers should not be flagged", formatErrors.isEmpty())
  }

  fun testNoSpecifiers() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="plain">Hello world</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val formatErrors = highlights.filter {
      it.description?.contains("format specifier") == true ||
        it.description?.contains("Format specifier") == true
    }

    assertTrue("Plain string should not be flagged", formatErrors.isEmpty())
  }

  fun testUnpositionedPercentS() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="bad">Hello %s</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val formatErrors = highlights.filter {
      it.description?.contains("Invalid format specifier") == true
    }

    assertFalse("Unpositioned %s should be flagged", formatErrors.isEmpty())
  }

  fun testUnsupportedPercentF() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="bad_float">Value: %1${'$'}f</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val formatErrors = highlights.filter {
      it.description?.contains("Invalid format specifier") == true
    }

    assertFalse("Unsupported %1\$f should be flagged", formatErrors.isEmpty())
  }

  fun testNonSequentialNumbering() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="gap">%1${'$'}s and %3${'$'}s</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val gapWarnings = highlights.filter {
      it.description?.contains("not sequential") == true
    }

    assertFalse("Non-sequential numbering should be flagged", gapWarnings.isEmpty())
  }

  fun testConsistentPlurals() {
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

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val consistencyWarnings = highlights.filter {
      it.description?.contains("Plural variant") == true
    }

    assertTrue("Consistent plurals should not be flagged", consistencyWarnings.isEmpty())
  }

  fun testInconsistentPlurals() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <plurals name="items">
              <item quantity="one">%1${'$'}d item</item>
              <item quantity="other">%1${'$'}d items by %2${'$'}s</item>
          </plurals>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val consistencyWarnings = highlights.filter {
      it.description?.contains("Plural variant") == true
    }

    assertFalse("Inconsistent plural variants should be flagged", consistencyWarnings.isEmpty())
  }

  fun testIgnoresXmlOutsideComposeResources() {
    myFixture.addFileToProject(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="bad">Hello %s</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val formatErrors = highlights.filter {
      it.description?.contains("Invalid format specifier") == true
    }

    assertTrue("Should not inspect XML outside composeResources", formatErrors.isEmpty())
  }

  fun testQuickFixConvertsUnpositionedSpecifier() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="fix_me">Hello %s</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    myFixture.doHighlighting()

    val quickFix = myFixture.getAllQuickFixes().firstOrNull {
      it.text.contains("Convert") && it.text.contains("positional")
    }
    assertNotNull("Expected quick fix to convert specifier", quickFix)
    myFixture.launchAction(quickFix!!)

    val text = myFixture.editor.document.text
    assertTrue("Should contain positional specifier", text.contains("%1\$s"))
    assertFalse("Should not contain unpositioned specifier", Regex("""(?<![\d$])%s""").containsMatchIn(text))
  }

  fun testEscapedPercentNotFlagged() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="percent">100%% complete</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val formatErrors = highlights.filter {
      it.description?.contains("Invalid format specifier") == true
    }

    assertTrue("Escaped %% should not be flagged", formatErrors.isEmpty())
  }

  fun testDollarAmountNotFlagged() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="price">Price: ${'$'}10.00</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val formatErrors = highlights.filter {
      it.description?.contains("Invalid format specifier") == true ||
        it.description?.contains("Format specifier") == true
    }

    assertTrue("Dollar amount should not be flagged", formatErrors.isEmpty())
  }

  fun testTrailingPercentNotFlagged() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="pct">100%</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val formatErrors = highlights.filter {
      it.description?.contains("Invalid format specifier") == true ||
        it.description?.contains("Format specifier") == true
    }

    assertTrue("Trailing % without a letter should not be flagged", formatErrors.isEmpty())
  }

  fun testPercentSpaceWordNotFlagged() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="sale">50% off</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val formatErrors = highlights.filter {
      it.description?.contains("Invalid format specifier") == true ||
        it.description?.contains("Format specifier") == true
    }

    assertTrue("Percent followed by space then word should not be flagged", formatErrors.isEmpty())
  }

  fun testEscapedPercentImmediatelyFollowedByTextNotFlagged() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="compact">50%%off</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val formatErrors = highlights.filter {
      it.description?.contains("Invalid format specifier") == true
    }

    assertTrue("Escaped %% immediately followed by text should not be flagged", formatErrors.isEmpty())
  }

  fun testMixedDollarEscapedPercentAndValidSpecifier() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="complex">100%% of ${'$'}50 spent on %1${'$'}s</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val formatErrors = highlights.filter {
      it.description?.contains("Invalid format specifier") == true
    }

    assertTrue("Mixed content with valid specifier should not produce false positives", formatErrors.isEmpty())
  }

  fun testPercentInParenthesesNotFlagged() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="paren">(50%) discount</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val formatErrors = highlights.filter {
      it.description?.contains("Invalid format specifier") == true ||
        it.description?.contains("Format specifier") == true
    }

    assertTrue("Percent inside parentheses should not be flagged", formatErrors.isEmpty())
  }
}
