package tv.firetvwebwrapper.app

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_settings)

    if (savedInstanceState == null) {
      supportFragmentManager
        .beginTransaction()
        .replace(R.id.settings_container, SettingsFragment())
        .commit()
    }

    title = getString(R.string.settings_title)
  }
}

class SettingsFragment : PreferenceFragmentCompat() {
  private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
  private var bookmarksPref: Preference? = null
  private var updatePref: Preference? = null
  private var latestRelease: ReleaseInfo? = null
  private var isCheckingUpdate = false
  private var isDownloadingUpdate = false

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences, rootKey)

    val urlPref = findPreference<EditTextPreference>(Prefs.KEY_SERVER_URL)
    urlPref?.setOnBindEditTextListener { editText ->
      editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
    }
    urlPref?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
    urlPref?.setOnPreferenceChangeListener { preference, newValue ->
      val url = UrlValidator.normalize(newValue as? String)
      if (!UrlValidator.isValidWebUrl(url)) {
        Toast.makeText(requireContext(), R.string.invalid_url, Toast.LENGTH_LONG).show()
        return@setOnPreferenceChangeListener false
      }
      BookmarkStore.setHome(prefs, url)
      (preference as? EditTextPreference)?.text = url
      Toast.makeText(requireContext(), R.string.bookmark_home_updated, Toast.LENGTH_SHORT).show()
      refreshBookmarkSummary()
      false
    }

    bookmarksPref = findPreference(Prefs.KEY_MANAGE_BOOKMARKS)
    bookmarksPref?.setOnPreferenceClickListener {
      startActivity(Intent(requireContext(), BookmarkManagerActivity::class.java))
      true
    }

    updatePref = findPreference(Prefs.KEY_UPDATE)
    updatePref?.setOnPreferenceClickListener {
      if (isDownloadingUpdate || isCheckingUpdate) return@setOnPreferenceClickListener true
      val release = latestRelease
      if (release != null && release.downloadUrl != null && UpdateApi.hasNewerVersion(currentVersion(), release.versionName)) {
        downloadUpdate(release)
      } else {
        checkForUpdates(forceRefresh = true)
      }
      true
    }
    refreshUpdatePreferenceState()
  }

  override fun onResume() {
    super.onResume()
    refreshBookmarkSummary()
    if (latestRelease == null) {
      checkForUpdates(forceRefresh = true)
    } else {
      refreshUpdatePreferenceState()
    }
  }

  private fun refreshBookmarkSummary() {
    val bookmarks = BookmarkStore.getBookmarks(prefs)
    val summary = if (bookmarks.isEmpty()) {
      getString(R.string.bookmark_empty_list)
    } else {
      val label = UrlValidator.labelFor(bookmarks.first())
      getString(R.string.pref_saved_urls_summary, label, bookmarks.size)
    }
    bookmarksPref?.summary = summary
  }

  private fun checkForUpdates(forceRefresh: Boolean = false) {
    if (isCheckingUpdate && !forceRefresh) return
    isCheckingUpdate = true
    refreshUpdatePreferenceState()
    updatePref?.summary = getString(R.string.update_summary_checking)

    viewLifecycleOwner.lifecycleScope.launch {
      try {
        val release = withContext(Dispatchers.IO) { UpdateApi.fetchLatestRelease() }
        latestRelease = release
        updatePref?.summary = buildUpdateSummary(release)
      } catch (_: Exception) {
        updatePref?.summary = getString(R.string.update_summary_error)
      } finally {
        isCheckingUpdate = false
        refreshUpdatePreferenceState()
      }
    }
  }

  private fun downloadUpdate(release: ReleaseInfo) {
    val downloadUrl = release.downloadUrl ?: return
    isDownloadingUpdate = true
    refreshUpdatePreferenceState()
    updatePref?.summary = getString(R.string.update_summary_downloading, release.versionName)
    Toast.makeText(requireContext(), R.string.update_download_start, Toast.LENGTH_LONG).show()

    viewLifecycleOwner.lifecycleScope.launch {
      try {
        val apkFile = withContext(Dispatchers.IO) {
          UpdateInstaller.downloadApk(requireContext().applicationContext, downloadUrl, release.assetName)
        }
        Toast.makeText(requireContext(), R.string.update_download_complete, Toast.LENGTH_LONG).show()
        val launched = UpdateInstaller.launchInstallIntent(requireContext(), apkFile)
        if (!launched) {
          Toast.makeText(requireContext(), R.string.update_permission_required, Toast.LENGTH_LONG).show()
        }
        updatePref?.summary = buildUpdateSummary(release)
      } catch (e: Exception) {
        val message = e.localizedMessage ?: e.javaClass.simpleName
        Toast.makeText(requireContext(), getString(R.string.update_download_failed, message), Toast.LENGTH_LONG).show()
        updatePref?.summary = getString(R.string.update_summary_error)
      } finally {
        isDownloadingUpdate = false
        refreshUpdatePreferenceState()
      }
    }
  }

  private fun buildUpdateSummary(release: ReleaseInfo?): String {
    val current = currentVersion()
    return when {
      release == null -> getString(R.string.update_summary_error)
      UpdateApi.hasNewerVersion(current, release.versionName) ->
        getString(R.string.update_summary_available, current, release.versionName)
      else -> getString(R.string.update_summary_latest, current)
    }
  }

  private fun refreshUpdatePreferenceState() {
    updatePref?.isEnabled = !(isCheckingUpdate || isDownloadingUpdate)
  }

  private fun currentVersion(): String = BuildConfig.VERSION_NAME
}
