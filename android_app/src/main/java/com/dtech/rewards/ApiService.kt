package com.dtech.rewards

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @POST("/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @POST("/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @GET("/user")
    suspend fun getUserPoints(@Query("username") username: String): Response<PointsResponse>

    @POST("/add-point")
    suspend fun addPoint(@Body request: AddPointRequest): Response<PointsResponse>

    @POST("/withdraw")
    suspend fun withdraw(@Body request: WithdrawRequest): Response<WithdrawResponse>
}
