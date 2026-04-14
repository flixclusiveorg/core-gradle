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

import com.android.build.gradle.tasks.ProcessLibraryManifest
import com.flixclusive.gradle.util.Constants
import com.flixclusive.gradle.util.android
import com.flixclusive.gradle.util.androidComponents
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import java.io.File

internal abstract class CompileResourcesTask : Exec() {
    @get:InputDirectory
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    abstract val input: DirectoryProperty

    @get:InputFile
    abstract val manifestFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    override fun exec() {
        val androidComponents = project.androidComponents

        val aaptExecutable = androidComponents.sdkComponents.aapt2.get().executable.get().asFile
        val androidJar =  androidComponents.sdkComponents.sdkDirectory.get().asFile
            .resolve("platforms")
            .resolve("android-${project.android.compileSdk}")
            .resolve("android.jar")

        if (!aaptExecutable.exists()) {
            throw GradleException("aapt2 executable not found at ${aaptExecutable.path}")
        }

        if (!androidJar.exists()) {
            throw GradleException("android.jar not found at ${androidJar.path}")
        }

        val tmpRes = File.createTempFile("res", ".zip")

        execActionFactory.newExecAction().apply {
            executable = aaptExecutable.path
            args("compile")
            args("--dir", input.asFile.get().path)
            args("-o", tmpRes.path)
            execute()
        }

        execActionFactory.newExecAction().apply {
            executable = aaptExecutable.path
            args("link")
            args("-I", androidJar.path)
            args("-R", tmpRes.path)
            args("--manifest", manifestFile.asFile.get().path)
            args("-o", outputFile.asFile.get().path)
            args("--auto-add-overlay")
            execute()
        }

        tmpRes.delete()
    }

    companion object {
        fun Project.registerCompileResourcesTask(): TaskProvider<CompileResourcesTask> {
            val intermediates = project.layout.buildDirectory.dir("intermediates")

            return tasks.register<CompileResourcesTask>("compileResources") {
                val processManifestTask = project.tasks.named<ProcessLibraryManifest>("processDebugManifest")

                group = Constants.TASK_GROUP

                val inputFiles = android.sourceSets.getByName("main")
                    .res.directories
                    .map { File(it) }
                    .single()

                input.set(inputFiles)
                manifestFile.set(processManifestTask.flatMap { it.manifestOutputFile })
                outputFile.set(intermediates.map { it.file("res.apk") })
            }
        }
    }
}