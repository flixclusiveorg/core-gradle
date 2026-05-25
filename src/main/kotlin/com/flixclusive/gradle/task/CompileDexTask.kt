/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.flixclusive.gradle.task

import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.options.SyncOptions.ErrorFormatMode
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexParameters
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.flixclusive.gradle.getFlixclusive
import com.flixclusive.gradle.util.Constants
import com.flixclusive.gradle.util.android
import com.flixclusive.gradle.util.androidComponents
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.util.stream.Collectors

abstract class CompileDexTask : DefaultTask() {
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    abstract val input: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputFile
    abstract val providerClassFile: RegularFileProperty

    @TaskAction
    fun compileDex() {
        val androidComponents = project.androidComponents
        val minSdk = project.android.defaultConfig.minSdk ?: Constants.MINIMUM_SDK_VERSION
        val globalSyntheticsDir =
            outputDir.get().asFile.resolve("global-synthetics").also { it.mkdirs() }
        val paths = androidComponents.sdkComponents.bootClasspath.get().map { file ->
            file.asFile.toPath()
        }

        val bootClasspath = ClassFileProviderFactory(paths)
        val classpath = ClassFileProviderFactory(listOf<Path>())

        val dexBuilder = DexArchiveBuilder.createD8DexBuilder(
            DexParameters(
                minSdkVersion = minSdk,
                debuggable = false,
                dexPerClass = false,
                withDesugaring = true,
                desugarBootclasspath = bootClasspath,
                desugarClasspath = classpath,
                coreLibDesugarConfig = null,
                enableApiModeling = true,
                messageReceiver = MessageReceiverImpl(
                    ErrorFormatMode.HUMAN_READABLE,
                    LoggerFactory.getLogger(CompileDexTask::class.java),
                )
            )
        )

        try {
            outputDir.get().asFile.mkdirs()

            val files = input.files
                .filter(File::exists)
                .filterNot {
                    DISALLOWED_BUNDLES.any { disallowed ->
                        it.absolutePath.contains(disallowed, ignoreCase = true)
                    }
                }

            files.forEach {
                logger.lifecycle("Adding ${it.absolutePath} to dex input")
            }

            val fileStreams = files
                .map { path ->
                    ClassFileInputs
                        .fromPath(path.toPath())
                        .entries { _, _ -> true }
                }
                .stream().flatMap { it }

            fileStreams.use { classesInput ->
                val files = classesInput.collect(Collectors.toList())

                dexBuilder.convert(
                    input = files.stream(),
                    dexOutput = outputDir.get().asFile.toPath(),
                    globalSyntheticsOutput = globalSyntheticsDir.toPath()
                )

                for (file in files) {
                    val reader = ClassReader(file.readAllBytes())

                    val classNode = ClassNode()
                    reader.accept(classNode, 0)

                    for (annotation in classNode.visibleAnnotations.orEmpty() + classNode.invisibleAnnotations.orEmpty()) {
                        if (annotation.desc == "Lcom/flixclusive/provider/FlixclusiveProvider;") {
                            val flixclusive = project.extensions.getFlixclusive()

                            require(flixclusive.providerClassName == null) {
                                "Only 1 active provider class per project is supported"
                            }

                            for (method in classNode.methods) {
                                if (method.name == "getManifest" && method.desc == "()Lcom/flixclusive/provider/ProviderManifest;") {
                                    throw IllegalArgumentException("Provider class cannot override getManifest, use manifest.json system!")
                                }
                            }

                            flixclusive.providerClassName = classNode.name.replace('/', '.')
                                .also { providerClassFile.asFile.orNull?.writeText(it) }
                        }
                    }
                }
            }

            logger.lifecycle("Compiled dex to ${outputDir.get()}")
        } finally {
            bootClasspath.close()
            classpath.close()
        }
    }

    companion object {
        private val DISALLOWED_BUNDLES = setOf("kotlin-stdlib")
        fun Project.registerCompileDexTask(providerClassFile: RegularFile): TaskProvider<CompileDexTask> {
            val intermediates = layout.buildDirectory.dir("intermediates")

            return tasks.register<CompileDexTask>("compileDex") {
                group = Constants.TASK_GROUP

                this@register.providerClassFile.set(providerClassFile)
                outputDir.set(intermediates.map { it.dir("dex") })

                val artifacts = configurations["debugRuntimeClasspath"]

                input.from(
                    artifacts.incoming
                        .artifactView {
                            attributes {
                                attribute(
                                    ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                                    ArtifactTypeDefinition.JAR_TYPE,
                                )
                            }
                        }
                        .files
                )

                input.from(tasks.named("compileDebugKotlin")) // exists but empty dir = no contribution
                input.from(
                    try {
                        tasks.named("compileDebugJavaWithJavac") // this is what actually feeds classes in
                    } catch (_: UnknownDomainObjectException) {
                        null
                    }
                )
            }
        }
    }
}