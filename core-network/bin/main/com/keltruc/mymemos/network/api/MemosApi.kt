package com.keltruc.mymemos.network.api

import com.keltruc.mymemos.network.dto.*
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

    // Comments, reactions, relations, shares
    @GET("api/v1/{name}/comments")
    suspend fun listMemoComments(@Path("name", encoded = true) memoName: String, @Query("pageSize") pageSize: Int = 200): ListMemoCommentsResponseDto

    @POST("api/v1/{name}/comments")
    suspend fun createMemoComment(@Path("name", encoded = true) memoName: String, @Body comment: MemoWriteDto): MemoDto

    @GET("api/v1/{name}/reactions")
    suspend fun listMemoReactions(@Path("name", encoded = true) memoName: String): ListReactionsResponseDto

    @POST("api/v1/{name}/reactions")
    suspend fun upsertMemoReaction(@Path("name", encoded = true) memoName: String, @Body body: UpsertReactionRequestDto): ReactionDto

    @DELETE("api/v1/{name}")
    suspend fun deleteMemoReaction(@Path("name", encoded = true) reactionName: String)

    @PATCH("api/v1/{name}/relations")
    suspend fun setMemoRelations(@Path("name", encoded = true) memoName: String, @Body body: SetMemoRelationsRequestDto)

    @POST("api/v1/{parent}/shares")
    suspend fun createMemoShare(@Path("parent", encoded = true) memoName: String, @Body share: MemoShareDto): MemoShareDto

    @GET("api/v1/{parent}/shares")
    suspend fun listMemoShares(@Path("parent", encoded = true) memoName: String): ListMemoSharesResponseDto

    @DELETE("api/v1/{name}")
    suspend fun deleteMemoShare(@Path("name", encoded = true) shareName: String)

    // Shortcuts
    @GET("api/v1/{parent}/shortcuts")
    suspend fun listShortcuts(@Path("parent", encoded = true) userName: String): ListShortcutsResponseDto

    @POST("api/v1/{parent}/shortcuts")
    suspend fun createShortcut(@Path("parent", encoded = true) userName: String, @Body shortcut: ShortcutDto): ShortcutDto

    @PATCH("api/v1/{name}")
    suspend fun updateShortcut(
        @Path("name", encoded = true) name: String,
        @Body shortcut: ShortcutDto,
        @Query("updateMask") updateMask: String = "title,filter",
    ): ShortcutDto

    @DELETE("api/v1/{name}")
    suspend fun deleteShortcut(@Path("name", encoded = true) name: String)

    // User profile, stats, settings, tokens, webhooks, notifications
    @PATCH("api/v1/{name}")
    suspend fun updateUser(
        @Path("name", encoded = true) name: String,
        @Body user: UserWriteDto,
        @Query("updateMask") updateMask: String,
    ): UserDto

    @GET("api/v1/{name}:getStats")
    suspend fun getUserStats(@Path("name", encoded = true) userName: String): UserStatsDto

    @GET("api/v1/{name}")
    suspend fun getUserSetting(@Path("name", encoded = true) settingName: String): UserSettingDto

    @PATCH("api/v1/{name}")
    suspend fun updateUserSetting(
        @Path("name", encoded = true) settingName: String,
        @Body setting: UserSettingDto,
        @Query("updateMask") updateMask: String,
    ): UserSettingDto

    @GET("api/v1/{parent}/personalAccessTokens")
    suspend fun listPersonalAccessTokens(@Path("parent", encoded = true) userName: String): ListPersonalAccessTokensResponseDto

    @POST("api/v1/{parent}/personalAccessTokens")
    suspend fun createPersonalAccessToken(
        @Path("parent", encoded = true) userName: String,
        @Body body: CreatePersonalAccessTokenRequestDto,
    ): CreatePersonalAccessTokenResponseDto

    @DELETE("api/v1/{name}")
    suspend fun deletePersonalAccessToken(@Path("name", encoded = true) name: String)

    @GET("api/v1/{parent}/webhooks")
    suspend fun listUserWebhooks(@Path("parent", encoded = true) userName: String): ListUserWebhooksResponseDto

    @POST("api/v1/{parent}/webhooks")
    suspend fun createUserWebhook(@Path("parent", encoded = true) userName: String, @Body webhook: UserWebhookDto): UserWebhookDto

    @DELETE("api/v1/{name}")
    suspend fun deleteUserWebhook(@Path("name", encoded = true) name: String)

    @GET("api/v1/{parent}/notifications")
    suspend fun listUserNotifications(
        @Path("parent", encoded = true) userName: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("filter") filter: String? = null,
    ): ListNotificationsResponseDto

    @PATCH("api/v1/{name}")
    suspend fun updateUserNotification(
        @Path("name", encoded = true) name: String,
        @Body body: NotificationWriteDto,
        @Query("updateMask") updateMask: String = "status",
    ): UserNotificationDto

    @DELETE("api/v1/{name}")
    suspend fun deleteUserNotification(@Path("name", encoded = true) name: String)

    // Admin
    @GET("api/v1/users")
    suspend fun listUsers(@Query("pageSize") pageSize: Int = 200, @Query("showDeleted") showDeleted: Boolean = false): ListUsersResponseDto

    @POST("api/v1/users")
    suspend fun createUser(@Body user: UserWriteDto): UserDto

    @DELETE("api/v1/{name}")
    suspend fun deleteUser(@Path("name", encoded = true) name: String, @Query("force") force: Boolean = false)

    @GET("api/v1/{name}")
    suspend fun getInstanceSetting(@Path("name", encoded = true) settingName: String): InstanceSettingDto

    @PATCH("api/v1/{name}")
    suspend fun updateInstanceSetting(
        @Path("name", encoded = true) settingName: String,
        @Body setting: InstanceSettingDto,
        @Query("updateMask") updateMask: String,
    ): InstanceSettingDto

    @GET("api/v1/instance/stats")
    suspend fun getInstanceStats(): InstanceStatsDto
}
