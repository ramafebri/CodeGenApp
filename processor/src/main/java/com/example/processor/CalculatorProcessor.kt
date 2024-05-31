package com.example.processor

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName

class CalculatorProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val logger = environment.logger

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Get all symbols with AutoGeneratedCalculator and SUM
        val symbols = resolver.getSymbolsWithAnnotation(AutoGeneratedCalculator::class.qualifiedName.orEmpty())
            .plus(resolver.getSymbolsWithAnnotation(SUM::class.qualifiedName.orEmpty()))
            .toSet()

        // Filter out symbols that are not classes
        symbols.filterIsInstance<KSClassDeclaration>().forEach { symbol ->
            (symbol as? KSClassDeclaration)?.let { classDeclaration ->
                if (classDeclaration.classKind == ClassKind.INTERFACE) {
                    val packageName = symbol.containingFile?.packageName?.asString().orEmpty()
                    val className = symbol.containingFile?.fileName?.replace(".kt", "").orEmpty()
                    val fileName = "${className}Impl"
                    val generatedClassName = "${className}Impl"
                    val interfaceName = symbol.asType(emptyList()).toTypeName()

                    val fileSpecBuilder = FileSpec.builder(packageName, generatedClassName)

                    // Create a class that implements calculator interface
                    val classBuilder = TypeSpec.classBuilder(generatedClassName)
                        .addSuperinterfaces(listOf(interfaceName))
                        .addPrintAnnotationFunction(symbol)
                        .addSumFunction(symbol)

                    fileSpecBuilder.addType(classBuilder.build())

                    // Write the file
                    environment.codeGenerator.createNewFile(
                        Dependencies(false, symbol.containingFile!!),
                        packageName,
                        fileName,
                        extensionName = "kt"
                    ).bufferedWriter().use {
                        fileSpecBuilder.build().writeTo(it)
                    }
                }
            }
        }

        // Filter out symbols that are not valid, e.g. annotated on a class
        val ret = symbols.filterNot { it.validate() }.toList()
        return ret
    }

    private fun TypeSpec.Builder.addPrintAnnotationFunction(symbol: KSAnnotated): TypeSpec.Builder {
        symbol.annotations.first {
            it.shortName.asString() == AutoGeneratedCalculator::class.simpleName
        }.let { annotation ->
            // Get the annotation message
            val message = annotation.arguments.first().value as String

            // Add a function that prints the annotation message
            this.addFunction(
                FunSpec.builder(name = "printAnnotation")
                    .addStatement("println(%S)", message)
                    .build()
            )
        }
        return this
    }

    private fun TypeSpec.Builder.addSumFunction(symbol: KSClassDeclaration): TypeSpec.Builder {
        // Get all functions with SUM annotation
        symbol.getDeclaredFunctions().forEach { func ->
            func.annotations.first { annotation ->
                annotation.shortName.asString() == SUM::class.simpleName
            }.let { annotation ->
                val firstParameter = func.parameters.first()
                val firstParameterName = firstParameter.name?.asString().orEmpty()
                val firstParameterType = firstParameter.type.resolve().toTypeName()

                val secondParameter = func.parameters.last()
                val secondParameterName = secondParameter.name?.asString().orEmpty()
                val secondParameterType = secondParameter.type.resolve().toTypeName()

                // Get the annotation message
                val message = annotation.arguments.first().value as String
                val returnType = func.returnType!!.resolve().toTypeName()

                // Add a function that prints the annotation message and returns the sum of two parameters
                this.addFunction(
                    FunSpec.builder(name = func.simpleName.asString())
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter(firstParameterName, firstParameterType)
                        .addParameter(secondParameterName, secondParameterType)
                        .returns(returnType)
                        .addStatement("println(%S)", message)
                        .addStatement("return $firstParameterName + $secondParameterName")
                        .build()
                )
            }
        }
        return this
    }
}