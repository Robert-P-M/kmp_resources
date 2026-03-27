package dev.robdoes.kmpresources.ide.linter

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class KmpFormatInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(KmpFormatInspection::class.java)

        // Setup the shared Dummy XML file once for all tests
        val xmlContent = """
            <resources>
                <string name="no_args">Hello World</string>
                <string name="one_arg">Hello %s</string>
                <plurals name="plural_args">
                    <item quantity="one">1 item</item>
                    <item quantity="other">%d items</item>
                </plurals>
            </resources>
        """.trimIndent()
        myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)
    }

    private fun getDummyKotlinSetup(): String = """
        package dev.robdoes.ui
        
        // --- DUMMY KMP DECLARATIONS ---
        object Res {
            object string {
                val no_args = "no_args"
                val one_arg = "one_arg"
            }
            object plurals {
                val plural_args = "plural_args"
            }
        }
        fun stringResource(resource: String, vararg formatArgs: Any): String = ""
        fun pluralStringResource(resource: String, quantity: Int, vararg formatArgs: Any): String = ""
        // ------------------------------
    """.trimIndent()

    fun testInspectionIgnoresCorrectUsages() {
        val kotlinContent = """
            ${getDummyKotlinSetup()}
            fun renderUi() {
                stringResource(Res.string.no_args)
                stringResource(Res.string.one_arg, "Rob")
                pluralStringResource(Res.plurals.plural_args, 5, 5) 
            }
        """.trimIndent()

        myFixture.configureByText("MainScreen.kt", kotlinContent)

        // Should not find any <error> tags
        myFixture.checkHighlighting(true, false, true)
    }

    fun testInspectionHighlightsMissingArguments() {
        val kotlinContent = """
            ${getDummyKotlinSetup()}
            fun renderUi() {
                <error descr="KMP Resource: 'stringResource' expects 1 format argument(s), but 0 were provided.">stringResource</error>(Res.string.one_arg)
                
                <error descr="KMP Resource: 'pluralStringResource' expects 1 format argument(s), but 0 were provided.">pluralStringResource</error>(Res.plurals.plural_args, 5)
            }
        """.trimIndent()

        myFixture.configureByText("MainScreen.kt", kotlinContent)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testInspectionHighlightsTooManyArguments() {
        val kotlinContent = """
            ${getDummyKotlinSetup()}
            fun renderUi() {
                // 'no_args' requires 0 args, but 1 is provided
                <error descr="KMP Resource: 'stringResource' expects 0 format argument(s), but 1 were provided.">stringResource</error>(Res.string.no_args, "Extra")
                
                // 'one_arg' requires 1 arg, but 2 are provided
                <error descr="KMP Resource: 'stringResource' expects 1 format argument(s), but 2 were provided.">stringResource</error>(Res.string.one_arg, "Rob", "Extra")
            }
        """.trimIndent()

        myFixture.configureByText("MainScreen.kt", kotlinContent)
        myFixture.checkHighlighting(true, false, true)
    }
}