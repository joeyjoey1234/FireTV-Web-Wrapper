package tv.firetvwebwrapper.app

object Prefs {
  const val KEY_SERVER_URL = "server_url"
  const val KEY_BOOKMARKS = "bookmarks"
  const val KEY_MANAGE_BOOKMARKS = "manage_bookmarks"
  const val KEY_UPDATE = "check_update"
  const val KEY_USER_AGENT_MODE = "user_agent_mode"
  const val KEY_FORCE_MOBILE_UA = "force_mobile_ua" // deprecated, kept for migration
  const val KEY_ALLOW_HTTP = "allow_http"
  const val KEY_ALLOW_INVALID_SSL = "allow_invalid_ssl"
  
  const val UA_MODE_MOBILE = "mobile"
  const val UA_MODE_DESKTOP = "desktop"
  const val UA_MODE_AUTO = "auto"
}
