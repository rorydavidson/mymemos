package com.keltruc.mymemos.network.dto

import kotlinx.serialization.Serializable

/** gRPC-gateway error body: `{"code":3,"message":"...","details":[]}`. */
@Serializable
data class ApiErrorDto(
    val code: Int = 0,
    val message: String = "",
)

@Serializable
data class LocationDto(
    val placeholder: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)
