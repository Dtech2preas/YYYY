package com.dtech.rewards

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WithdrawActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiService: ApiService
    private lateinit var balanceText: TextView
    private lateinit var withdrawButton: Button
    private lateinit var tierSpinner: Spinner
    private lateinit var accountNumberInput: EditText
    private lateinit var accountNameInput: EditText

    private var currentPoints = 0

    private val tiers = listOf(
        Pair(1000, 10),
        Pair(1500, 15),
        Pair(2000, 20)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_withdraw)

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

        balanceText = findViewById(R.id.balanceText)
        withdrawButton = findViewById(R.id.withdrawButton)
        tierSpinner = findViewById(R.id.tierSpinner)
        accountNumberInput = findViewById(R.id.accountNumberInput)
        accountNameInput = findViewById(R.id.accountNameInput)

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tiers.map { "${it.first} Points = ${it.second} USD" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        tierSpinner.adapter = adapter

        tierSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                checkEligibility()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        withdrawButton.setOnClickListener {
            val position = tierSpinner.selectedItemPosition
            val tier = tiers[position]
            val accountNumber = accountNumberInput.text.toString().trim()
            val accountName = accountNameInput.text.toString().trim()

            if (accountNumber.isEmpty()) {
                Toast.makeText(this, "Please enter a valid Bank Account Number.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            withdrawButton.isEnabled = false
            withdrawButton.text = "Processing..."

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = apiService.withdraw(WithdrawRequest(username, tier.first, tier.second, accountNumber, accountName))
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@WithdrawActivity, "Withdrawal requested successfully!", Toast.LENGTH_LONG).show()
                            accountNumberInput.text.clear()
                            accountNameInput.text.clear()
                            fetchPoints()
                        } else {
                            Toast.makeText(this@WithdrawActivity, "Failed to request withdrawal.", Toast.LENGTH_SHORT).show()
                            withdrawButton.isEnabled = true
                            withdrawButton.text = "Request Withdrawal"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@WithdrawActivity, "Network error.", Toast.LENGTH_SHORT).show()
                        withdrawButton.isEnabled = true
                        withdrawButton.text = "Request Withdrawal"
                    }
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
                        currentPoints = response.body()?.points ?: 0
                        balanceText.text = "Your Balance: $currentPoints pts"
                        checkEligibility()
                    }
                }
            } catch (e: Exception) {
                // handle silently
            }
        }
    }

    private fun checkEligibility() {
        val position = tierSpinner.selectedItemPosition
        if (position >= 0 && position < tiers.size) {
            val requiredPoints = tiers[position].first
            if (currentPoints >= requiredPoints) {
                withdrawButton.isEnabled = true
                withdrawButton.text = "Request Withdrawal"
            } else {
                withdrawButton.isEnabled = false
                withdrawButton.text = "Not Enough Points"
            }
        }
    }
}
