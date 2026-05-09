package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jeudego.pairgoth.web.WebappManager
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.name
import kotlin.io.path.useDirectoryEntries

abstract class RatingsHandler(val origin: RatingsManager.Ratings) {
    companion object {
        private val delay = TimeUnit.HOURS.toMillis(1L)
        private val ymd = DateTimeFormatter.ofPattern("yyyyMMdd")
        private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    abstract val defaultURL: URL
    open val active = true
    lateinit var players: Json.Array
    lateinit var activeRatingsFile: File
    private var updated = false
    val ready get() = this::activeRatingsFile.isInitialized && this::players.isInitialized

    val url: URL by lazy {
        WebappManager.properties.getProperty("ratings.${origin.name.lowercase(Locale.ROOT)}")?.let { URL(it) } ?: defaultURL
    }

    fun activeDate() = ymd.parse(activeRatingsFile.name.substringBefore(".json").substringAfter("${origin.name}-"))

    fun getRatingsFiles() = RatingsManager.path.useDirectoryEntries("${origin.name}-*.json") { entries ->
        entries.sortedBy { it.fileName.name }.map {
            it.toFile()
        }.toList()
    }

    private fun getLatestRatingsFile() = getRatingsFiles().lastOrNull()

    // Loads (or reloads, if the active file changed) players from the given cache file.
    // Returns true if a (re)load happened, false if it was a no-op.
    private fun initIfNeeded(ratingsFile: File): Boolean {
        val same = this::players.isInitialized && this::activeRatingsFile.isInitialized
                && activeRatingsFile.canonicalPath == ratingsFile.canonicalPath
        return if (!same) {
            readPlayers(ratingsFile)
            true
        } else false
    }

    @Synchronized
    private fun readPlayers(ratingsFile: File) {
        logger.info("Reading ${origin.name} players from ${ratingsFile.canonicalPath}")
        players = Json.parse(ratingsFile.readText())?.asArray() ?: Json.Array()
        activeRatingsFile = ratingsFile
    }

    // Optional global freeze: when `ratings.date=YYYY-MM-DD` is set in pairgoth.properties,
    // it acts as an upper bound. Once today >= that date, the handler loads the most recent
    // cached snapshot whose date is <= ratings.date (the "freeze-eligible" file) and stops
    // fetching once we have one settled snapshot >= that date (no later one can be ≤ it).
    // Until then, dynamic fetch continues so the cached set grows toward the freeze date.
    // Useful for multi-day tournaments where ratings must not change mid-event ("configure
    // once in advance and forget"). Cache files dated past freeze are kept on disk for free
    // — small cost — and become useful again if the freeze is later removed.
    private fun parseFreezeDate(): LocalDate? =
        WebappManager.properties.getProperty("ratings.date")
            ?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

    private fun fileDate(file: File): LocalDate? = runCatching {
        LocalDate.parse(file.name.removePrefix("${origin.name}-").removeSuffix(".json"), ymd)
    }.getOrNull()

    // Latest cached file whose date <= freeze, or null if none such.
    private fun latestEligibleCached(freeze: LocalDate): File? =
        getRatingsFiles().filter { f -> fileDate(f)?.let { !it.isAfter(freeze) } == true }.lastOrNull()

    @Synchronized
    fun updateIfNeeded(): Boolean {
        val freeze = parseFreezeDate()
        val frozen = freeze != null && !LocalDate.now().isBefore(freeze)
        val latestCached = getLatestRatingsFile()
        val freezeSettled = frozen && latestCached != null
            && (fileDate(latestCached)?.let { !it.isBefore(freeze!!) } ?: false)
        val cacheFresh = latestCached != null && Date().time - latestCached.lastModified() < delay

        fun effective(): File? = if (frozen) latestEligibleCached(freeze!!) else latestCached

        // Skip fetch when:
        //  - frozen and settled (no future EGD publication can be ≤ freeze), or
        //  - cache mtime is within the cooldown window.
        if ((frozen && freezeSettled) || cacheFresh) {
            return effective()?.let { initIfNeeded(it) } ?: false
        }

        val payload = fetchPayload()
        val parsed = payload?.let { parsePayload(it) }
        if (parsed == null) {
            // payload missing or unparseable; handler has already logged. Fall back.
            return effective()?.let { initIfNeeded(it) }
                ?: latestCached?.let { initIfNeeded(it) }
                ?: false
        }
        val (lastUpdated, lastPlayers) = parsed
        val ratingsFilename = "${origin.name}-${ymd.format(lastUpdated)}.json"
        val newFile = RatingsManager.path.resolve(ratingsFilename).toFile()
        val isFreshFile = latestCached == null || latestCached.name != ratingsFilename
        if (isFreshFile) {
            RatingsManager.logger.info("Updating $origin cache from $url")
            newFile.printWriter().use { out -> out.println(lastPlayers.toString()) }
        }

        // After fetch, recompute what to load. Under freeze, the new file might be past the
        // upper bound — we still want the latest eligible (which may pre-date newFile).
        val toLoad = if (frozen) latestEligibleCached(freeze!!) else newFile
        return when {
            toLoad == null -> {
                // frozen but no cached file ≤ freeze (and the just-fetched one is past it).
                // Best we can do: surface the freshly fetched data so the system isn't empty.
                logger.warn("ratings.date=$freeze active but no cached ${origin.name} snapshot at or before that date; using dynamic")
                players = lastPlayers
                activeRatingsFile = newFile
                true
            }
            toLoad.canonicalPath == newFile.canonicalPath && isFreshFile -> {
                // optimization: avoid re-parsing what we just produced
                players = lastPlayers
                activeRatingsFile = newFile
                true
            }
            else -> initIfNeeded(toLoad)
        }
    }

    fun fetchPlayers(): Json.Array {
        updated = updateIfNeeded()
        return players
    }

    protected fun fetchPayload(): String? {
        if (url.protocol == "file") {
            return try {
                File(url.toURI()).readText(defaultCharset())
            } catch (e: Exception) {
                logger.error("Could not read ${origin.name} ratings from $url: ${e.javaClass.simpleName} ${e.message}")
                null
            }
        }
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                // Don't set Accept-Encoding - let OkHttp handle compression transparently
                .header("Connection", "keep-alive")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Could not fetch $origin ratings: unexpected code $response")
                val contentType = response.headers["Content-Type"]?.toMediaType()
                return response.body!!.source().readString(contentType?.charset() ?: defaultCharset())
            }
        } catch (ioe: IOException) {
            logger.error("Could not refresh ${origin.name} ratings from ${url}: ${ioe.javaClass.name} ${ioe.message}")
            logger.debug("Could not refresh ${origin.name} ratings from ${url}", ioe)
            return null
        }
    }
    open fun defaultCharset() = StandardCharsets.UTF_8
    fun updated() = updated
    // Returns null if the payload could not be parsed (e.g. a Cloudflare bot-challenge page).
    // Implementations are expected to log a diagnostic in that case.
    abstract fun parsePayload(payload: String): Pair<LocalDate, Json.Array>?
    val logger = LoggerFactory.getLogger(origin.name)
    val atom = "[-._`'a-zA-ZÀ-ÿ]"
}
