package eu.kanade.tachiyomi.animeextension.hi.animesalt.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup

class AbyssExtractor(
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
        val reqHeaders = headers.newBuilder()
            .set(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            )
            .set("Origin", "https://playhydrax.com")
            .set("Referer", "https://playhydrax.com/")
            .build()

        val document =
            client.newCall(
                GET(url, reqHeaders),
            ).awaitSuccess()
                .use { response ->
                    Jsoup.parse(response.body.string())
                }

        val scripts =
            document.select("script")
                .joinToString("\n") { it.data() }

        val encrypted =
            Regex("""const\s+datas\s*=\s*"([^"]*)"""")
                .find(scripts)
                ?.groupValues
                ?.getOrNull(1)
                ?: return

        val body =
            """
            {
                "text":"$encrypted"
            }
            """.trimIndent()
                .toRequestBody(
                    "application/json".toMediaType(),
                )

        val decrypted =
            client.newCall(
                POST(
                    url = "https://enc-dec.app/api/dec-abyss",
                    headers = reqHeaders,
                    body = body,
                ),
            ).awaitSuccess()
                .use { response ->

                    json.decodeFromString<AbyssResponse>(
                        response.body.string(),
                    )
                }

        decrypted.result.sources
            .filter { it.status }
            .forEach { source ->

                callback(
                    Video(
                        url = source.url,
                        quality = "Abyss [${source.codec.uppercase()}] ${source.type}",
                        videoUrl = source.url,
                        headers = headers.newBuilder()
                            .set(
                                "Referer",
                                "https://playhydrax.com/",
                            )
                            .build(),
                    ),
                )
            }
    }

    @Serializable
    data class AbyssResponse(
        val status: Long,
        val result: Result,
    )

    @Serializable
    data class Result(
        val sources: List<AbyssSource>,
    )

    @Serializable
    data class AbyssSource(
        val url: String,
        val size: Long,
        val type: String,
        val codec: String,
        val status: Boolean,
    )
}
