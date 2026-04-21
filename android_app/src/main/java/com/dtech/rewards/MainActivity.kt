package com.dtech.rewards

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        if (sessionManager.getUsername() != null) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)

        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val registerButton = findViewById<Button>(R.id.registerButton)

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            loginButton.text = "Logging in..."

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = apiService.login(AuthRequest(username, password))
                    withContext(Dispatchers.Main) {
                        loginButton.isEnabled = true
                        loginButton.text = "Login"
                        if (response.isSuccessful) {
                            Toast.makeText(this@MainActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                            sessionManager.saveUsername(username)
                            startActivity(Intent(this@MainActivity, DashboardActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this@MainActivity, "Login failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        loginButton.isEnabled = true
                        loginButton.text = "Login"
                        Toast.makeText(this@MainActivity, "Network error.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        registerButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerButton.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = apiService.register(AuthRequest(username, password))
                    withContext(Dispatchers.Main) {
                        registerButton.isEnabled = true
                        if (response.isSuccessful) {
                            Toast.makeText(this@MainActivity, "Account created! You can now log in.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Registration failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        registerButton.isEnabled = true
                        Toast.makeText(this@MainActivity, "Network error.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
