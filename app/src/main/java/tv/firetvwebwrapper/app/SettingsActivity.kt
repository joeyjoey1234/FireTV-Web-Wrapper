package tv.firetvwebwrapper.app

import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
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
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences, rootKey)

    val urlPref = findPreference<EditTextPreference>(Prefs.KEY_SERVER_URL)
    urlPref?.setOnBindEditTextListener { editText ->
      editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
    }
    urlPref?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
  }
}
