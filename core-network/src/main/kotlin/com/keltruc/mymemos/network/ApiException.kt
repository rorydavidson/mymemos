package com.keltruc.mymemos.network

import com.keltruc.mymemos.network.dto.ApiErrorDto
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/** Server-reported error with the gRPC status code and message from the body. */
class ApiException(val httpStatus: Int, val grpcCode: Int, message: String) : Exception(message) {
    val isUnauthenticated: Boolean get() = httpStatus == 401 || grpcCode == 16
    val isNotFound: Boolean get() = httpStatus == 404 || grpcCode == 5

    companion object {
        fun from(e: HttpException, json: Json): ApiException {
            val body = e.response()?.errorBody()?.string().orEmpty()
            val parsed = runCatching { json.decodeFromString<ApiErrorDto>(body) }.getOrNull()
            return ApiException(e.code(), parsed?.code ?: 0, parsed?.message?.ifEmpty { null } ?: e.message())
        }
    }
}
