package dev.robdoes.kmpresources.ide.linter

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class KmpFormatInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // Enable our inspection in the simulated IDE
        myFixture.enableInspections(KmpFormatInspection())
    }

    fun testFormatInspectionHighlightsMissingArgumentsCorrectly() {
        // Arrange: 1. Create a virtual XML file with formatting arguments
        val xmlContent = """
            <resources>
                <string name="no_args">Hello World</string>
                <string name="one_arg">Hello %s</string>
                <string name="two_args">Hello %1${'$'}s, you have %2${'$'}d new messages</string>
                <plurals name="plural_args">
                    <item quantity="one">1 item</item>
                    <item quantity="other">%d items</item>
                </plurals>
            </resources>
        """.trimIndent()
        myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)

        // Arrange: 2. Create a Kotlin file using these resources.
        // We add DUMMY implementations of 'Res' and 'stringResource' so the
        // internal Kotlin compiler does not throw UNRESOLVED_REFERENCE errors.
        val kotlinContent = """
            package dev.robdoes.ui
            
            // --- DUMMY KMP DECLARATIONS TO SATISFY THE KOTLIN COMPILER ---
            object Res {
                object string {
                    val no_args = "no_args"
                    val one_arg = "one_arg"
                    val two_args = "two_args"
                }
                object plurals {
                    val plural_args = "plural_args"
                }
            }
            fun stringResource(resource: String, vararg formatArgs: Any): String = ""
            fun pluralStringResource(resource: String, quantity: Int, vararg formatArgs: Any): String = ""
            // -------------------------------------------------------------

            fun renderUi() {
                // --- CORRECT USAGES (Should NOT be highlighted) ---
                stringResource(Res.string.no_args)
                stringResource(Res.string.one_arg, "Rob")
                stringResource(Res.string.two_args, "Rob", 5)
                
                // For plurals: first arg is resource, second is quantity for logic, third+ are format args
                pluralStringResource(Res.plurals.plural_args, 5, 5) 

                // --- INCORRECT USAGES (Should BE highlighted) ---
                <error descr="KMP Resource: 'stringResource' expects 1 format argument(s), but 0 were provided.">stringResource(Res.string.one_arg)</error>
                
                <error descr="KMP Resource: 'stringResource' expects 2 format argument(s), but 1 were provided.">stringResource(Res.string.two_args, "Rob")</error>
                
                <error descr="KMP Resource: 'pluralStringResource' expects 1 format argument(s), but 0 were provided.">pluralStringResource(Res.plurals.plural_args, 5)</error>
            }
        """.trimIndent()

        myFixture.configureByText("MainScreen.kt", kotlinContent)

        // Act & Assert
        // Check if our specific KmpFormatInspection highlights match the <error> tags.
        myFixture.checkHighlighting(true, false, true)
    }
}