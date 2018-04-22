package org.javacs.kt

import org.javacs.kt.position.position
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.KotlinType
import org.javacs.kt.util.toPath
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.descriptors.ModuleDescriptor

/**
 * @param content Current contents of the entire file
 * @param parsed An element that surrounds the cursor
 * @param compiled Result of type-checking `parsed`
 * @param cursor The user's cursor
 * @param textOffset Offset between the coordinate system of `parsed` and the coordinates of `cursor`
 */
class CompiledCode(
        val content: String,
        val parsed: KtElement,
        val compiled: BindingContext,
        val container: ComponentProvider,
        val cursor: Int,
        val textOffset: Int,
        private val compiler: Compiler,
        private val sourcePath: Collection<KtFile>) {

    /**
     * Convert an offset from relative to the cursor, to relative to the start of parsed.
     * The result of offset is suitable for calling parsed.findElementAt(offset(?))
     * Note that this is NOT the same as the coordinate system of parsed,
     * because parsed may be embedded in a synthetic surrounding expression for the purpose of compilation
     */
    fun offset(relativeToCursor: Int): Int = cursor - textOffset + relativeToCursor

    fun lineBeforeCursor(): String = content.substring(0, cursor).substringAfterLast('\n')

    /**
     * If we're having trouble figuring out the type of an expression,
     * try re-parsing and re-analyzing just the difficult expression
     */
    fun robustType(expr: KtExpression): KotlinType? {
        val scope = findScope(expr) ?: return null
        val parse = compiler.createExpression(expr.text)
        val (analyze, _) = compiler.compileExpression(parse, scope, sourcePath)

        return analyze.getType(parse)
    }

    /**
     * Find the nearest lexical around an expression
     */
    fun findScope(expr: KtElement) =
            expr.parentsWithSelf
                    .filterIsInstance<KtElement>()
                    .mapNotNull { compiled.get(BindingContext.LEXICAL_SCOPE, it) }
                    .firstOrNull()

    fun describePosition(relativeToCursor: Int): String {
        val abs = offset(relativeToCursor)
        val pos = position(content, abs)
        val file = parsed.containingKtFile.toPath().fileName

        return "$file ${pos.line}:${pos.character}"
    }
}