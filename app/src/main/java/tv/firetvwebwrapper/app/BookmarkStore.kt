package tv.firetvwebwrapper.app

import android.content.SharedPreferences
import org.json.JSONArray

object BookmarkStore {
  fun getBookmarks(prefs: SharedPreferences): List<String> {
    val list = loadRawBookmarks(prefs)
    val home = prefs.getString(Prefs.KEY_SERVER_URL, "").orEmpty()
    var needsSave = false
    if (home.isNotBlank()) {
      val index = list.indexOf(home)
      if (index == -1) {
        list.add(0, home)
        needsSave = true
      } else if (index > 0) {
        list.removeAt(index)
        list.add(0, home)
        needsSave = true
      }
    }
    if (needsSave) {
      persist(prefs, list)
    }
    return list.toList()
  }

  fun addBookmark(prefs: SharedPreferences, rawUrl: String): List<String> {
    val url = UrlValidator.normalize(rawUrl)
    if (url.isBlank()) return getBookmarks(prefs)
    val list = loadRawBookmarks(prefs)
    if (!list.contains(url)) {
      list.add(url)
      persist(prefs, list)
    }
    return getBookmarks(prefs)
  }

  fun removeBookmark(prefs: SharedPreferences, rawUrl: String): Boolean {
    val url = UrlValidator.normalize(rawUrl)
    if (url.isBlank()) return false
    val list = loadRawBookmarks(prefs)
    if (!list.contains(url) || list.size <= 1) {
      return false
    }
    list.remove(url)
    persist(prefs, list)

    val home = prefs.getString(Prefs.KEY_SERVER_URL, "").orEmpty()
    if (home == url) {
      val newHome = list.firstOrNull().orEmpty()
      prefs.edit().putString(Prefs.KEY_SERVER_URL, newHome).apply()
    }
    return true
  }

  fun setHome(prefs: SharedPreferences, rawUrl: String): List<String> {
    val url = UrlValidator.normalize(rawUrl)
    if (url.isBlank()) return getBookmarks(prefs)
    val list = loadRawBookmarks(prefs)
    list.remove(url)
    list.add(0, url)
    persist(prefs, list)
    prefs.edit().putString(Prefs.KEY_SERVER_URL, url).apply()
    return list.toList()
  }

  fun homeUrl(prefs: SharedPreferences): String = prefs.getString(Prefs.KEY_SERVER_URL, "").orEmpty()

  private fun loadRawBookmarks(prefs: SharedPreferences): MutableList<String> {
    val stored = prefs.getString(Prefs.KEY_BOOKMARKS, null).orEmpty()
    if (stored.isBlank()) return mutableListOf()
    return try {
      val json = JSONArray(stored)
      MutableList(json.length()) { index -> json.optString(index) }
        .map { UrlValidator.normalize(it) }
        .filter { it.isNotBlank() }
        .distinct()
        .toMutableList()
    } catch (_: Exception) {
      mutableListOf()
    }
  }

  private fun persist(prefs: SharedPreferences, list: List<String>) {
    val sanitized = list.filter { it.isNotBlank() }
    val json = JSONArray()
    sanitized.forEach { json.put(it) }
    prefs.edit().putString(Prefs.KEY_BOOKMARKS, json.toString()).apply()
  }
}
