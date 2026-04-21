package com.dtech.rewards

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiService: ApiService
    private lateinit var profilePoints: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

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

        findViewById<TextView>(R.id.profileUsername).text = username
        profilePoints = findViewById(R.id.profilePoints)

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
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
                        profilePoints.text = "Total Points: ${response.body()?.points ?: 0}"
                    }
                }
            } catch (e: Exception) {
                // Handle silently
            }
        }
    }
}
