package com.dtech.rewards

data class AuthRequest(val username: String, val password: String)
data class AuthResponse(val success: Boolean?, val error: String?)
data class PointsResponse(val points: Int?, val error: String?)
data class AddPointRequest(val username: String)
data class WithdrawRequest(
    val username: String,
    val points: Int,
    val amount: Int,
    val accountNumber: String,
    val accountName: String
)
data class WithdrawResponse(val success: Boolean?, val error: String?)
