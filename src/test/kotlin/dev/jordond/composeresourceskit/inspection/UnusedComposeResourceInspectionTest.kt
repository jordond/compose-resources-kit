package dev.jordond.composeresourceskit.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class UnusedComposeResourceInspectionTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(UnusedComposeResourceInspection())
  }

  private fun addComposeResource(
    path: String,
    content: String,
  ) {
    myFixture.addFileToProject("composeResources/$path", content)
  }

  // -- Unused detection --

  fun testUnusedStringHighlighted() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="unused_string">Not used anywhere</string>
      </resources>
      """.trimIndent(),
    )

    myFixture.configureByText("Test.kt", "val x = 1")

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val unusedWarnings = highlights.filter {
      it.description?.contains("Unused Compose resource") == true
    }

    assertFalse("Expected unused resource warning", unusedWarnings.isEmpty())
  }

  fun testUsedStringNotHighlighted() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="app_name">My App</string>
      </resources>
      """.trimIndent(),
    )

    myFixture.addFileToProject("src/Main.kt", "val x = Res.string.app_name")

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val unusedWarnings = highlights.filter {
      it.description?.contains("Unused Compose resource") == true
    }

    assertTrue("Should not flag used resource", unusedWarnings.isEmpty())
  }

  fun testUnusedStringArrayHighlighted() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string-array name="unused_array">
              <item>One</item>
              <item>Two</item>
          </string-array>
      </resources>
      """.trimIndent(),
    )

    myFixture.configureByText("Test.kt", "val x = 1")

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val unusedWarnings = highlights.filter {
      it.description?.contains("Unused Compose resource") == true
    }

    assertFalse("Expected unused resource warning for string-array", unusedWarnings.isEmpty())
  }

  fun testUsedStringArrayNotHighlighted() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string-array name="colors">
              <item>Red</item>
              <item>Blue</item>
          </string-array>
      </resources>
      """.trimIndent(),
    )

    myFixture.addFileToProject("src/Main.kt", "val x = Res.array.colors")

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val unusedWarnings = highlights.filter {
      it.description?.contains("Unused Compose resource") == true
    }

    assertTrue("Should not flag used string-array", unusedWarnings.isEmpty())
  }

  fun testUnusedPluralsHighlighted() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <plurals name="unused_plural">
              <item quantity="one">%1${'$'}d item</item>
              <item quantity="other">%1${'$'}d items</item>
          </plurals>
      </resources>
      """.trimIndent(),
    )

    myFixture.configureByText("Test.kt", "val x = 1")

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val unusedWarnings = highlights.filter {
      it.description?.contains("Unused Compose resource") == true
    }

    assertFalse("Expected unused resource warning for plurals", unusedWarnings.isEmpty())
  }

  fun testUsedPluralsNotHighlighted() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <plurals name="item_count">
              <item quantity="one">%1${'$'}d item</item>
              <item quantity="other">%1${'$'}d items</item>
          </plurals>
      </resources>
      """.trimIndent(),
    )

    myFixture.addFileToProject("src/Main.kt", "val x = Res.plurals.item_count")

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val unusedWarnings = highlights.filter {
      it.description?.contains("Unused Compose resource") == true
    }

    assertTrue("Should not flag used plurals", unusedWarnings.isEmpty())
  }

  // -- Ignores non-compose resources --

  fun testIgnoresXmlOutsideComposeResources() {
    myFixture.addFileToProject(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="some_string">Value</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val unusedWarnings = highlights.filter {
      it.description?.contains("Unused Compose resource") == true
    }

    assertTrue("Should not inspect XML outside composeResources", unusedWarnings.isEmpty())
  }

  // -- Mixed used/unused --

  fun testMixedUsedAndUnusedResources() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="used_one">Used</string>
          <string name="unused_one">Not used</string>
      </resources>
      """.trimIndent(),
    )

    myFixture.addFileToProject("src/Main.kt", "val x = Res.string.used_one")

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val unusedWarnings = highlights.filter {
      it.description?.contains("Unused Compose resource") == true
    }

    assertEquals("Only the unused resource should be flagged", 1, unusedWarnings.size)
    assertTrue(unusedWarnings[0].description!!.contains("unused_one"))
  }

  fun testSimilarNamesConflictReproduction() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="home">Home</string>
          <string name="home_action">Home Action</string>
      </resources>
      """.trimIndent(),
    )

    // Using home_action should NOT count as using home
    myFixture.addFileToProject("src/Main.kt", "val x = Res.string.home_action")

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val unusedWarnings = highlights.filter {
      it.description?.contains("Unused Compose resource") == true
    }

    // Bug: current implementation will see "Res.string.home_action" and think "Res.string.home" is used.
    // So unusedWarnings will be empty or not contain "home".
    val homeWarning = unusedWarnings.find { it.description!!.contains("'home'") }
    assertNotNull("Resource 'home' should be flagged as unused even if 'home_action' is used", homeWarning)
  }

  // -- Quick fix --

  fun testQuickFixRemovesTag() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="unused_string">Not used</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)

    val highlights = myFixture.doHighlighting()
    val unusedWarnings = highlights.filter {
      it.description?.contains("Unused Compose resource") == true
    }

    assertFalse("Expected warning to be present", unusedWarnings.isEmpty())

    // Apply the quick fix
    val intention = myFixture.getAllQuickFixes().firstOrNull { it.text == "Remove unused resource" }
    assertNotNull("Expected 'Remove unused resource' quick fix", intention)
    myFixture.launchAction(intention!!)

    // Verify the tag was removed
    val text = myFixture.editor.document.text
    assertFalse("Tag should be removed", text.contains("unused_string"))
  }
}
