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

import com.android.build.api.dsl.LibraryExtension
import com.flixclusive.gradle.FLX_PROVIDER_EXTENSION_NAME
import com.flixclusive.gradle.FlixclusiveProviderExtension
import com.flixclusive.gradle.getFlixclusive
import com.flixclusive.gradle.task.AlignTask
import com.flixclusive.gradle.task.CompileDexTask.Companion.registerCompileDexTask
import com.flixclusive.gradle.task.CompileResourcesTask.Companion.registerCompileResourcesTask
import com.flixclusive.gradle.task.DeployWithAdbTask
import com.flixclusive.gradle.task.GenerateUpdaterJsonTask
import com.flixclusive.gradle.task.GenerateUpdaterJsonTask.Companion.registerGenerateUpdaterJsonTask
import com.flixclusive.gradle.util.Constants
import com.flixclusive.gradle.util.android
import com.flixclusive.gradle.util.configureAndroid
import com.flixclusive.gradle.util.createProviderManifest
import com.flixclusive.gradle.util.isValidFilename
import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

@Suppress("unused")
abstract class FlixclusiveProvider : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            extensions.create(
                FLX_PROVIDER_EXTENSION_NAME,
                FlixclusiveProviderExtension::class.java,
                project
            )


            extensions.configure<LibraryExtension> {
                configureAndroid(libraryExtension = this@configure)
            }

            registerTasks()
        }
    }

    private fun Project.registerTasks() {
        val extension = extensions.getFlixclusive()
        val intermediates = layout.buildDirectory.dir("intermediates")
        val providerClassFile = intermediates.get().file("providerClass")
        val compileDexTask = registerCompileDexTask(providerClassFile)
        val compileResourcesTask = registerCompileResourcesTask()
        registerGenerateUpdaterJsonTask()

        if (rootProject.tasks.findByName("generateUpdaterJson") == null) {
            rootProject.tasks.register<GenerateUpdaterJsonTask>("generateUpdaterJson") {
                group = Constants.TASK_GROUP

                outputs.upToDateWhen { false }
                outputFile.set(this@register.project.layout.buildDirectory.asFile.get().resolve("updater.json"))
            }
        }

        val packageTask = tasks.register<Zip>("package") {
            group = Constants.TASK_GROUP
            entryCompression = ZipEntryCompression.STORED
            isPreserveFileTimestamps = false
            archiveBaseName.set("$name-unaligned")
            archiveVersion.set("")
            archiveExtension.set(Constants.PROVIDER_EXTENSION)
            destinationDirectory.set(intermediates)

            val manifestFile = intermediates.get().file("manifest.json")
            from(manifestFile)
            doFirst {
                if (!isValidFilename(name)) {
                    throw IllegalStateException("Invalid project name: $name")
                }

                val (versionCode, _) = extension.getVersionDetails()
                require(versionCode > 0L) {
                    "No provider version is set"
                }

                if (extension.providerClassName == null) {
                    if (providerClassFile.asFile.exists()) {
                        extension.providerClassName = providerClassFile.asFile.readText()
                    }
                }

                require(extension.providerClassName != null) {
                    "No provider class found, make sure your provider class is annotated with @FlixclusiveProvider"
                }

                manifestFile.asFile.writeText(
                    Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    }.encodeToString(project.createProviderManifest())
                )
            }

            from(compileDexTask.map { it.outputs.files })

            if (extension.requiresResources) {
                val resourcesFile = compileResourcesTask.flatMap { it.outputFile }
                val resourcesFileTree = project.zipTree(resourcesFile)
                val resources = resourcesFile.map {
                    if (it.asFile.exists()) {
                        resourcesFileTree
                    } else {
                        emptyList()
                    }
                }

                from(resources) {
                    exclude("AndroidManifest.xml")
                }

                val mainSourceSet = android.sourceSets.getByName("main")
                from(mainSourceSet.assets.directories) {
                    into("assets")
                }
            }

        }

        val makeTask = tasks.register<AlignTask>("make") {
            group = Constants.TASK_GROUP
            inputZip.fileProvider(packageTask.map { it.outputs.files.singleFile })
            outputZip.set(layout.buildDirectory.file("${project.name}.${Constants.PROVIDER_EXTENSION}"))

            doLast {
                logger.lifecycle("Provider package ${name}.flx created at ${outputs.files.singleFile}")
            }
        }

        afterEvaluate {
            val rootGenerateUpdaterJsonTask = rootProject.tasks.findByName("generateUpdaterJson") as? GenerateUpdaterJsonTask

            tasks.register<DeployWithAdbTask>("deployWithAdb") {
                group = Constants.TASK_GROUP

                providerFile.fileProvider(makeTask.map { it.outputs.files.singleFile })
                rootGenerateUpdaterJsonTask?.let {
                    updaterJsonFile.set { it.outputs.files.singleFile }
                    dependsOn(makeTask, rootGenerateUpdaterJsonTask)
                }
            }
        }
    }
}