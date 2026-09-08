package com.keltruc.mymemos.network.api

import com.keltruc.mymemos.network.dto.AttachmentCreateDto
import com.keltruc.mymemos.network.dto.AttachmentDto
import com.keltruc.mymemos.network.dto.GetCurrentUserResponseDto
import com.keltruc.mymemos.network.dto.InstanceProfileDto
import com.keltruc.mymemos.network.dto.ListAttachmentsResponseDto
import com.keltruc.mymemos.network.dto.ListMemosResponseDto
import com.keltruc.mymemos.network.dto.MemoDto
import com.keltruc.mymemos.network.dto.MemoWriteDto
import com.keltruc.mymemos.network.dto.RefreshTokenResponseDto
import com.keltruc.mymemos.network.dto.SetMemoAttachmentsRequestDto
import com.keltruc.mymemos.network.dto.SignInRequestDto
import com.keltruc.mymemos.network.dto.SignInResponseDto
import com.keltruc.mymemos.network.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Memos v1 REST surface (grpc-gateway). Resource names such as `memos/abc` are passed
 * whole in the path; `{name}` is declared `encoded = true` so the slash survives.
 */
interface MemosApi {

    // Instance
    @GET("api/v1/instance/profile")
    suspend fun getInstanceProfile(): InstanceProfileDto

    // Auth
    @POST("api/v1/auth/signin")
    suspend fun signIn(@Body body: SignInRequestDto): SignInResponseDto

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(): RefreshTokenResponseDto

    @POST("api/v1/auth/signout")
    suspend fun signOut()

    @GET("api/v1/auth/me")
    suspend fun getCurrentUser(): GetCurrentUserResponseDto

    // Users
    @GET("api/v1/{name}")
    suspend fun getUser(@Path("name", encoded = true) name: String): UserDto

    // Memos
    @GET("api/v1/memos")
    suspend fun listMemos(
        @Query("pageSize") pageSize: Int = 50,
        @Query("pageToken") pageToken: String? = null,
        @Query("state") state: String? = null,
        @Query("orderBy") orderBy: String? = null,
        @Query("filter") filter: String? = null,
    ): ListMemosResponseDto

    @GET("api/v1/{name}")
    suspend fun getMemo(@Path("name", encoded = true) name: String): MemoDto

    @POST("api/v1/memos")
    suspend fun createMemo(
        @Body memo: MemoWriteDto,
        @Query("memoId") memoId: String? = null,
    ): MemoDto

    @PATCH("api/v1/{name}")
    suspend fun updateMemo(
        @Path("name", encoded = true) name: String,
        @Body memo: MemoWriteDto,
        @Query("updateMask") updateMask: String,
    ): MemoDto

    @DELETE("api/v1/{name}")
    suspend fun deleteMemo(@Path("name", encoded = true) name: String)

    // Attachments
    @POST("api/v1/attachments")
    suspend fun createAttachment(@Body attachment: AttachmentCreateDto): AttachmentDto

    @GET("api/v1/{name}/attachments")
    suspend fun listMemoAttachments(@Path("name", encoded = true) memoName: String): ListAttachmentsResponseDto

    /** Replaces the memo's full attachment set. Empty list clears it. */
    @PATCH("api/v1/{name}/attachments")
    suspend fun setMemoAttachments(
        @Path("name", encoded = true) memoName: String,
        @Body body: SetMemoAttachmentsRequestDto,
    )

    @DELETE("api/v1/{name}")
    suspend fun deleteAttachment(@Path("name", encoded = true) name: String)
}
