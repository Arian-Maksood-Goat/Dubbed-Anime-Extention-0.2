package eu.kanade.tachiyomi.animeextension.hi.animesalt

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
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimeSalt : ParsedAnimeHttpSource() {

    override val name = "AnimeSalt"
    override val baseUrl = "https://animesalt.ac"
    override val lang = "hi"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val customHeaders = headersOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer" to baseUrl,
        "Origin" to baseUrl
    )

    private val abyssExtractor by lazy { AbyssExtractor(client, customHeaders) }
    private val awsExtractor by lazy { AwsStreamExtractor(client, customHeaders) }
    private val megaExtractor by lazy { MegaPlayExtractor(client, customHeaders) }

    // Popular
    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/category/status/ongoing/page/$page", customHeaders)

    override fun popularAnimeSelector(): String = "article"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        title = element.selectFirst("header h2")?.text()?.trim() ?: ""
        setUrlWithoutDomain(element.selectFirst("a")?.attr("href") ?: "")
        thumbnail_url = element.selectFirst("img")?.attr("data-src") ?: element.selectFirst("img")?.attr("src")
    }

    override fun popularAnimeNextPageSelector(): String? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/category/type/anime/?type=series&page=$page", customHeaders)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = null

    // Search
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val formBody = FormBody.Builder()
            .add("action", "torofilm_infinite_scroll")
            .add("page", page.toString())
            .add("per_page", "12")
            .add("query_type", "search")
            .add("query_args[s]", query)
            .build()
        return POST("$baseUrl/wp-admin/admin-ajax.php", body = formBody, headers = customHeaders)
    }

    override fun searchAnimeSelector(): String = "article"
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector(): String? = null

    // Details
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1")?.text() ?: ""
        thumbnail_url = document.selectFirst("div.bd img")?.attr("data-src")
        description = document.selectFirst("#overview-text p")?.text()
    }

    // Episodes
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val episodes = mutableListOf<SEpisode>()

        document.select("div.season-buttons a").forEach { seasonBtn ->
            val postId = seasonBtn.attr("data-post")
            val dataSeason = seasonBtn.attr("data-season")

            val formBody = FormBody.Builder()
                .add("action", "action_select_season")
                .add("season", dataSeason)
                .add("post", postId)
                .build()

            val seasonDoc = client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", body = formBody, headers = customHeaders))
                .execute().asJsoup()

            seasonDoc.select("li article").forEachIndexed { index, ep ->
                val href = ep.selectFirst("a")?.attr("href") ?: return@forEachIndexed
                val epName = ep.selectFirst("h2.entry-title")?.text() ?: "Episode ${index + 1}"

                episodes.add(SEpisode.create().apply {
                    url = fixUrl(href, baseUrl)
                    name = epName
                    episode_number = (index + 1).toFloat()
                })
            }
        }
        return episodes
    }

    override fun episodeListSelector(): String = throw UnsupportedOperationException()
    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // Video List
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videos = mutableListOf<Video>()

        document.select("iframe").forEach { iframe ->
            var url = iframe.attr("data-src").ifBlank { iframe.attr("src") }
            if (url.isNotBlank()) {
                url = fixUrl(url, baseUrl)

                when {
                    url.contains("short.icu") || url.contains("abyssplayer.com") -> {
                        val abyssUrl = url.replace("short.icu", "abyssplayer.com")
                        abyssExtractor.videosFromUrl(abyssUrl) { videos.add(it) }
                    }
                    url.contains("z.awstream.net") -> {
                        awsExtractor.videosFromUrl(url) { videos.add(it) }
                    }
                    url.contains("megaplay.buzz") -> {
                        megaExtractor.videosFromUrl(url) { videos.add(it) }
                    }
                }
            }
        }
        return videos
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()
}