package eu.kanade.tachiyomi.animeextension.hi.animesalt.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

class AwsStreamExtractor(
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
        val extractedHash =
            url.substringAfterLast("/")

        val document =
            client.newCall(
                GET(url, headers),
            ).awaitSuccess()
                .use { response ->
                    Jsoup.parse(response.body.string())
                }

        val requestHeaders =
            headers.newBuilder()
                .set(
                    "x-requested-with",
                    "XMLHttpRequest",
                )
                .build()

        val formBody =
            FormBody.Builder()
                .add("hash", extractedHash)
                .add(
                    "r",
                    "https://z.awstream.net",
                )
                .build()

        val apiUrl =
            "https://z.awstream.net/player/index.php?data=$extractedHash&do=getVideo"

        val response =
            client.newCall(
                POST(
                    url = apiUrl,
                    headers = requestHeaders,
                    body = formBody,
                ),
            ).awaitSuccess()
                .use { resp ->

                    json.decodeFromString<Response>(
                        resp.body.string(),
                    )
                }

        val m3u8 =
            response.videoSource

        callback(
            Video(
                url = m3u8,
                quality = "AWSStream 1080p",
                videoUrl = m3u8,
                headers = headers,
            ),
        )
    }

    @Serializable
    data class Response(
        val hls: Boolean,
        val videoImage: String,
        val videoSource: String,
        val securedLink: String,
        val downloadLinks: List<String?> = emptyList(),
        val attachmentLinks: List<String?> = emptyList(),
        val ck: String,
    )
}
