package tv.firetvwebwrapper.app

import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

class BookmarkManagerActivity : AppCompatActivity() {
  private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
  private lateinit var adapter: BookmarkListAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_bookmark_manager)

    setTitle(R.string.bookmark_manager_title)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    val listView = findViewById<ListView>(R.id.bookmark_list)
    adapter = BookmarkListAdapter(this,
      onSetHome = { handleSetHome(it) },
      onDelete = { handleDelete(it) }
    )
    listView.adapter = adapter
    listView.emptyView = findViewById(R.id.bookmark_empty_view)
    listView.itemsCanFocus = true
    listView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    listView.isFocusable = true
    listView.isFocusableInTouchMode = true

    findViewById<Button>(R.id.add_bookmark_button).setOnClickListener {
      promptForBookmark()
    }
  }

  override fun onResume() {
    super.onResume()
    refreshData()
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  private fun refreshData() {
    val bookmarks = BookmarkStore.getBookmarks(prefs)
    adapter.submit(bookmarks, BookmarkStore.homeUrl(prefs))
  }

  private fun promptForBookmark() {
    val input = EditText(this).apply {
      inputType = InputType.TYPE_TEXT_VARIATION_URI
      hint = getString(R.string.bookmark_add_hint)
    }
    AlertDialog.Builder(this)
      .setTitle(R.string.bookmark_add)
      .setMessage(R.string.bookmark_add_message)
      .setView(input)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        val url = UrlValidator.normalize(input.text?.toString())
        if (!UrlValidator.isValidWebUrl(url)) {
          Toast.makeText(this, R.string.bookmark_invalid, Toast.LENGTH_LONG).show()
          return@setPositiveButton
        }
        BookmarkStore.addBookmark(prefs, url)
        Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
        refreshData()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun handleSetHome(url: String) {
    BookmarkStore.setHome(prefs, url)
    Toast.makeText(this, R.string.bookmark_home_updated, Toast.LENGTH_SHORT).show()
    refreshData()
  }

  private fun handleDelete(url: String) {
    val removed = BookmarkStore.removeBookmark(prefs, url)
    val messageRes = if (removed) R.string.bookmark_deleted else R.string.bookmark_delete_blocked
    Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    refreshData()
  }
}
