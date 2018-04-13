package arrow.fold

import arrow.Kind
import arrow.data.*
import arrow.typeclasses.*
import arrow.instances.*

import arrow.common.utils.KnownException
import arrow.common.utils.fullName
import arrow.common.utils.removeBackticks
import me.eugeniomarletti.kotlin.metadata.escapedClassName
import java.io.File
import javax.lang.model.element.Element

data class AutoFoldResult(val annotatedFold: AnnotatedFold, val fileName: String, val content: String)
data class GenerationError(val message: String, val element: Element)

fun <F> MonadError<F, GenerationError>.processAutoFoldElement(annotatedFold: AnnotatedFold): Kind<F, AutoFoldResult> =
  annotatedFold.targets.let { targets ->
    val sourceClassName = annotatedFold.classData.fullName.escapedClassName
    val sumTypeParams = typeParams(annotatedFold.typeParams)
    val returnType = getFoldReturnType(annotatedFold.typeParams)
    val functionTypeParams = functionTypeParams(annotatedFold.typeParams, returnType)

    binding {
      """
        |package ${annotatedFold.classData.`package`.escapedClassName}
        |
        |inline fun $functionTypeParams $sourceClassName$sumTypeParams.fold(
        |  ${params(targets, returnType, annotatedFold).bind().joinToString(separator = ",\n  ")}
        |): $returnType = when (this) {
        |  ${patternMatching(targets)}
        |}""".trimMargin()
    }
  }.map {
    AutoFoldResult(annotatedFold, "${foldAnnotationClass.simpleName}.${annotatedFold.type.simpleName.toString().toLowerCase()}.kt", it)
  }

private fun typeParams(params: List<String>): String =
  if (params.isNotEmpty()) params.joinToString(prefix = "<", postfix = ">") else ""

private fun getFoldReturnType(params: List<String>): String {
  fun check(param: String, next: List<String>): String = (param[0] + 1).let {
    if (next.contains(it.toString())) check(next.firstOrNull() ?: "", next.drop(1))
    else it.toString()
  }

  return if (params.isNotEmpty()) check(params.first(), params.drop(1)) else "A"
}

private fun functionTypeParams(params: List<String>, returnType: String): String =
  if (params.isEmpty()) "<$returnType>"
  else params.joinToString(prefix = "<", postfix = ", $returnType>")

private fun patternMatching(variants: List<Variant>): String = variants.joinToString(transform = { variant ->
  "is ${variant.fullName.escapedClassName} -> ${variant.simpleName.decapitalize().escapedClassName}(this)"
}, separator = "\n  ")

fun <F> MonadError<F, GenerationError>.params(variants: List<Variant>, returnType: String, annotatedFold: AnnotatedFold): Kind<F, ListK<String>> = with(ListK.traverse()) {
  variants.map { variant ->
    if (variant.typeParams.size > annotatedFold.typeParams.size) raiseError(generationError(annotatedFold, variant))
    else "crossinline ${variant.simpleName.decapitalize()}: (${variant.fullName.escapedClassName}${typeParams(variant.typeParams)}) -> $returnType".just()
  }.k().sequence(this@params).map { it.fix() }
}

private fun generationError(annotatedFold: AnnotatedFold, variant: Variant) = GenerationError("""
  |
  | @autofold cannot create a fold method for sealed class ${annotatedFold.classData.fullName.escapedClassName.removeBackticks()}
  | sealed class ${annotatedFold.classData.fullName.escapedClassName.removeBackticks()}${typeParams(annotatedFold.typeParams)}
  | ${" ".repeat("sealed class ${annotatedFold.classData.fullName.escapedClassName.removeBackticks()}".length)} ^ contains less generic information than variant
  |
  | ${variant.fullName.escapedClassName.removeBackticks()}${typeParams(variant.typeParams)}
  | ${" ".repeat(variant.fullName.escapedClassName.removeBackticks().length)} ^ Cannot check for instance of erased type
  """.trimMargin(), annotatedFold.type)
