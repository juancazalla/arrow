package arrow.fold

import arrow.Kind
import arrow.core.*
import arrow.data.*
import arrow.typeclasses.*

import arrow.common.messager.logE
import com.google.auto.service.AutoService
import arrow.common.utils.AbstractProcessor
import arrow.common.utils.asClassOrPackageDataWrapper
import arrow.common.utils.isSealed

import me.eugeniomarletti.kotlin.metadata.KotlinClassMetadata
import me.eugeniomarletti.kotlin.metadata.kotlinMetadata
import java.io.File
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.lang.model.element.TypeParameterElement

@AutoService(Processor::class)
class AutoFoldProcessor : AbstractProcessor(), MonadError<EitherPartialOf<GenerationError>, GenerationError> by Either.monadError() /*, Traverse<ForListK> by ListK.traverse()*/ {

  private val annotatedList = mutableListOf<Either<GenerationError, AnnotatedFold>>()
  override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()
  override fun getSupportedAnnotationTypes(): Set<String> = setOf(foldAnnotationClass.canonicalName)

  /**
   * Processor entry point
   */
  override fun onProcess(annotations: Set<TypeElement>, roundEnv: RoundEnvironment) {
    annotatedList += roundEnv
      .getElementsAnnotatedWith(foldAnnotationClass)
      .map { element ->
        when {
          element.let { it.kotlinMetadata as? KotlinClassMetadata }?.data?.classProto?.isSealed == true -> {
            val (nameResolver, classProto) = element.kotlinMetadata.let { it as KotlinClassMetadata }.data

            AnnotatedFold(
              element as TypeElement,
              element.typeParameters.map(TypeParameterElement::toString),
              element.kotlinMetadata
                .let { it as KotlinClassMetadata }
                .data
                .asClassOrPackageDataWrapper(elementUtils.getPackageOf(element).toString()),
              classProto.sealedSubclassFqNameList
                .map(nameResolver::getString)
                .map { it.replace('/', '.') }
                .map {
                  Variant(it,
                    elementUtils.getTypeElement(it).typeParameters.map(TypeParameterElement::toString),
                    it.substringAfterLast("."))
                }
            ).right()
          }

          else -> GenerationError("Generation of fold is only supported for sealed classes.", element).left()
        }
      }

    if (roundEnv.processingOver()) {
      val generatedDir = File(this.generatedDir!!, foldAnnotationClass.simpleName).also { it.mkdirs() }

      annotatedList.k().traverse(Either.applicative(), ::identity)
        .flatMap { folds: List<AnnotatedFold> ->
          folds.map(::processAutoFoldElement).k().traverse(Either.applicative(), ::identity)
        }.fix().fold(
          { error: GenerationError -> logE(error.message, error.element) },
          { results: List<AutoFoldResult> ->
            results.forEach { (_, name, content) -> File(generatedDir, name).writeText(content) }
          }
        )
    }
  }

}
