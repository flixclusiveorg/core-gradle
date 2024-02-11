package com.flixclusive.gradle.util

import com.flixclusive.gradle.entities.Language
import com.flixclusive.gradle.entities.PluginData
import com.flixclusive.gradle.entities.PluginManifest
import com.flixclusive.gradle.getFlixclusive
import org.gradle.api.Project

fun Project.createPluginManifest(): PluginManifest {
    val extension = this.extensions.getFlixclusive()

    require(extension.pluginClassName != null) {
        "No plugin class found, make sure your plugin class is annotated with @FlixclusivePlugin"
    }

    return PluginManifest(
        pluginClassName = extension.pluginClassName!!,
        name = name,
        version = version.toString(),
        updateUrl = extension.updateUrl.orNull,
        requiresResources = extension.requiresResources.get(),
    )
}

fun Project.createPluginData(): PluginData {
    val extension = extensions.getFlixclusive()

    return PluginData(
        buildUrl = extension.buildUrl.orNull?.let { String.format(it, name) },
        status = extension.status.get(),
        version = version.toString(),
        name = name,
        authors = extension.authors.getOrElse(emptyList()),
        description = extension.description.orNull,
        repositoryUrl = extension.repositoryUrl.orNull,
        language = extension.language.getOrElse(Language.Specific("en")),
        iconUrl = extension.iconUrl.orNull,
        pluginType = extension.pluginType.orNull,
        changelog = extension.changelog.orNull,
        changelogMedia = extension.changelogMedia.orNull
    )
}