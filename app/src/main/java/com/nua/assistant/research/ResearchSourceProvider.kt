package com.nua.assistant.research

import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface ResearchSourceProvider {
    suspend fun gather(query: String, allowedUrls: List<String>, limit: Int): List<ResearchSource>
}

@Singleton
class DirectUrlResearchSourceProvider @Inject constructor(
    private val client: OkHttpClient,
) : ResearchSourceProvider {
    override suspend fun gather(
        query: String,
        allowedUrls: List<String>,
        limit: Int,
    ): List<ResearchSource> = withContext(Dispatchers.IO) {
        allowedUrls.distinct().take(limit.coerceIn(1, 12)).mapNotNull { url ->
            val uri = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
            if (uri.scheme != "https" || uri.userInfo != null || uri.host.isNullOrBlank()) return@mapNotNull null
            val request = Request.Builder()
                .url(url)
                .header("accept", "text/html, text/plain, application/json")
                .get()
                .build()
            runCatching {
                client.newBuilder().followRedirects(false).build().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val type = response.header("content-type").orEmpty().lowercase()
                    if (!type.startsWith("text/") && !type.startsWith("application/json")) return@use null
                    val raw = response.body?.bytes().orEmpty()
                    val text = raw.copyOf(minOf(raw.size, 512 * 1024))
                        .toString(Charsets.UTF_8)
                        .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
                        .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
                        .replace(Regex("""<[^>]+>"""), " ")
                        .replace(Regex("""\s+"""), " ")
                        .trim()
                    val digest = MessageDigest.getInstance("SHA-256")
                        .digest(url.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                        .take(16)
                    ResearchSource(
                        id = "S" + digest,
                        url = url,
                        title = uri.host,
                        content = text.take(100_000),
                        retrievedAtMillis = System.currentTimeMillis(),
                    )
                }
            }.getOrNull()
        }
    }
}
