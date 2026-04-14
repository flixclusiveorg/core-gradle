package com.flixclusive.gradle.util

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.flixclusive.gradle.FlixclusiveProviderExtension
import com.flixclusive.gradle.getFlixclusive
import com.flixclusive.model.provider.ProviderManifest
import com.flixclusive.model.provider.ProviderMetadata
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByName
import java.security.MessageDigest


val Project.android get() = extensions.getByName("android") as CommonExtension

val Project.androidComponents: AndroidComponentsExtension<*, *, *>
    get() = extensions.getByName("androidComponents") as AndroidComponentsExtension<*, *, *>

fun Project.flxProvider(configuration: FlixclusiveProviderExtension.() -> Unit)
    = extensions.getFlixclusive().configuration()

fun Project.android(configuration: LibraryExtension.() -> Unit)
    = extensions.getByName<LibraryExtension>("android").configuration()

internal fun Project.createProviderManifest(): ProviderManifest {
    val extension = this.extensions.getFlixclusive()
    val (versionCode, versionName) = extension.getVersionDetails()

    require(extension.providerClassName != null) {
        "No provider class found, make sure your provider class is annotated with @FlixclusiveProvider"
    }

    validateId(name = name, id = extension.id)
    validateRepositoryUrl(
        buildBranch = extension.buildBranch,
        buildUrl = extension.buildUrl,
        repositoryUrl = extension.repositoryUrl
    )

    return ProviderManifest(
        id = generateId(
            repositoryUrl = extension.repositoryUrl!!,
            id = extension.id!!
        ),
        providerClassName = extension.providerClassName!!,
        name = name,
        versionName = versionName,
        versionCode = versionCode,
        updateUrl = extension.updateUrl,
        requiresResources = extension.requiresResources,
    )
}

internal fun Project.createProviderMetadata(): ProviderMetadata {
    val extension = extensions.getFlixclusive()
    val (versionCode, versionName) = extension.getVersionDetails()

    validateId(name = name, id = extension.id)
    validateRepositoryUrl(
        buildBranch = extension.buildBranch,
        buildUrl = extension.buildUrl,
        repositoryUrl = extension.repositoryUrl
    )

    return ProviderMetadata(
        id = generateId(
            repositoryUrl = extension.repositoryUrl!!,
            id = extension.id!!
        ),
        repositoryUrl = extension.repositoryUrl!!,
        buildUrl = extension.buildUrl!!.let { String.format(it, name) },
        status = extension.status,
        versionName = versionName,
        versionCode = versionCode,
        name = extension.providerName,
        adult = extension.adult,
        authors = extension.authors.getOrElse(emptyList()),
        description = extension.description,
        language = extension.language,
        iconUrl = extension.iconUrl,
        providerType = extension.providerType,
        changelog = extension.changelog,
    )
}

private const val MIN_ID_LENGTH = 8
private const val MAX_ID_LENGTH = 25
private fun validateId(name: String, id: String?) {
    require(id != null) {
        "The provider '${name}' does not have an ID set. Please ensure that a VERY unique value is specified for the `id` property in the provider's `build.gradle.kts` file."
    }

    require(id.length in MIN_ID_LENGTH..MAX_ID_LENGTH) {
        "The provider '${name}' has an ID that has a length of ${id.length}. IDs must have at least $MIN_ID_LENGTH-$MAX_ID_LENGTH characters length."
    }

    val bannedCharacters = Regex("[^\\p{Cc}\\\"\\\\\\u2028\\u2029]+?")
    require(bannedCharacters.matches(id)) {
        """
            The provider '${name}' has an ID character that is invalid. Make sure it does not contain the following characters:
            - Control characters (\p{Cc})
            - Double quotes (")
            - Backslash (\)
            - Line separator (\u2028 or \n)
            - Paragraph separator (\u2029)
        """.trimIndent()
    }
}

private fun validateRepositoryUrl(
    buildBranch: String,
    buildUrl: String?,
    repositoryUrl: String?
) {
    require(buildBranch.isNotBlank()) {
        "No build branch found, make sure you've set buildBranch on the provider's `build.gradle.kts`"
    }

    require(buildUrl != null) {
        "No build URL found, make sure you've set buildUrl or `setRepository` on the provider's `build.gradle.kts`"
    }

    require(repositoryUrl != null) {
        "No repository URL found, make sure you've set repositoryUrl or `setRepository` on the provider's `build.gradle.kts`"
    }
}

private fun generateId(repositoryUrl: String, id: String): String {
    val dataToHash = listOfNotNull(
        repositoryUrl,
        id,
    ).joinToString("|")
    val digest = MessageDigest.getInstance("SHA-1")
    val hashBytes = digest.digest(dataToHash.toByteArray())
    return hashBytes.toHexString().take(15)
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }