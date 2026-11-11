package kr.jh.wallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {
    private lateinit var web: WebView
    private val prefs by lazy { getSharedPreferences("wallpaper", MODE_PRIVATE) }
    private val folderPickerRequest = 501

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == folderPickerRequest && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val trees = JSONArray(prefs.getString("trees", "[]"))
            if ((0 until trees.length()).none { trees.optString(it) == uri.toString() }) trees.put(uri.toString())
            prefs.edit().putString("trees", trees.toString()).apply()
            val json = collectImages().toString()
            web.evaluateJavascript("window.onNativeImages && window.onNativeImages(${JSONObject.quote(json)})", null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.webChromeClient = WebChromeClient()
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(NativeBridge(), "NativeBridge")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    inner class NativeBridge {
        @JavascriptInterface fun selectFolder() { runOnUiThread { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), folderPickerRequest) } }
        @JavascriptInterface fun getImages(): String = collectImages().toString()
        @JavascriptInterface fun getSettings(): String = prefs.getString("settings", "{}") ?: "{}"
        @JavascriptInterface fun saveSettings(json: String) {
            try {
                JSONObject(json)
                prefs.edit().putString("settings", json).apply()
                sendBroadcast(Intent(WallpaperServiceImpl.ACTION_SETTINGS_CHANGED).setPackage(packageName))
            } catch (_: Exception) {}
        }
        @JavascriptInterface fun readPreview(uriString: String): String {
            return try {
                val uri = Uri.parse(uriString)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 1000 || bounds.outHeight / sample > 1000) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return ""
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                bitmap.recycle()
                "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            } catch (_: Exception) { "" }
        }
        @JavascriptInterface fun openWallpaperPicker() {
            runOnUiThread {
                try { startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, android.content.ComponentName(this@MainActivity, WallpaperServiceImpl::class.java))) }
                catch (_: Exception) { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
            }
        }
    }

    private fun collectImages(): JSONArray {
        val result = JSONArray()
        val trees = JSONArray(prefs.getString("trees", "[]"))
        for (i in 0 until trees.length()) {
            val tree = Uri.parse(trees.optString(i))
            val folderName = queryDisplayName(tree) ?: "선택 폴더 ${i + 1}"
            collectFromTree(tree, tree, folderName, result, 0)
        }
        return result
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Exception) { null }

    private fun collectFromTree(tree: Uri, directory: Uri, folder: String, out: JSONArray, depth: Int) {
        if (depth > 12 || out.length() > 5000) return
        try {
            val docId = DocumentsContract.getDocumentId(directory)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: "image"
                    val mime = c.getString(2) ?: ""
                    val child = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) collectFromTree(tree, child, "$folder / $name", out, depth + 1)
                    else if (mime.startsWith("image/")) out.put(JSONObject().put("uri", child.toString()).put("name", name).put("folder", folder))
                }
            }
        } catch (_: Exception) {}
    }
}
