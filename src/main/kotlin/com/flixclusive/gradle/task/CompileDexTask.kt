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

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.options.SyncOptions.ErrorFormatMode
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexParameters
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.flixclusive.gradle.getFlixclusive
import com.google.common.io.Closer
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.stream.Collectors

abstract class CompileDexTask : DefaultTask() {
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    val input: ConfigurableFileCollection = project.objects.fileCollection()

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:OutputFile
    abstract val providerClassFile: RegularFileProperty

    @TaskAction
    fun compileDex() {
        val android = project.extensions.getByName("android") as BaseExtension

        val minSdk = android.defaultConfig.minSdkVersion?.apiLevel ?: 21

        val dexOutputDir = outputFile.get().asFile.parentFile

        Closer.create().use { closer ->
            val dexBuilder = DexArchiveBuilder.createD8DexBuilder(
                DexParameters(
                    minSdkVersion = minSdk,
                    debuggable = true,
                    dexPerClass = false,
                    withDesugaring = true,
                    desugarBootclasspath = ClassFileProviderFactory(android.bootClasspath.map(File::toPath))
                        .also { closer.register(it) },
                    desugarClasspath = ClassFileProviderFactory(listOf<Path>()).also { closer.register(it) },
                    coreLibDesugarConfig = null,
                    enableApiModeling = true,
                    messageReceiver = MessageReceiverImpl(
                        ErrorFormatMode.HUMAN_READABLE,
                        LoggerFactory.getLogger(CompileDexTask::class.java),
                    )
                )
            )

            val fileStreams =
                input.map { input ->
                    val path = input.toPath()
                    ClassFileInputs.fromPath(path)
                        .use {
                            it.entries { _, _ -> true }
                        }
                }.toTypedArray()

            Arrays.stream(fileStreams).flatMap { it }
                .use { classesInput ->
                    val files = classesInput.collect(Collectors.toList())

                    dexBuilder.convert(
                        input = files.stream(),
                        globalSyntheticsOutput = null,
                        dexOutput = dexOutputDir.toPath()
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
        }

        logger.lifecycle("Compiled dex to ${outputFile.get()}")
    }
}