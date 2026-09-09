package com.keltruc.mymemos.network.api

import com.keltruc.mymemos.network.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * The only real [MemosApi]. Every method is one request; the client the factory built
 * handles auth, cookies and turning a non-2xx into an
 * [com.keltruc.mymemos.network.ApiException].
 *
 * Resource names such as `memos/abc` are interpolated straight into the path so their
 * slashes stay separators, which is what the server's grpc-gateway routing expects.
 */
internal class KtorMemosApi(private val client: HttpClient) : MemosApi {

    // Instance
    override suspend fun getInstanceProfile(): InstanceProfileDto =
        client.get("api/v1/instance/profile").body()

    // Auth
    override suspend fun signIn(body: SignInRequestDto): SignInResponseDto =
        client.post("api/v1/auth/signin") { json(body) }.body()

    override suspend fun refreshToken(): RefreshTokenResponseDto =
        client.post("api/v1/auth/refresh").body()

    override suspend fun signOut() {
        client.post("api/v1/auth/signout")
    }

    override suspend fun getCurrentUser(): GetCurrentUserResponseDto =
        client.get("api/v1/auth/me").body()

    // Users
    override suspend fun getUser(name: String): UserDto = client.get("api/v1/$name").body()

    // Memos
    override suspend fun listMemos(
        pageSize: Int,
        pageToken: String?,
        state: String?,
        orderBy: String?,
        filter: String?,
    ): ListMemosResponseDto = client.get("api/v1/memos") {
        parameter("pageSize", pageSize)
        parameter("pageToken", pageToken)
        parameter("state", state)
        parameter("orderBy", orderBy)
        parameter("filter", filter)
    }.body()

    override suspend fun getMemo(name: String): MemoDto = client.get("api/v1/$name").body()

    override suspend fun createMemo(memo: MemoWriteDto, memoId: String?): MemoDto =
        client.post("api/v1/memos") {
            parameter("memoId", memoId)
            json(memo)
        }.body()

    override suspend fun updateMemo(name: String, memo: MemoWriteDto, updateMask: String): MemoDto =
        client.patch("api/v1/$name") {
            parameter("updateMask", updateMask)
            json(memo)
        }.body()

    override suspend fun deleteMemo(name: String) {
        client.delete("api/v1/$name")
    }

    // Attachments
    override suspend fun createAttachment(attachment: AttachmentCreateDto): AttachmentDto =
        client.post("api/v1/attachments") { json(attachment) }.body()

    override suspend fun listMemoAttachments(memoName: String): ListAttachmentsResponseDto =
        client.get("api/v1/$memoName/attachments").body()

    override suspend fun setMemoAttachments(memoName: String, body: SetMemoAttachmentsRequestDto) {
        client.patch("api/v1/$memoName/attachments") { json(body) }
    }

    override suspend fun deleteAttachment(name: String) {
        client.delete("api/v1/$name")
    }

    // Comments, reactions, relations, shares
    override suspend fun listMemoComments(memoName: String, pageSize: Int): ListMemoCommentsResponseDto =
        client.get("api/v1/$memoName/comments") { parameter("pageSize", pageSize) }.body()

    override suspend fun createMemoComment(memoName: String, comment: MemoWriteDto): MemoDto =
        client.post("api/v1/$memoName/comments") { json(comment) }.body()

    override suspend fun listMemoReactions(memoName: String): ListReactionsResponseDto =
        client.get("api/v1/$memoName/reactions").body()

    override suspend fun upsertMemoReaction(memoName: String, body: UpsertReactionRequestDto): ReactionDto =
        client.post("api/v1/$memoName/reactions") { json(body) }.body()

    override suspend fun deleteMemoReaction(reactionName: String) {
        client.delete("api/v1/$reactionName")
    }

    override suspend fun setMemoRelations(memoName: String, body: SetMemoRelationsRequestDto) {
        client.patch("api/v1/$memoName/relations") { json(body) }
    }

    override suspend fun createMemoShare(memoName: String, share: MemoShareDto): MemoShareDto =
        client.post("api/v1/$memoName/shares") { json(share) }.body()

    override suspend fun listMemoShares(memoName: String): ListMemoSharesResponseDto =
        client.get("api/v1/$memoName/shares").body()

    override suspend fun deleteMemoShare(shareName: String) {
        client.delete("api/v1/$shareName")
    }

    // Shortcuts
    override suspend fun listShortcuts(userName: String): ListShortcutsResponseDto =
        client.get("api/v1/$userName/shortcuts").body()

    override suspend fun createShortcut(userName: String, shortcut: ShortcutDto): ShortcutDto =
        client.post("api/v1/$userName/shortcuts") { json(shortcut) }.body()

    override suspend fun updateShortcut(name: String, shortcut: ShortcutDto, updateMask: String): ShortcutDto =
        client.patch("api/v1/$name") {
            parameter("updateMask", updateMask)
            json(shortcut)
        }.body()

    override suspend fun deleteShortcut(name: String) {
        client.delete("api/v1/$name")
    }

    // User profile, stats, settings, tokens, webhooks, notifications
    override suspend fun updateUser(name: String, user: UserWriteDto, updateMask: String): UserDto =
        client.patch("api/v1/$name") {
            parameter("updateMask", updateMask)
            json(user)
        }.body()

    override suspend fun getUserStats(userName: String): UserStatsDto =
        client.get("api/v1/$userName:getStats").body()

    override suspend fun getUserSetting(settingName: String): UserSettingDto =
        client.get("api/v1/$settingName").body()

    override suspend fun updateUserSetting(settingName: String, setting: UserSettingDto, updateMask: String): UserSettingDto =
        client.patch("api/v1/$settingName") {
            parameter("updateMask", updateMask)
            json(setting)
        }.body()

    override suspend fun listPersonalAccessTokens(userName: String): ListPersonalAccessTokensResponseDto =
        client.get("api/v1/$userName/personalAccessTokens").body()

    override suspend fun createPersonalAccessToken(
        userName: String,
        body: CreatePersonalAccessTokenRequestDto,
    ): CreatePersonalAccessTokenResponseDto =
        client.post("api/v1/$userName/personalAccessTokens") { json(body) }.body()

    override suspend fun deletePersonalAccessToken(name: String) {
        client.delete("api/v1/$name")
    }

    override suspend fun listUserWebhooks(userName: String): ListUserWebhooksResponseDto =
        client.get("api/v1/$userName/webhooks").body()

    override suspend fun createUserWebhook(userName: String, webhook: UserWebhookDto): UserWebhookDto =
        client.post("api/v1/$userName/webhooks") { json(webhook) }.body()

    override suspend fun deleteUserWebhook(name: String) {
        client.delete("api/v1/$name")
    }

    override suspend fun listUserNotifications(
        userName: String,
        pageSize: Int,
        filter: String?,
    ): ListNotificationsResponseDto = client.get("api/v1/$userName/notifications") {
        parameter("pageSize", pageSize)
        parameter("filter", filter)
    }.body()

    override suspend fun updateUserNotification(
        name: String,
        body: NotificationWriteDto,
        updateMask: String,
    ): UserNotificationDto = client.patch("api/v1/$name") {
        parameter("updateMask", updateMask)
        json(body)
    }.body()

    override suspend fun deleteUserNotification(name: String) {
        client.delete("api/v1/$name")
    }

    // Admin
    override suspend fun listUsers(pageSize: Int, showDeleted: Boolean): ListUsersResponseDto =
        client.get("api/v1/users") {
            parameter("pageSize", pageSize)
            parameter("showDeleted", showDeleted)
        }.body()

    override suspend fun createUser(user: UserWriteDto): UserDto =
        client.post("api/v1/users") { json(user) }.body()

    override suspend fun deleteUser(name: String, force: Boolean) {
        client.delete("api/v1/$name") { parameter("force", force) }
    }

    override suspend fun getInstanceSetting(settingName: String): InstanceSettingDto =
        client.get("api/v1/$settingName").body()

    override suspend fun updateInstanceSetting(
        settingName: String,
        setting: InstanceSettingDto,
        updateMask: String,
    ): InstanceSettingDto = client.patch("api/v1/$settingName") {
        parameter("updateMask", updateMask)
        json(setting)
    }.body()

    override suspend fun getInstanceStats(): InstanceStatsDto =
        client.get("api/v1/instance/stats").body()
}

/** Retrofit's `@Body` in one line: JSON content type plus the payload. */
private inline fun <reified T : Any> io.ktor.client.request.HttpRequestBuilder.json(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
