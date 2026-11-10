package com.peramsuneel.cleanplayer.online

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Isolated NewPipe integration. Nothing in the player package depends on this class;
 * the player receives only the final URL returned by resolve().
 */
data class OnlineSearchResult(
    val serviceId: Int,
    val serviceName: String,
    val url: String,
    val title: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?
)

object OnlineSearchRepository {
    /** Single kill switch: set false to disable online search without affecting local playback. */
    const val ENABLE_ONLINE_SEARCH = true

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var initialized = false

    @Synchronized
    private fun initializeIfNeeded() {
        if (initialized) return
        val downloader = object : Downloader() {
            override fun execute(request: ExtractorRequest): ExtractorResponse {
                val requestBody = request.dataToSend()?.toRequestBody(null)
                val builder = okhttp3.Request.Builder()
                    .url(request.url())
                    .method(request.httpMethod(), requestBody)
                    .header("User-Agent", USER_AGENT)
                request.headers().forEach { (name, values) ->
                    builder.removeHeader(name)
                    values.forEach { value -> builder.addHeader(name, value) }
                }
                httpClient.newCall(builder.build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    return ExtractorResponse(
                        response.code,
                        response.message,
                        response.headers.toMultimap(),
                        body,
                        response.request.url.toString()
                    )
                }
            }
        }
        NewPipe.init(downloader)
        initialized = true
    }

    @Throws(Exception::class)
    fun search(query: String): List<OnlineSearchResult> {
        check(ENABLE_ONLINE_SEARCH) { "Online search is disabled." }
        require(query.isNotBlank()) { "Enter a search term." }
        initializeIfNeeded()

        val results = mutableListOf<OnlineSearchResult>()
        for (service in NewPipe.getServices()) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Search cancelled")
            try {
                val handler = service.searchQHFactory.fromQuery(query, emptyList(), "")
                val info = SearchInfo.getInfo(service, handler)
                info.relatedItems.filterIsInstance<StreamInfoItem>().forEach { item ->
                    if (results.size >= 30) return@forEach
                    val thumbnail = item.thumbnails.firstOrNull()?.url
                    results.add(
                        OnlineSearchResult(
                            service.serviceId,
                            service.serviceInfo.name,
                            item.url,
                            item.name,
                            item.duration,
                            thumbnail
                        )
                    )
                }
            } catch (error: Exception) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("Search cancelled")
                // One unsupported or unavailable service must not prevent results from other services.
            }
            if (results.size >= 30) break
        }
        return results.take(30)
    }

    @Throws(Exception::class)
    fun resolve(result: OnlineSearchResult): String {
        check(ENABLE_ONLINE_SEARCH) { "Online search is disabled." }
        initializeIfNeeded()
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Resolve cancelled")
        val service = NewPipe.getService(result.serviceId)
        val info = StreamInfo.getInfo(service, result.url)

        // Prefer a progressive stream with both audio and video. For audio services, use the
        // first direct audio URL. Never pass extractor objects into the player.
        val video = info.videoStreams.firstOrNull { it.isUrl && !it.isVideoOnly }
        if (video != null) return video.content

        val audio = info.audioStreams.firstOrNull { it.isUrl }
        if (audio != null) return audio.content

        throw IOException("No directly playable stream was provided by ${result.serviceName}.")
    }
}
