package tv.firetvwebwrapper.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object UpdateInstaller {
  private const val UPDATE_FOLDER = "updates"

  fun cleanupResidualApks(context: Context) {
    val dir = updatesDir(context)
    if (!dir.exists()) return
    dir.listFiles()?.forEach { file ->
      if (file.isDirectory) {
        file.deleteRecursively()
      } else {
        file.delete()
      }
    }
  }

  @Throws(IOException::class)
  fun downloadApk(context: Context, downloadUrl: String, assetName: String?): File {
    val dir = updatesDir(context)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    val safeName = when {
      assetName.isNullOrBlank() -> "firetv-web-wrapper-update.apk"
      assetName.endsWith(".apk", ignoreCase = true) -> assetName
      else -> "$assetName.apk"
    }
    val target = File(dir, safeName)
    if (target.exists()) {
      target.delete()
    }

    val connection = URL(downloadUrl).openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 30000
    connection.setRequestProperty("User-Agent", "FireTvWebWrapper/${BuildConfig.VERSION_NAME}")

    return try {
      val code = connection.responseCode
      if (code != HttpURLConnection.HTTP_OK) {
        throw IOException("Download failed with HTTP $code")
      }
      connection.inputStream.use { input ->
        FileOutputStream(target).use { output ->
          input.copyTo(output)
        }
      }
      target
    } catch (e: Exception) {
      target.delete()
      throw e
    } finally {
      connection.disconnect()
    }
  }

  fun launchInstallIntent(context: Context, apkFile: File): Boolean {
    if (!hasInstallPermission(context)) {
      requestInstallPermission(context)
      return false
    }

    val authority = "${BuildConfig.APPLICATION_ID}.provider"
    val uri = FileProvider.getUriForFile(context, authority, apkFile)
    val intent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
    return true
  }

  private fun hasInstallPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      true
    } else {
      context.packageManager.canRequestPackageInstalls()
    }
  }

  private fun requestInstallPermission(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
      data = Uri.parse("package:${context.packageName}")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }

  private fun updatesDir(context: Context): File = File(context.cacheDir, UPDATE_FOLDER)
}
