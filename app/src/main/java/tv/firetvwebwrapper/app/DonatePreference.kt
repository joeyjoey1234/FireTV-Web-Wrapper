package tv.firetvwebwrapper.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

class DonatePreference @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

  private val donateUrl = "https://buymeacoffee.com/joejoe1234"
  private val username = "joejoe1234"

  init {
    setOnPreferenceClickListener {
      showQrCodeDialog()
      true
    }
  }

  private fun showQrCodeDialog() {
    val qrBitmap = generateQrCode(donateUrl, 512, 512)
    val imageView = ImageView(context).apply {
      setImageBitmap(qrBitmap)
      setPadding(32, 32, 32, 32)
    }

    AlertDialog.Builder(context)
      .setTitle("Buy Me a Coffee")
      .setMessage("Scan this QR code or open the link below.\n\nUsername: $username")
      .setView(imageView)
      .setPositiveButton("Open Link") { _, _ -> openDonateLink() }
      .setNegativeButton("Close", null)
      .show()
  }

  private fun openDonateLink() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(donateUrl)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }

  private fun generateQrCode(content: String, width: Int, height: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints)
    
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
      for (y in 0 until height) {
        bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
      }
    }
    return bitmap
  }
}
