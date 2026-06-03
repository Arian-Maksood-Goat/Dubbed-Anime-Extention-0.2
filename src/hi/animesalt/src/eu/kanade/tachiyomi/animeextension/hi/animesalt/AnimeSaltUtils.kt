package eu.kanade.tachiyomi.animeextension.hi.animesalt

import android.util.Base64
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object AnimeSaltUtils {

    fun vrfEncrypt(input: String): String {
        var vrf = input

        ORDER.sortedBy { it.first }
            .forEach { step ->
                vrf = when (step.second) {
                    "exchange" -> exchange(vrf, step.third)
                    "rc4" -> rc4Encrypt(step.third[0], vrf)
                    "reverse" -> vrf.reversed()
                    "base64" -> Base64.encode(
                        vrf.toByteArray(),
                        Base64.URL_SAFE or Base64.NO_WRAP,
                    ).toString(Charsets.UTF_8)

                    else -> vrf
                }
            }

        return URLEncoder.encode(vrf, "utf-8")
    }

    private fun rc4Encrypt(
        key: String,
        input: String,
    ): String {
        val secretKey = SecretKeySpec(
            key.toByteArray(),
            "RC4",
        )

        val cipher = Cipher.getInstance("RC4")

        cipher.init(
            Cipher.ENCRYPT_MODE,
            secretKey,
            cipher.parameters,
        )

        val encrypted = cipher.doFinal(
            input.toByteArray(),
        )

        return Base64.encode(
            encrypted,
            Base64.URL_SAFE or Base64.NO_WRAP,
        ).toString(Charsets.UTF_8)
    }

    private fun exchange(
        input: String,
        keys: List<String>,
    ): String {
        val key1 = keys[0]
        val key2 = keys[1]

        return input.map { char ->
            val index = key1.indexOf(char)

            if (index != -1) {
                key2[index]
            } else {
                char
            }
        }.joinToString("")
    }

    private val EXCHANGE_KEY_1 =
        listOf(
            "AP6GeR8H0lwUz1",
            "UAz8Gwl10P6ReH",
        )

    private const val KEY_1 =
        "ItFKjuWokn4ZpB"

    private const val KEY_2 =
        "fOyt97QWFB3"

    private val EXCHANGE_KEY_2 =
        listOf(
            "1majSlPQd2M5",
            "da1l2jSmP5QM",
        )

    private val EXCHANGE_KEY_3 =
        listOf(
            "CPYvHj09Au3",
            "0jHA9CPYu3v",
        )

    private const val KEY_3 =
        "736y1uTJpBLUX"

    private val ORDER =
        listOf(
            Triple(1, "exchange", EXCHANGE_KEY_1),
            Triple(2, "rc4", listOf(KEY_1)),
            Triple(3, "rc4", listOf(KEY_2)),
            Triple(4, "exchange", EXCHANGE_KEY_2),
            Triple(5, "exchange", EXCHANGE_KEY_3),
            Triple(6, "reverse", emptyList()),
            Triple(7, "rc4", listOf(KEY_3)),
            Triple(8, "base64", emptyList()),
        )
}
