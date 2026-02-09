package tv.firetvwebwrapper.app

import android.net.Uri

object UrlValidator {
  fun isValidWebUrl(value: String?): Boolean {
    val url = value?.trim().orEmpty()
    if (url.isBlank()) return false
    val lower = url.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
      return false
    }
    return try {
      val parsed = Uri.parse(url)
      !parsed.host.isNullOrBlank()
    } catch (_: Exception) {
      false
    }
  }

  fun normalize(value: String?): String = value?.trim().orEmpty()

  fun labelFor(url: String): String {
    return try {
      val host = Uri.parse(url).host
      if (host.isNullOrBlank()) url else host
    } catch (_: Exception) {
      url
    }
  }
}
