package com.keltruc.mymemos.network

import com.keltruc.mymemos.network.dto.ApiErrorDto
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/** Server-reported error with the gRPC status code and message from the body. */
class ApiException(val httpStatus: Int, val grpcCode: Int, message: String) : Exception(message) {
    val isUnauthenticated: Boolean get() = httpStatus == 401 || grpcCode == 16
    val isNotFound: Boolean get() = httpStatus == 404 || grpcCode == 5

    companion object {
        suspend fun from(e: ResponseException, json: Json): ApiException {
            val body = runCatching { e.response.bodyAsText() }.getOrDefault("")
            val parsed = runCatching { json.decodeFromString<ApiErrorDto>(body) }.getOrNull()
            val status = e.response.status
            return ApiException(
                httpStatus = status.value,
                grpcCode = parsed?.code ?: 0,
                message = parsed?.message?.ifEmpty { null }
                    ?: status.description.ifEmpty { "HTTP ${status.value}" },
            )
        }
    }
}
