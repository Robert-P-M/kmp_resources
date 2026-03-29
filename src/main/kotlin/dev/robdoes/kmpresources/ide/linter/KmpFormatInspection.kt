package dev.robdoes.kmpresources.ide.linter

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.infrastructure.resolver.KmpResourceResolver
import dev.robdoes.kmpresources.domain.model.ResourceType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

class KmpFormatInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {

            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                val callee = expression.calleeExpression ?: return
                val functionName = callee.text

                if (functionName != "stringResource" && functionName != "pluralStringResource") return

                val valueArguments = expression.valueArguments
                if (valueArguments.isEmpty()) return

                val resourceArg = valueArguments[0].getArgumentExpression() ?: return

                val resolved = KmpResourceResolver.resolveReference(resourceArg) ?: return
                val tags = KmpResourceResolver.findXmlTags(expression.project, resolved)
                if (tags.isEmpty()) return

                val xmlTag = tags.first()

                val expectedArgsCount = if (resolved.type == ResourceType.Plural) {
                    val items = xmlTag.findSubTags("item")
                    if (items.isEmpty()) {
                        0
                    } else {
                        items.maxOf { FormatArgumentAnalyzer.countRequiredArguments(it.value.text) }
                    }
                } else {
                    FormatArgumentAnalyzer.countRequiredArguments(xmlTag.value.text)
                }

                val providedArgsCount = if (functionName == "pluralStringResource") {
                    valueArguments.size - 2
                } else {
                    valueArguments.size - 1
                }

                val actualProvided = maxOf(0, providedArgsCount)

                if (expectedArgsCount != actualProvided) {
                    val message = KmpResourcesBundle.message(
                        "inspection.format.args.mismatch",
                        functionName,
                        expectedArgsCount,
                        actualProvided
                    )

                    holder.registerProblem(callee, message)
                }
            }
        }
    }
}