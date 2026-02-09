package tv.firetvwebwrapper.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

class SetupActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_setup)

    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val input = findViewById<EditText>(R.id.server_url_input)
    val button = findViewById<Button>(R.id.save_button)

    val existing = prefs.getString(Prefs.KEY_SERVER_URL, "").orEmpty()
    if (existing.isNotBlank()) {
      input.setText(existing)
      input.setSelection(existing.length)
    }
    button.isEnabled = UrlValidator.isValidWebUrl(existing)

    button.setOnClickListener {
      val url = UrlValidator.normalize(input.text?.toString())
      if (!UrlValidator.isValidWebUrl(url)) {
        Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_LONG).show()
        return@setOnClickListener
      }
      BookmarkStore.setHome(prefs, url)
      startActivity(Intent(this, MainActivity::class.java))
      finish()
    }

    input.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
      override fun afterTextChanged(s: Editable?) {
        button.isEnabled = UrlValidator.isValidWebUrl(s?.toString())
      }
    })
  }

}
