package kr.jh.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class WallpaperServiceImpl : WallpaperService() {
    companion object { const val ACTION_SETTINGS_CHANGED = "kr.jh.wallpaper.SETTINGS_CHANGED" }
    override fun onCreateEngine(): Engine = WallpaperEngine()

    inner class WallpaperEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var currentIndex = 0
        private var lastTap = 0L
        private var bitmap: Bitmap? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; setShadowLayer(5f, 1f, 2f, Color.BLACK) }
        private val tick = object : Runnable { override fun run() { if (visible) { advance(); drawFrame(); schedule() } } }
        private val settingsReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) { currentIndex = 0; loadBitmap(); drawFrame(); schedule() }
        }

        init {
            setTouchEventsEnabled(true)
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(settingsReceiver, android.content.IntentFilter(ACTION_SETTINGS_CHANGED), android.content.Context.RECEIVER_NOT_EXPORTED)
            else @Suppress("DEPRECATION") registerReceiver(settingsReceiver, android.content.IntentFilter(ACTION_SETTINGS_CHANGED))
        }

        override fun onVisibilityChanged(visibleNow: Boolean) {
            visible = visibleNow
            handler.removeCallbacks(tick)
            if (visible) { loadBitmap(); drawFrame(); schedule() }
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { super.onSurfaceChanged(holder, format, width, height); drawFrame() }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) { visible = false; handler.removeCallbacks(tick); super.onSurfaceDestroyed(holder) }
        override fun onDestroy() { handler.removeCallbacks(tick); try { unregisterReceiver(settingsReceiver) } catch (_: Exception) {}; bitmap?.recycle(); bitmap = null; super.onDestroy() }

        private fun settings(): JSONObject = try { JSONObject(getSharedPreferences("wallpaper", MODE_PRIVATE).getString("settings", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        private fun images(): JSONArray = settings().optJSONArray("images") ?: JSONArray()
        private fun activeImages(): List<JSONObject> {
            val arr = images(); val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) { val item = arr.optJSONObject(i) ?: continue; if (item.optBoolean("enabled", true)) list.add(item) }
            return list
        }
        private fun loadBitmap() {
            val list = activeImages()
            if (list.isEmpty()) { bitmap?.recycle(); bitmap = null; currentIndex = 0; return }
            if (currentIndex >= list.size) currentIndex = 0
            val uri = Uri.parse(list[currentIndex].optString("uri"))
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val maxW = (resources.displayMetrics.widthPixels * 2).coerceAtLeast(1080)
                val maxH = (resources.displayMetrics.heightPixels * 2).coerceAtLeast(1920)
                var sample = 1
                while (bounds.outWidth / sample > maxW || bounds.outHeight / sample > maxH) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val next = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                if (next != null) { bitmap?.takeIf { it !== next && !it.isRecycled }?.recycle(); bitmap = next }
            } catch (_: Exception) { bitmap?.recycle(); bitmap = null }
        }
        private fun advance() {
            val list = activeImages(); if (list.size <= 1) return
            if (settings().optString("order", "random") == "sequential") currentIndex = (currentIndex + 1) % list.size
            else { var next = currentIndex; while (next == currentIndex) next = Random.nextInt(list.size); currentIndex = next }
            loadBitmap()
        }
        private fun schedule() {
            handler.removeCallbacks(tick)
            val seconds = settings().optInt("interval", 30)
            if (visible && seconds > 0 && activeImages().size > 1) handler.postDelayed(tick, seconds * 1000L)
        }
        override fun onTouchEvent(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_UP && settings().optBoolean("doubleTap", true)) {
                val now = System.currentTimeMillis()
                if (now - lastTap < 350) { advance(); drawFrame() }
                lastTap = now
            }
            super.onTouchEvent(event)
        }
        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                canvas.drawColor(Color.BLACK)
                val b = bitmap
                if (b != null && !b.isRecycled) {
                    val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
                    val mode = settings().optString("fitMode", "mirror")
                    val srcRatio = b.width.toFloat() / b.height
                    val dstRatio = w / h
                    if (mode == "mirror") {
                        val scale = maxOf(w / b.width, h / b.height)
                        val bw = b.width * scale; val bh = b.height * scale
                        paint.alpha = 105
                        canvas.drawBitmap(b, null, RectF((w-bw)/2, (h-bh)/2, (w+bw)/2, (h+bh)/2), paint)
                        paint.alpha = 255
                        val s = minOf(w / b.width, h / b.height)
                        val rw=b.width*s; val rh=b.height*s
                        canvas.drawBitmap(b, null, RectF((w-rw)/2,(h-rh)/2,(w+rw)/2,(h+rh)/2), paint)
                    } else {
                        var scale = if (mode == "contain") minOf(w / b.width, h / b.height) else maxOf(w / b.width, h / b.height)
                        if (mode == "smart-center") scale *= .9f
                        if (mode == "stretch") canvas.drawBitmap(b, null, RectF(0f,0f,w,h), paint)
                        else { val rw=b.width*scale; val rh=b.height*scale; canvas.drawBitmap(b, null, RectF((w-rw)/2,(h-rh)/2,(w+rw)/2,(h+rh)/2), paint) }
                    }
                    val list = activeImages()
                    if (settings().optBoolean("showFilename", true) && list.isNotEmpty()) {
                        val item = list.getOrNull(currentIndex.coerceAtMost(list.lastIndex))
                        val label = item?.let { "${it.optString("folder")} / ${it.optString("name")}" } ?: ""
                        textPaint.textSize = settings().optInt("fontSize", 12).coerceIn(10, 36) * resources.displayMetrics.density
                        val y = h - 42f * resources.displayMetrics.density
                        val fm = textPaint.fontMetrics
                        val width = textPaint.measureText(label).coerceAtMost(w - 32f)
                        val rect = RectF((w-width)/2-18, y+fm.ascent-8, (w+width)/2+18, y+fm.descent+8)
                        paint.color = Color.argb(165,0,0,0); canvas.drawRoundRect(rect, 24f,24f,paint); paint.color=Color.WHITE
                        canvas.drawText(label, w/2, y, textPaint)
                    }
                } else {
                    textPaint.textSize = 18f; canvas.drawText("앱에서 이미지 폴더를 추가해 주세요", canvas.width/2f, canvas.height/2f, textPaint)
                }
            } catch (_: Exception) { } finally { if (canvas != null) try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {} }
        }
    }
}
