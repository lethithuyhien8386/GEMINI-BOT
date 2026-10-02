package com.example.geminigamebot

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class FloatingWidgetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()

    private val botRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            callGeminiAI()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_widget, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        windowManager.addView(floatingView, params)

        val ivDragHandle = floatingView.findViewById<View>(R.id.ivDragHandle)
        val btnToggleBot = floatingView.findViewById<Button>(R.id.btnToggleBot)
        val btnClose = floatingView.findViewById<Button>(R.id.btnClose)

        ivDragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        btnToggleBot.setOnClickListener {
            isRunning = !isRunning
            if (isRunning) {
                btnToggleBot.text = "STOP"
                Toast.makeText(this, "Game Bot Started!", Toast.LENGTH_SHORT).show()
                handler.post(botRunnable)
            } else {
                btnToggleBot.text = "START"
                Toast.makeText(this, "Game Bot Stopped!", Toast.LENGTH_SHORT).show()
                handler.removeCallbacks(botRunnable)
            }
        }

        btnClose.setOnClickListener {
            stopSelf()
        }
    }

    private fun callGeminiAI() {
        val prefs = getSharedPreferences("BotPrefs", MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: return
        if (apiKey.isEmpty()) return

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

        val jsonBody = JSONObject().apply {
            val contents = JSONArray()
            val content = JSONObject()
            val parts = JSONArray()
            parts.put(JSONObject().put("text", "You are an expert game playing AI bot. Return JSON only with format: {"action": "click" or "swipe", "x": 500, "y": 500, "endX": 500, "endY": 1000, "duration": 300, "reason": "..."}. Assume screen size is 1080x1920."))
            content.put("parts", parts)
            contents.put(content)
            put("contents", contents)
        }

        val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val respStr = response.body?.string() ?: return
                try {
                    val jsonResp = JSONObject(respStr)
                    val text = jsonResp.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    val cleanJson = text.replace("```json", "").replace("```", "").trim()
                    val actionObj = JSONObject(cleanJson)

                    val action = actionObj.optString("action")
                    val x = actionObj.optDouble("x", 500.0).toFloat()
                    val y = actionObj.optDouble("y", 500.0).toFloat()

                    handler.post {
                        val accessibility = GameAccessibilityService.instance
                        if (accessibility != null) {
                            if (action == "click") {
                                accessibility.click(x, y)
                            } else if (action == "swipe") {
                                val endX = actionObj.optDouble("endX", 500.0).toFloat()
                                val endY = actionObj.optDouble("endY", 1000.0).toFloat()
                                val duration = actionObj.optLong("duration", 300L)
                                accessibility.swipe(x, y, endX, endY, duration)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(botRunnable)
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}