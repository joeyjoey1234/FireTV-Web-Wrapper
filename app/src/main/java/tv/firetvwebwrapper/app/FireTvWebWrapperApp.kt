package tv.firetvwebwrapper.app

import android.app.Application
import androidx.preference.PreferenceManager

class FireTvWebWrapperApp : Application() {
  override fun onCreate() {
    super.onCreate()
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
  }
}
