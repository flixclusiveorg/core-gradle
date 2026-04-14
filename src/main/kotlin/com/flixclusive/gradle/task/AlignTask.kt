package com.flixclusive.gradle.task

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.repository.Revision
import com.android.sdklib.BuildToolInfo
import com.flixclusive.gradle.util.android
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.internal.ExecActionFactory
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Aligns a zip file to be loaded by Android.
 * This includes aligning native libraries, `resources.so`, and compiled dex files.
 */
abstract class AlignTask : DefaultTask() {
    @get:InputFile
    abstract val inputZip: RegularFileProperty

    @get:OutputFile
    abstract val outputZip: RegularFileProperty

    @get:Inject
    protected abstract val execActionFactory: ExecActionFactory

    private val zipAlignExecutable: Provider<File>

    init {
        val android = project.android

        val sdkService = getBuildService<SdkComponentsBuildService, SdkComponentsBuildService.Parameters>(
            buildServiceRegistry = project.gradle.sharedServices
        )
        val sdkLoader = sdkService.map {
            it.sdkLoader(
                compileSdkVersion = project.provider { "android-${android.compileSdk}" },
                buildToolsRevision = project.provider { Revision.parseRevision(android.buildToolsVersion) },
            )
        }

        usesService(sdkService)
        zipAlignExecutable = sdkLoader.flatMap { it.buildToolInfoProvider }
            .map { File(it.getPath(BuildToolInfo.PathId.ZIP_ALIGN)) }
    }

    @TaskAction
    fun align() {
        execActionFactory.newExecAction().run {
            executable = zipAlignExecutable.get().absolutePath
            args("-v") // Verbose output
            args("-f") // Overwrite existing
            args("-P", "16") // Align native libs to a 16KiB page size
            args("4") // Align all other files to 4 bytes
            args(inputZip.get().asFile.absolutePath)
            args(outputZip.get().asFile.absolutePath)

            val output = ByteArrayOutputStream()
            isIgnoreExitValue = true
            standardOutput = output
            errorOutput = output

            val result = execute()
            if (result.exitValue != 0) {
                logger.error(output.toString())
                result.assertNormalExitValue()
            } else {
                logger.info(output.toString())
            }
        }
    }
}