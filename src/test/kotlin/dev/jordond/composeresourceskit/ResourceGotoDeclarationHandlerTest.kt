package dev.jordond.composeresourceskit

import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ResourceGotoDeclarationHandlerTest : BasePlatformTestCase() {
  private val handler = ResourceGotoDeclarationHandler()

  private val stringsXml =
    """
    <?xml version="1.0" encoding="utf-8"?>
    <resources>
        <string name="app_name">My App</string>
        <string name="greeting">Hello World</string>
        <string-array name="items">
            <item>One</item>
            <item>Two</item>
        </string-array>
        <plurals name="item_count">
            <item quantity="one">%d item</item>
            <item quantity="other">%d items</item>
        </plurals>
    </resources>
    """.trimIndent()

  private fun addComposeResource(
    path: String,
    content: String,
  ) {
    myFixture.addFileToProject("composeResources/$path", content)
  }

  private fun gotoTargets(kotlinCode: String): Array<PsiElement>? {
    myFixture.configureByText("Test.kt", kotlinCode)
    val element = myFixture.file.findElementAt(myFixture.caretOffset)
    return handler.getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
  }

  // region XML resource navigation (string, array, plurals)

  fun testNavigateToStringResource() {
    addComposeResource("values/strings.xml", stringsXml)
    val targets = gotoTargets("val x = Res.string.<caret>app_name")

    assertNotNull(targets)
    assertEquals(1, targets!!.size)
    assertInstanceOf(targets[0], XmlAttributeValue::class.java)
    assertEquals("app_name", (targets[0] as XmlAttributeValue).value)
  }

  fun testNavigateToSecondStringResource() {
    addComposeResource("values/strings.xml", stringsXml)
    val targets = gotoTargets("val x = Res.string.<caret>greeting")

    assertNotNull(targets)
    assertEquals(1, targets!!.size)
    assertInstanceOf(targets[0], XmlAttributeValue::class.java)
    assertEquals("greeting", (targets[0] as XmlAttributeValue).value)
  }

  fun testNavigateToStringArrayResource() {
    addComposeResource("values/strings.xml", stringsXml)
    val targets = gotoTargets("val x = Res.array.<caret>items")

    assertNotNull(targets)
    assertEquals(1, targets!!.size)
    assertInstanceOf(targets[0], XmlAttributeValue::class.java)
    assertEquals("items", (targets[0] as XmlAttributeValue).value)
  }

  fun testNavigateToPluralsResource() {
    addComposeResource("values/strings.xml", stringsXml)
    val targets = gotoTargets("val x = Res.plurals.<caret>item_count")

    assertNotNull(targets)
    assertEquals(1, targets!!.size)
    assertInstanceOf(targets[0], XmlAttributeValue::class.java)
    assertEquals("item_count", (targets[0] as XmlAttributeValue).value)
  }

  fun testStringResourceInMultipleLocales() {
    addComposeResource("values/strings.xml", stringsXml)
    addComposeResource(
      "values-fr/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="app_name">Mon App</string>
      </resources>
      """.trimIndent(),
    )
    val targets = gotoTargets("val x = Res.string.<caret>app_name")

    assertNotNull(targets)
    assertEquals(2, targets!!.size)
    assertTrue(targets.all { it is XmlAttributeValue })
  }

  // endregion

  // region File resource navigation (drawable, font)

  fun testNavigateToDrawablePng() {
    addComposeResource("drawable/icon.png", "fake-png-content")
    val targets = gotoTargets("val x = Res.drawable.<caret>icon")

    assertNotNull(targets)
    assertEquals(1, targets!!.size)
    assertEquals("icon.png", targets[0].containingFile.name)
  }

  fun testNavigateToDrawableXml() {
    addComposeResource(
      "drawable/background.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <vector xmlns:android="http://schemas.android.com/apk/res/android"/>
      """.trimIndent(),
    )
    val targets = gotoTargets("val x = Res.drawable.<caret>background")

    assertNotNull(targets)
    assertEquals(1, targets!!.size)
    assertEquals("background.xml", targets[0].containingFile.name)
  }

  fun testNavigateToFontTtf() {
    addComposeResource("font/roboto.ttf", "fake-ttf-content")
    val targets = gotoTargets("val x = Res.font.<caret>roboto")

    assertNotNull(targets)
    assertEquals(1, targets!!.size)
    assertEquals("roboto.ttf", targets[0].containingFile.name)
  }

  fun testNavigateToFontOtf() {
    addComposeResource("font/opensans.otf", "fake-otf-content")
    val targets = gotoTargets("val x = Res.font.<caret>opensans")

    assertNotNull(targets)
    assertEquals(1, targets!!.size)
    assertEquals("opensans.otf", targets[0].containingFile.name)
  }

  fun testNavigateToDrawableInQualifiedDirectory() {
    addComposeResource("drawable-hdpi/icon.png", "fake-png-content")
    val targets = gotoTargets("val x = Res.drawable.<caret>icon")

    assertNotNull(targets)
    assertEquals(1, targets!!.size)
    assertEquals("icon.png", targets[0].containingFile.name)
  }

  // endregion

  // region Scoping / exclusion

  fun testIgnoresResourcesOutsideComposeResources() {
    myFixture.addFileToProject("values/strings.xml", stringsXml)
    val targets = gotoTargets("val x = Res.string.<caret>app_name")

    assertNull(targets)
  }

  fun testIgnoresResourcesInBuildDirectory() {
    myFixture.addFileToProject(
      "build/generated/composeResources/values/strings.xml",
      stringsXml,
    )
    val targets = gotoTargets("val x = Res.string.<caret>app_name")

    assertNull(targets)
  }

  // endregion

  // region Negative / edge cases

  fun testUnknownResourceTypeReturnsNull() {
    addComposeResource("values/strings.xml", stringsXml)
    val targets = gotoTargets("val x = Res.raw.<caret>some_file")

    assertNull(targets)
  }

  fun testNonResourceExpressionReturnsNull() {
    val targets = gotoTargets("val x = foo.bar.<caret>baz")

    assertNull(targets)
  }

  fun testNonExistentResourceReturnsNull() {
    addComposeResource("values/strings.xml", stringsXml)
    val targets = gotoTargets("val x = Res.string.<caret>non_existent")

    assertNull(targets)
  }

  fun testNullSourceElementReturnsNull() {
    myFixture.configureByText("Test.kt", "val x = 1")
    val result = handler.getGotoDeclarationTargets(null, 0, myFixture.editor)

    assertNull(result)
  }

  // endregion
}
