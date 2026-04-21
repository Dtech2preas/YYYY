package com.dtech.rewards

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class BerserkerActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiService: ApiService
    private lateinit var pointsBadge: TextView
    private lateinit var watchAdButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_berserker)

        sessionManager = SessionManager(this)
        val username = sessionManager.getUsername()
        if (username == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)

        pointsBadge = findViewById(R.id.pointsBadge)
        watchAdButton = findViewById(R.id.watchAdButton)

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
        }

        watchAdButton.setOnClickListener {
            watchAdButton.isEnabled = false
            watchAdButton.text = "Processing..."

            // Open Ad in Browser
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://otieu.com/4/10205357"))
            startActivity(browserIntent)

            // Add Point Request
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = apiService.addPoint(AddPointRequest(username))
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            pointsBadge.text = "${response.body()?.points ?: 0} pts"
                            Toast.makeText(this@BerserkerActivity, "+1 Point Added!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@BerserkerActivity, "Failed to add point", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@BerserkerActivity, "Network error. Point not added.", Toast.LENGTH_SHORT).show()
                    }
                }

                // Re-enable button after 3 seconds
                delay(3000)
                withContext(Dispatchers.Main) {
                    watchAdButton.isEnabled = true
                    watchAdButton.text = "WATCH AD (+1 PT)"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        fetchPoints()
    }

    private fun fetchPoints() {
        val username = sessionManager.getUsername() ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getUserPoints(username)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        pointsBadge.text = "${response.body()?.points ?: 0} pts"
                    }
                }
            } catch (e: Exception) {
                // handle silently
            }
        }
    }
}
