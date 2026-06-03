package eu.kanade.tachiyomi.animeextension.hi.animesalt

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.animeextension.hi.animesalt.AnimeSalt.Companion.UA_MOBILE
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class CloudFlareBypassResult(
    val cookies: String,
    val userAgent: String,
)

class CloudflareBypass(
    private val context: Context,
) {

    @SuppressLint("SetJavaScriptEnabled")
    fun getCookies(pageUrl: String): CloudFlareBypassResult? {
        clearCookiesForUrl(pageUrl)

        val latch = CountDownLatch(1)

        var result: CloudFlareBypassResult? = null
        var webView: WebView? = null

        val completed = AtomicBoolean(false)

        Handler(Looper.getMainLooper()).post {
            try {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = UA_MOBILE
                }

                val userAgent =
                    webView?.settings?.userAgentString ?: UA_MOBILE

                webView?.webViewClient = object : WebViewClient() {

                    override fun onPageFinished(
                        view: WebView,
                        url: String,
                    ) {
                        pollForClearance(
                            pageUrl,
                            userAgent,
                            completed,
                        ) { bypassResult ->

                            if (completed.compareAndSet(false, true)) {
                                result = bypassResult
                                latch.countDown()
                            }
                        }
                    }
                }

                CookieManager
                    .getInstance()
                    .setCookie(pageUrl, "")

                webView?.loadUrl(pageUrl)
            } catch (_: Exception) {
                if (completed.compareAndSet(false, true)) {
                    latch.countDown()
                }
            }
        }

        try {
            latch.await(30, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            return null
        } finally {
            Handler(Looper.getMainLooper()).post {
                try {
                    webView?.stopLoading()
                    webView?.clearHistory()
                    webView?.removeAllViews()
                    webView?.destroy()
                } catch (_: Exception) {
                }
            }
        }

        return result
    }

    private fun pollForClearance(
        url: String,
        userAgent: String,
        completed: AtomicBoolean,
        onComplete: (CloudFlareBypassResult) -> Unit,
    ) {
        val handler = Handler(Looper.getMainLooper())

        val startTime = System.currentTimeMillis()

        val timeoutMs = 30_000L
        val pollIntervalMs = 500L

        handler.postDelayed(
            object : Runnable {

                override fun run() {
                    if (completed.get()) return

                    val elapsed =
                        System.currentTimeMillis() - startTime

                    if (elapsed >= timeoutMs) {
                        return
                    }

                    val cookies =
                        CookieManager
                            .getInstance()
                            .getCookie(url)

                    if (cookies?.contains("cf_clearance=") == true) {
                        onComplete(
                            CloudFlareBypassResult(
                                cookies = cookies,
                                userAgent = userAgent,
                            ),
                        )
                    } else {
                        handler.postDelayed(
                            this,
                            pollIntervalMs,
                        )
                    }
                }
            },
            pollIntervalMs,
        )
    }

    private fun clearCookiesForUrl(
        pageUrl: String,
    ) {
        val domain =
            Uri.parse(pageUrl).host ?: return

        val cookieManager =
            CookieManager.getInstance()

        listOf(
            "https://$domain",
            "https://www.$domain",
        ).forEach { url ->

            cookieManager
                .getCookie(url)
                ?.split(";")
                ?.forEach { cookie ->

                    val cookieName =
                        cookie.substringBefore("=")
                            .trim()

                    if (cookieName.isNotEmpty()) {
                        cookieManager.setCookie(
                            url,
                            "$cookieName=; Max-Age=0; Path=/",
                        )

                        cookieManager.setCookie(
                            url,
                            "$cookieName=; Max-Age=0; Path=/; Domain=.$domain",
                        )
                    }
                }
        }

        cookieManager.flush()
    }
}
