package tv.firetvwebwrapper.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
  val tagName: String,
  val versionName: String,
  val assetName: String?,
  val downloadUrl: String?
)

object UpdateApi {
  private const val OWNER = "joeyjoey1234"
  private const val REPO = "FireTV-Web-Wrapper"
  private const val LATEST_RELEASE_URL = "https://api.github.com/repos/joeyjoey1234/FireTV-Web-Wrapper/releases/latest"

  @Throws(Exception::class)
  fun fetchLatestRelease(): ReleaseInfo? {
    val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("User-Agent", "FireTvWebWrapper/${BuildConfig.VERSION_NAME}")

    return try {
      val code = connection.responseCode
      if (code != HttpURLConnection.HTTP_OK) {
        val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }
        throw IllegalStateException("GitHub API error $code: ${errorText.orEmpty()}")
      }
      val body = connection.inputStream.bufferedReader().use { it.readText() }
      parseLatest(JSONObject(body))
    } finally {
      connection.disconnect()
    }
  }

  fun hasNewerVersion(current: String, candidate: String): Boolean {
    return compareVersions(normalizeVersion(current), normalizeVersion(candidate)) < 0
  }

  private fun parseLatest(json: JSONObject): ReleaseInfo? {
    val tagName = json.optString("tag_name")
    if (tagName.isNullOrBlank()) return null
    val assets = json.optJSONArray("assets")
    var downloadUrl: String? = null
    var assetName: String? = null
    if (assets != null) {
      for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        val name = asset.optString("name")
        val url = asset.optString("browser_download_url")
        val contentType = asset.optString("content_type")
        val looksLikeApk = name.endsWith(".apk", ignoreCase = true) || contentType.contains("android.package-archive", ignoreCase = true)
        if (looksLikeApk && url.isNotBlank()) {
          downloadUrl = url
          assetName = name
          break
        }
      }
    }
    return ReleaseInfo(
      tagName = tagName,
      versionName = normalizeVersion(tagName),
      assetName = assetName,
      downloadUrl = downloadUrl
    )
  }

  private fun normalizeVersion(value: String): String {
    var version = value.trim()
    if (version.startsWith("v", ignoreCase = true)) {
      version = version.substring(1)
    }
    return version
  }

  private fun compareVersions(current: String, candidate: String): Int {
    val currentParts = versionParts(current)
    val candidateParts = versionParts(candidate)
    val max = maxOf(currentParts.size, candidateParts.size)
    for (i in 0 until max) {
      val currentValue = currentParts.getOrElse(i) { 0 }
      val candidateValue = candidateParts.getOrElse(i) { 0 }
      if (currentValue != candidateValue) {
        return currentValue.compareTo(candidateValue)
      }
    }
    return 0
  }

  private fun versionParts(value: String): List<Int> {
    return value.split('.', '-', '_')
      .filter { it.isNotBlank() }
      .map { part -> part.toIntOrNull() ?: 0 }
  }
}
