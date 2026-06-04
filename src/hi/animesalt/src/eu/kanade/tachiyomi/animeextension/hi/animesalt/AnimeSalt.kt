package eu.kanade.tachiyomi.animeextension.hi.animesalt

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.animeextension.hi.animesalt.extractors.AbyssExtractor
import eu.kanade.tachiyomi.animeextension.hi.animesalt.extractors.AwsStreamExtractor
import eu.kanade.tachiyomi.animeextension.hi.animesalt.extractors.MegaPlayExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimeSalt : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "AnimeSalt"
    override val baseUrl = "https://animesalt.ac"
    override val lang = "hi"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    override val headers = headersOf(
        "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer", baseUrl,
        "Origin", baseUrl
    )

    private val abyssExtractor by lazy { AbyssExtractor(client, headers) }
    private val awsExtractor by lazy { AwsStreamExtractor(client, headers) }
    private val megaExtractor by lazy { MegaPlayExtractor(client, headers) }

    // ==================== Popular / Latest ====================
    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/category/status/ongoing/page/$page")

    override fun popularAnimeSelector() = "article"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        title = element.selectFirst("header h2")?.text()?.trim() ?: ""
        setUrlWithoutDomain(element.selectFirst("a")?.attr("href") ?: "")
        thumbnail_url = element.selectFirst("img")?.attr("data-src") ?: element.selectFirst("img")?.attr("src")
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/category/type/anime/?type=series&page=$page")

    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)

    // ==================== Search ====================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val formBody = FormBody.Builder()
            .add("action", "torofilm_infinite_scroll")
            .add("page", page.toString())
            .add("per_page", "12")
            .add("query_type", "search")
            .add("query_args[s]", query)
            .build()
        return POST("$baseUrl/wp-admin/admin-ajax.php", body = formBody)
    }

    override fun searchAnimeSelector() = "article"
    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)

    // ==================== Details & Episodes ====================
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1")?.text() ?: ""
        thumbnail_url = document.selectFirst("div.bd img")?.attr("data-src")
        description = document.selectFirst("#overview-text p")?.text()
    }

    override fun episodeListParse(document: Document): List<SEpisode> {
        val episodes = mutableListOf<SEpisode>()

        document.select("div.season-buttons a").forEach { seasonBtn ->
            val postId = seasonBtn.attr("data-post")
            val dataSeason = seasonBtn.attr("data-season")

            val formBody = FormBody.Builder()
                .add("action", "action_select_season")
                .add("season", dataSeason)
                .add("post", postId)
                .build()

            val seasonDoc = client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", body = formBody))
                .execute().asJsoup()

            seasonDoc.select("li article").forEachIndexed { index, ep ->
                val href = ep.selectFirst("a")?.attr("href") ?: return@forEachIndexed
                val epName = ep.selectFirst("h2.entry-title")?.text() ?: "Episode ${index + 1}"

                episodes.add(SEpisode.create().apply {
                    url = fixUrl(href)
                    name = epName
                    episode_number = (index + 1).toFloat()
                })
            }
        }
        return episodes
    }

    // ==================== Video List ====================
    override fun videoListParse(document: Document): List<Video> {
        val videos = mutableListOf<Video>()

        document.select("iframe").forEach { iframe ->
            var iframeUrl = iframe.attr("data-src").ifBlank { iframe.attr("src") }
            if (iframeUrl.isNotBlank()) {
                iframeUrl = fixUrl(iframeUrl)

                when {
                    iframeUrl.contains("short.icu") || iframeUrl.contains("abyssplayer.com") -> {
                        val abyssUrl = iframeUrl.replace("short.icu", "abyssplayer.com")
                        abyssExtractor.videosFromUrl(abyssUrl) { videos.add(it) }
                    }
                    iframeUrl.contains("z.awstream.net") -> {
                        awsExtractor.videosFromUrl(iframeUrl) { videos.add(it) }
                    }
                    iframeUrl.contains("megaplay.buzz") -> {
                        megaExtractor.videosFromUrl(iframeUrl) { videos.add(it) }
                    }
                }
            }
        }
        return videos
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // TODO: Add preferences later if needed
    }
}