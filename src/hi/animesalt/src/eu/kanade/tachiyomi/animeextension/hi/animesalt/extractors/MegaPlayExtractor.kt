package eu.kanade.tachiyomi.animeextension.hi.animesalt.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

class MegaPlayExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
) {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun videosFromUrl(
        url: String,
        callback: (Video) -> Unit,
    ) {
        val mainHeaders = headers.newBuilder()
            .set(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
            )
            .set("Accept", "*/*")
            .set("Origin", "https://rapid-cloud.co")
            .set("Referer", "https://rapid-cloud.co/")
            .build()

        try {
            val requestHeaders =
                headers.newBuilder()
                    .set("Accept", "*/*")
                    .set("X-Requested-With", "XMLHttpRequest")
                    .build()

            val document =
                client.newCall(
                    GET(url, requestHeaders),
                ).awaitSuccess()
                    .use { response ->
                        Jsoup.parse(response.body.string())
                    }

            val id =
                document
                    .selectFirst("#megaplay-player")
                    ?.attr("data-id")
                    ?: return

            val apiUrl =
                "https://megaplay.buzz/stream/getSources?id=$id&id=$id"

            val apiResponse =
                client.newCall(
                    GET(apiUrl, requestHeaders),
                ).awaitSuccess()
                    .use { response ->
                        json.decodeFromString<MegaPlayResponse>(
                            response.body.string(),
                        )
                    }

            callback(
                Video(
                    url = apiResponse.sources.file,
                    quality = "MegaPlay",
                    videoUrl = apiResponse.sources.file,
                    headers = mainHeaders,
                ),
            )
        } catch (_: Exception) {
            return
        }
    }

    @Serializable
    data class MegaPlayResponse(
        val sources: Sources,
        val tracks: List<Track> = emptyList(),
    )

    @Serializable
    data class Sources(
        val file: String,
    )

    @Serializable
    data class Track(
        val file: String,
        val label: String,
        val kind: String,
        val default: Boolean? = null,
    )
}
