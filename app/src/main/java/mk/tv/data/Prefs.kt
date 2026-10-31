package mk.tv.data

import android.content.Context

/** Favourites and last-watched channel, kept on the device. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("mktv", Context.MODE_PRIVATE)

    var favorites: Set<String>
        get() = sp.getStringSet("favorites", emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet("favorites", value).apply()

    var lastChannel: String?
        get() = sp.getString("last_channel", null)
        set(value) = sp.edit().putString("last_channel", value).apply()

    fun toggleFavorite(id: String): Set<String> {
        val updated = favorites.toMutableSet().apply { if (!add(id)) remove(id) }
        favorites = updated
        return updated
    }
}
