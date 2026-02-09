package tv.firetvwebwrapper.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView

class BookmarkListAdapter(
  private val context: Context,
  private val onSetHome: (String) -> Unit,
  private val onDelete: (String) -> Unit
) : BaseAdapter() {

  private val items = mutableListOf<String>()
  private var homeUrl: String? = null

  fun submit(urls: List<String>, home: String) {
    items.clear()
    items.addAll(urls)
    homeUrl = home
    notifyDataSetChanged()
  }

  override fun getCount(): Int = items.size

  override fun getItem(position: Int): String = items[position]

  override fun getItemId(position: Int): Long = position.toLong()

  override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
    val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_bookmark, parent, false)
    val url = getItem(position)
    val isHome = url == homeUrl

    val nameView = view.findViewById<TextView>(R.id.bookmark_name)
    val urlView = view.findViewById<TextView>(R.id.bookmark_url)
    val badgeView = view.findViewById<TextView>(R.id.bookmark_badge)
    val setHomeButton = view.findViewById<Button>(R.id.action_set_home)
    val deleteButton = view.findViewById<Button>(R.id.action_delete)

    nameView.text = UrlValidator.labelFor(url)
    urlView.text = url
    badgeView.visibility = if (isHome) View.VISIBLE else View.GONE

    setHomeButton.isEnabled = !isHome
    setHomeButton.setOnClickListener { onSetHome(url) }

    deleteButton.isEnabled = items.size > 1
    deleteButton.setOnClickListener { onDelete(url) }

    return view
  }
}
