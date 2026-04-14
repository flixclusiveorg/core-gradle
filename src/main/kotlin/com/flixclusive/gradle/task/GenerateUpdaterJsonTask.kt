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

import com.flixclusive.gradle.findFlixclusive
import com.flixclusive.gradle.util.Constants
import com.flixclusive.gradle.util.createProviderMetadata
import com.flixclusive.model.provider.ProviderMetadata
import groovy.json.JsonBuilder
import groovy.json.JsonGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import java.util.LinkedList

internal abstract class GenerateUpdaterJsonTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private data class DuplicateInfo(
        val originalProvider: String,
        val duplicateProvider: String,
        val duplicatedId: String
    ) {
        override fun toString(): String {
            return "- Provider '$duplicateProvider' has same ID '$duplicatedId' as '$originalProvider'"
        }
    }

    @TaskAction
    fun generateUpdaterJson() {
        val list = mutableSetOf<ProviderMetadata>()
        val idToProvider = mutableMapOf<String, String>()
        val duplicates = mutableListOf<DuplicateInfo>()

        for (subproject in project.allprojects) {
            val flixclusive = subproject.extensions.findFlixclusive() ?: continue
            if (flixclusive.excludeFromUpdaterJson) continue

            val metadata = subproject.createProviderMetadata()

            if (!idToProvider.contains(metadata.id)) {
                idToProvider[metadata.id] = subproject.name
            } else {
                duplicates.add(
                    DuplicateInfo(
                        originalProvider = idToProvider[metadata.id]!!,
                        duplicateProvider = subproject.name,
                        duplicatedId = flixclusive.id!!
                    )
                )
            }

            list.add(metadata)
        }

        require(duplicates.isEmpty()) {
            buildString {
                appendLine("Found ${duplicates.size} providers with duplicate IDs:")
                duplicates.forEach {
                    appendLine(it.toString())
                }
            }
        }

        outputFile.asFile.get().writeText(
            JsonBuilder(
                /* content = */ list,
                /* generator = */ JsonGenerator.Options()
                    .excludeNulls()
                    .build()
            ).toPrettyString()
        )

        logger.lifecycle("Created ${outputFile.asFile.get()}")
    }

    companion object {
        fun Project.registerGenerateUpdaterJsonTask(): TaskProvider<GenerateUpdaterJsonTask> {
            return tasks.register("generateUpdaterJson", GenerateUpdaterJsonTask::class.java) {
                group = Constants.TASK_GROUP

                outputs.upToDateWhen { false }
                outputFile.set(layout.buildDirectory.file(Constants.UPDATER_JSON))
            }
        }
    }
}