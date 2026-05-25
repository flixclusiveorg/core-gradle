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

package com.flixclusive.gradle

import com.flixclusive.model.provider.Author
import com.flixclusive.model.provider.Language
import com.flixclusive.model.provider.ProviderStatus
import com.flixclusive.model.provider.ProviderType
import com.flixclusive.model.provider.Repository.Companion.toValidRepositoryLink
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.ListProperty
import javax.inject.Inject

const val FLX_PROVIDER_EXTENSION_NAME = "flxProvider"

@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class FlixclusiveProviderExtension @Inject constructor(val project: Project) {

    var buildBranch = "builds"

    var versionMajor = 0
    var versionMinor = 0
    var versionPatch = 0
    var versionBuild = 0

    internal var providerClassName: String? = null

    var repositoryUrl: String? = null
    var updateUrl: String? = null
    var buildUrl: String? = null

    /**
     *
     * [Author]s of the extension
     * */
    val authors: ListProperty<Author>
        = project.objects.listProperty(Author::class.java)

    /**
     * The **super fucking** unique identifier of the provider.
     *
     * **IMPORTANT:** The ID **MUST** be **super unique**.
     * Providers have no centralized database to track or validate IDs, meaning there is no guarantee that your chosen ID
     * has not been taken by another developer. This creates the possibility of ID conflicts, which can lead to serious
     * issues in functionality.
     *
     * This id will be used for provider updates, order management, and local storage purposes. Changing IDs on different builds/updates will cause update and backport issues. SO MAKE SURE YOUR ID IS A CONSTANT VALUE!
     *
     * **Guideline:** Put in significant effort to create a **VERY VERY UNIQUE ID** to minimize the risk of collisions.
     *
     * This ID must adhere to the following constraints:
     * - It must be at least 8-25 characters long.
     * - It must contain alphanumeric characters (letters and numbers).
     * - It can optionally include symbols for additional complexity.
     *
     * If the above constraints are not met, the build will *throw* an error, preventing further compilation.
     *
     * Lastly, IDs are re-hashed during build compilation to increase uniqueness.
     */
    var id: String? = null

    /**
     * Changelogs of the provider
     * */
    var changelog: String? = null

    /** The name of the provider. Defaults to the project name - [Project.getName]. */
    var providerName: String = project.name

    /** Determines whether the provider is an adult-only provider. Defaults to false. */
    var adult = false

    /** The provider's description */
    var description: String? = null
    /**
     * If your provider has an icon, put its image url here.
     *
     * This is an optional property.
     * */
    var iconUrl: String? = null
    /**
     * The main language of your provider.
     *
     * There are two supported values:
     * - Language.Multiple
     *      - Obviously for providers w/ multiple language support.
     * - Language("en")
     *      - For specific languages only. NOTE: Use the language's short-hand code.
     */
    var language: Language = Language(code = "en")

    /**
     * The main type that your provider supports.
     *
     * These are the possible values you could set:
     * - ProviderType.All
     * - ProviderType.TvShows
     * - ProviderType.Movies
     * - ProviderType(customType: String) // i.e., ProviderType("Anime")
     */
    var providerType: ProviderType = ProviderType(type = "Unknown")

    /**
     * Toggle this if this provider has its own resources. Defaults to false.
     */
    var requiresResources: Boolean = false

    /**
     * The current status of this provider. Defaults to Beta.
     *
     * These are the possible values you could set:
     * - ProviderStatus.Beta
     * - ProviderStatus.Down
     * - ProviderStatus.Working
     */
    var status: ProviderStatus = ProviderStatus.Beta

    /**
     *
     * Excludes this provider from the updater, meaning it won't show up for users.
     * Set this if the provider is still on beta. Defaults to false.
     * */
    var excludeFromUpdaterJson: Boolean = false

    @Deprecated("This has been deprecated. See https://github.com/flixclusiveorg/core-stubs for more details.")
    val userCache
        = project.gradle.gradleUserHomeDir.resolve("caches")

    /**
     * Adds an author to the list of authors.
     *
     * @param name The name of the author.
     * @param image The optional image or icon associated with the author.
     * @param socialLink The optional link associated with the author's social.
     */
    fun author(
        name: String,
        image: String? = null,
        socialLink: String? = null
    ) {
        authors.add(
            Author(
                name = name,
                image = image,
                socialLink = socialLink
            )
        )
    }

    /**
     * Sets the repository URL this provider belongs to
     *
     * @param url The url of the repository.
     */
    fun setRepository(url: String) {
        with(url.toValidRepositoryLink()) {
            updateUrl = getRawLink(filename = "updater.json", branch = buildBranch)
            buildUrl = getRawLink(filename = "%s.flx", branch = buildBranch)
            repositoryUrl = this@with.url
        }
    }


    /**
     * Calculates and returns version details.
     *
     * @return A Pair containing the version code (Long) and version name (String)
     * calculated using the [versionMajor], [versionMinor], [versionPatch], and
     * [versionBuild] properties.
     */
    fun getVersionDetails(): Pair<Long, String> {
        val versionCode = versionMajor * 10000L + versionMinor * 1000 + versionPatch * 100 + versionBuild
        val versionName = "${versionMajor}.${versionMinor}.${versionPatch}"

        return versionCode to versionName
    }
}


fun ExtensionContainer.getFlixclusive(): FlixclusiveProviderExtension {
    return getByName(FLX_PROVIDER_EXTENSION_NAME) as FlixclusiveProviderExtension
}

fun ExtensionContainer.findFlixclusive(): FlixclusiveProviderExtension? {
    return findByName(FLX_PROVIDER_EXTENSION_NAME) as FlixclusiveProviderExtension?
}