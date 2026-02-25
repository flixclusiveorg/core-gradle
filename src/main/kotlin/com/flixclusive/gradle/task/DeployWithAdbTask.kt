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
import com.flixclusive.gradle.getFlixclusive
import com.flixclusive.model.provider.Repository.Companion.toValidRepositoryLink
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import se.vidstige.jadb.AdbServerLauncher
import se.vidstige.jadb.JadbConnection
import se.vidstige.jadb.JadbDevice
import se.vidstige.jadb.JadbException
import se.vidstige.jadb.RemoteFile
import se.vidstige.jadb.Subprocess
import java.io.File
import java.nio.charset.StandardCharsets

internal abstract class DeployWithAdbTask : DefaultTask() {
    @get:Input
    @set:Option(option = "wait-for-debugger", description = "Enables debugging flag when starting the main activity")
    var waitForDebugger: Boolean = false

    @get:Input
    @set:Option(option = "debug-app", description = "Load the provider on the debug app")
    var debugApp: Boolean = false

    @TaskAction
    fun deployWithAdb() {
        val android = project.extensions.getByName("android") as BaseExtension

        AdbServerLauncher(Subprocess(), android.adbExecutable.absolutePath).launch()
        val jadbConnection = JadbConnection()
        val devices = jadbConnection.devices.filter {
            try {
                it.state == JadbDevice.State.Device
            } catch (_: JadbException) {
                false
            }
        }

        require(devices.size == 1) {
            "Only one ADB device should be connected, but ${devices.size} were!"
        }

        val device = devices[0]

        if (!pushProviderToLocalStorage(device, debugApp)) {
            return
        }

        val activityPath = if (debugApp) {
            "com.flixclusive.debug/com.flixclusive.mobile.MobileActivity"
        } else "com.flixclusive/com.flixclusive.mobile.MobileActivity"

        val args = arrayListOf("start", "-S", "-n", activityPath)

        if (waitForDebugger) {
            args.add("-D")
        }

        val response = String(
            device.executeShell("am", *args.toTypedArray()).readAllBytes(), StandardCharsets.UTF_8
        )

        if (response.contains("Error")) {
            logger.error(response)
        }

        logger.lifecycle("Deployed to ${device.serial}")
    }


    private fun pushProviderToLocalStorage(device: JadbDevice, isDebug: Boolean): Boolean {
        val makeTask = project.tasks.getByName("make") as AbstractCopyTask
        val generateUpdaterJsonTask = project.rootProject.tasks.getByName("generateUpdaterJson")

        val providerFile = makeTask.outputs.files.singleFile
        val updaterJson = generateUpdaterJsonTask.outputs.files.singleFile
        val repository = project.extensions.getFlixclusive().repositoryUrl?.toValidRepositoryLink()

        if (repository == null) {
            logger.error("Repository URL has not been set. Please set it on the project-level build.gradle.kts file")
            return false
        }

        val folderName = "${repository.owner}-${repository.name}"

        try {
            device.push(
                files = listOf(providerFile, updaterJson),
                sanitizedFolderName = folderName,
                isDebug = isDebug
            )
        } catch (_: JadbException) {
            device.push(
                files = listOf(providerFile, updaterJson),
                sanitizedFolderName = folderName,
                isDebug = isDebug,
                useOldStorage = true
            )
        }

        return true
    }

    private fun JadbDevice.push(
        files: List<File>,
        sanitizedFolderName: String,
        isDebug: Boolean,
        useOldStorage: Boolean = false
    ) {
        val initialPath = when (useOldStorage) {
            true -> if (isDebug) OLD_DEBUG_LOCAL_FILE_PATH else OLD_LOCAL_FILE_PATH
            false -> if (isDebug) DEBUG_LOCAL_FILE_PATH else LOCAL_FILE_PATH
        }

        files.forEach { file ->
            val remoteFilePath = "${initialPath}/${sanitizedFolderName}/${file.name}"
            push(file, RemoteFile(remoteFilePath))

            val fileName = files.first().nameWithoutExtension
            logger.lifecycle("$fileName have been pushed on $remoteFilePath.")
        }
    }

    companion object {
        private const val LOCAL_DEBUG_FILE_PATH_SUFFIX = "com.flixclusive.debug/files/providers/debug"
        private const val LOCAL_RELEASE_FILE_PATH_SUFFIX = "com.flixclusive/files/providers/debug"

        private const val OLD_DEBUG_LOCAL_FILE_PATH = "/sdcard/Android/data/$LOCAL_DEBUG_FILE_PATH_SUFFIX"
        private const val OLD_LOCAL_FILE_PATH = "/sdcard/Android/data/$LOCAL_RELEASE_FILE_PATH_SUFFIX"

        private const val DEBUG_LOCAL_FILE_PATH = "/storage/emulated/0/Android/data/$LOCAL_DEBUG_FILE_PATH_SUFFIX"
        private const val LOCAL_FILE_PATH = "/storage/emulated/0/Android/data/$LOCAL_RELEASE_FILE_PATH_SUFFIX"
    }
}