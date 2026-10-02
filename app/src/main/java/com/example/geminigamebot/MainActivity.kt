package com.example.geminigamebot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.geminigamebot.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("BotPrefs", MODE_PRIVATE)
        binding.etApiKey.setText(prefs.getString("api_key", ""))

        binding.btnSaveKey.setOnClickListener {
            val key = binding.etApiKey.text.toString().trim()
            if (key.isNotEmpty()) {
                prefs.edit().putString("api_key", key).apply()
                Toast.makeText(this, "API Key Saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter a valid API Key", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGrantOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.btnStartFloating.setOnClickListener {
            val key = prefs.getString("api_key", "")
            if (key.isNullOrEmpty()) {
                Toast.makeText(this, "Please save your Google AI Studio API Key first!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant Overlay Permission first!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            
            val serviceIntent = Intent(this, FloatingWidgetService::class.java)
            startService(serviceIntent)
            Toast.makeText(this, "Floating control started!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}