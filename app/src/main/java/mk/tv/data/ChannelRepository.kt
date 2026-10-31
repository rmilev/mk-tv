package mk.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Channel list = bundled assets/channels.json, replaced by a newer copy from GitHub when
 * available. That way new or fixed stream links reach the TV without reinstalling the APK.
 */
class ChannelRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cacheFile = File(context.filesDir, "channels.json")

    suspend fun load(): List<Channel> = withContext(Dispatchers.IO) {
        val bundled = parse(context.assets.open("channels.json").bufferedReader().readText())
        val cached = cacheFile.takeIf { it.exists() }?.let { runCatching { parse(it.readText()) }.getOrNull() }
        listOfNotNull(bundled, cached).maxBy { it.version }.channels
    }

    /** Fetches the latest list; returns it only if it is newer than what we have. */
    suspend fun refresh(current: List<Channel>, currentVersion: Int): List<Channel>? = runCatching {
        val text = Http.get(REMOTE_URL)
        val remote = parse(text)
        if (remote.version > currentVersion && remote.channels.isNotEmpty()) {
            withContext(Dispatchers.IO) { cacheFile.writeText(text) }
            remote.channels
        } else null
    }.getOrNull()

    suspend fun version(): Int = withContext(Dispatchers.IO) {
        val bundled = parse(context.assets.open("channels.json").bufferedReader().readText()).version
        val cached = cacheFile.takeIf { it.exists() }?.let { runCatching { parse(it.readText()).version }.getOrNull() }
        maxOf(bundled, cached ?: 0)
    }

    private fun parse(text: String): ChannelList = json.decodeFromString(ChannelList.serializer(), text)

    companion object {
        const val REMOTE_URL = "https://raw.githubusercontent.com/rmilev/mk-tv/main/app/src/main/assets/channels.json"
    }
}
