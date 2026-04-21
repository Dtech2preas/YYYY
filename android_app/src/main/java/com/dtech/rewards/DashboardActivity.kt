package com.dtech.rewards

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DashboardActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiService: ApiService
    private lateinit var pointsDisplay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

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

        findViewById<TextView>(R.id.greetingText).text = "Hello, $username"
        pointsDisplay = findViewById(R.id.pointsDisplay)

        findViewById<TextView>(R.id.logoutText).setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.berserkerButton).setOnClickListener {
            startActivity(Intent(this, BerserkerActivity::class.java))
        }

        findViewById<Button>(R.id.profileButton).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<Button>(R.id.withdrawButton).setOnClickListener {
            startActivity(Intent(this, WithdrawActivity::class.java))
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
                        pointsDisplay.text = response.body()?.points?.toString() ?: "0"
                    } else if (response.code() == 403) {
                        Toast.makeText(this@DashboardActivity, "Your account is banned.", Toast.LENGTH_LONG).show()
                        sessionManager.clearSession()
                        startActivity(Intent(this@DashboardActivity, MainActivity::class.java))
                        finish()
                    }
                }
            } catch (e: Exception) {
                // handle error silently
            }
        }
    }
}
