package com.nyora.hasan72341.sdk

import com.nyora.hasan72341.shared.extension.JvmExtensionRuntime
import com.nyora.hasan72341.shared.extension.MangaDetails
import com.nyora.hasan72341.shared.extension.MangaExtensionService
import com.nyora.hasan72341.shared.extension.MangaSearchPage
import com.nyora.hasan72341.shared.extension.SourceFilter
import com.nyora.hasan72341.shared.extension.SourceFilterDescriptor
import com.nyora.hasan72341.shared.extension.nativeParserCatalog
import com.nyora.hasan72341.shared.model.Manga
import com.nyora.hasan72341.shared.model.MangaChapter
import com.nyora.hasan72341.shared.model.MangaPage
import com.nyora.hasan72341.shared.model.MangaSource
import com.nyora.hasan72341.shared.net.HelperNetworkConfig
import kotlinx.coroutines.runBlocking

/**
 * In-process Nyora sources SDK for the JVM (Java & Kotlin).
 *
 * This runs the native kotatsu-parsers engine **directly in your process** — no
 * HTTP server, no cloud. Construct once and reuse; parser services are cached
 * internally per source.
 *
 * The browse/details/pages methods are `suspend` under the hood but are exposed
 * here as ordinary **blocking** calls (they wrap `runBlocking`), so they are
 * natural to call from Java. Call them off your UI thread.
 *
 * ```java
 * NyoraSources nyora = new NyoraSources();
 * for (MangaSource s : nyora.catalog()) System.out.println(s.getId());
 * MangaSearchPage page = nyora.popular("parser:MANGADEX", 1);
 * Manga first = page.getEntries().get(0);
 * MangaDetails d = nyora.details("parser:MANGADEX", first.getUrl());
 * List<MangaPage> pages = nyora.pages("parser:MANGADEX", d.getChapters().get(0));
 * ```
 */
class NyoraSources @JvmOverloads constructor(
    private val networkConfig: HelperNetworkConfig = HelperNetworkConfig(),
) {
    private val runtime = JvmExtensionRuntime(networkConfig)
    private val catalog: List<MangaSource> = nativeParserCatalog()
    private val byId: Map<String, MangaSource> = catalog.associateBy { it.id }

    /** Every source the bundled engine can parse. */
    fun catalog(): List<MangaSource> = catalog

    /** Look up a source by its id (e.g. `"parser:MANGADEX"`), or `null`. */
    fun sourceById(id: String): MangaSource? = byId[id]

    /** Case-insensitive lookup by id or name substring, or `null`. */
    fun findSource(query: String): MangaSource? {
        val needle = query.lowercase()
        return byId[query]
            ?: catalog.firstOrNull { it.id.lowercase().contains(needle) || it.name.lowercase().contains(needle) }
    }

    private fun service(sourceId: String): MangaExtensionService {
        val src = byId[sourceId]
            ?: throw NoSuchElementException("Unknown source id: $sourceId")
        return runtime.create(src.copy(isInstalled = true))
    }

    /** Popular titles for a source (blocking). */
    @JvmOverloads
    fun popular(sourceId: String, page: Int = 1): MangaSearchPage =
        runBlocking { service(sourceId).getPopular(page) }

    /** Latest-updated titles for a source (blocking). */
    @JvmOverloads
    fun latest(sourceId: String, page: Int = 1): MangaSearchPage =
        runBlocking { service(sourceId).getLatest(page) }

    /** Search a source (blocking). */
    @JvmOverloads
    fun search(
        sourceId: String,
        query: String,
        page: Int = 1,
        filters: List<SourceFilter> = emptyList(),
    ): MangaSearchPage =
        runBlocking { service(sourceId).search(query, page, filters) }

    /** Full details + chapter list for a title `url` on a source (blocking). */
    fun details(sourceId: String, url: String): MangaDetails =
        runBlocking { service(sourceId).getDetails(url) }

    /** Page image descriptors for a chapter (blocking). */
    fun pages(sourceId: String, chapter: MangaChapter): List<MangaPage> =
        runBlocking { service(sourceId).getPageList(chapter) }

    /** The advertised search filters for a source. */
    fun filters(sourceId: String): List<SourceFilterDescriptor> =
        service(sourceId).getFilterList()

    /** Whether a source exposes a "latest" feed. */
    fun supportsLatest(sourceId: String): Boolean = service(sourceId).supportsLatest

    /** Extra HTTP headers a source needs when fetching its images. */
    fun imageHeaders(sourceId: String): Map<String, String> = service(sourceId).getHeaders()

    companion object {
        /** Java-friendly factory: `NyoraSources.create()`. */
        @JvmStatic
        fun create(): NyoraSources = NyoraSources()
    }
}
