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

/**
 * Memos v1 REST surface (grpc-gateway). Resource names such as `memos/abc` are passed whole
 * and go into the path unescaped, so the slash survives as a separator.
 *
 * This stays an interface rather than becoming a client class so that callers do not care
 * which HTTP stack is underneath, and so a fake can be substituted in tests.
 * [com.keltruc.mymemos.network.api.KtorMemosApi] is the only real implementation.
 *
 * Every method throws [com.keltruc.mymemos.network.ApiException] when the server answers
 * with anything outside 2xx.
 */
interface MemosApi {

    // Instance
    suspend fun getInstanceProfile(): InstanceProfileDto

    // Auth
    suspend fun signIn(body: SignInRequestDto): SignInResponseDto

    suspend fun refreshToken(): RefreshTokenResponseDto

    suspend fun signOut()

    suspend fun getCurrentUser(): GetCurrentUserResponseDto

    // Users
    suspend fun getUser(name: String): UserDto

    // Memos
    suspend fun listMemos(
        pageSize: Int = 50,
        pageToken: String? = null,
        state: String? = null,
        orderBy: String? = null,
        filter: String? = null,
    ): ListMemosResponseDto

    suspend fun getMemo(name: String): MemoDto

    suspend fun createMemo(
        memo: MemoWriteDto,
        memoId: String? = null,
    ): MemoDto

    suspend fun updateMemo(
        name: String,
        memo: MemoWriteDto,
        updateMask: String,
    ): MemoDto

    suspend fun deleteMemo(name: String)

    // Attachments
    suspend fun createAttachment(attachment: AttachmentCreateDto): AttachmentDto

    suspend fun listMemoAttachments(memoName: String): ListAttachmentsResponseDto

    /** Replaces the memo's full attachment set. Empty list clears it. */

    suspend fun setMemoAttachments(
        memoName: String,
        body: SetMemoAttachmentsRequestDto,
    )

    suspend fun deleteAttachment(name: String)

    // Comments, reactions, relations, shares
    suspend fun listMemoComments(memoName: String, pageSize: Int = 200): ListMemoCommentsResponseDto

    suspend fun createMemoComment(memoName: String, comment: MemoWriteDto): MemoDto

    suspend fun listMemoReactions(memoName: String): ListReactionsResponseDto

    suspend fun upsertMemoReaction(memoName: String, body: UpsertReactionRequestDto): ReactionDto

    suspend fun deleteMemoReaction(reactionName: String)

    suspend fun setMemoRelations(memoName: String, body: SetMemoRelationsRequestDto)

    suspend fun createMemoShare(memoName: String, share: MemoShareDto): MemoShareDto

    suspend fun listMemoShares(memoName: String): ListMemoSharesResponseDto

    suspend fun deleteMemoShare(shareName: String)

    // Shortcuts
    suspend fun listShortcuts(userName: String): ListShortcutsResponseDto

    suspend fun createShortcut(userName: String, shortcut: ShortcutDto): ShortcutDto

    suspend fun updateShortcut(
        name: String,
        shortcut: ShortcutDto,
        updateMask: String = "title,filter",
    ): ShortcutDto

    suspend fun deleteShortcut(name: String)

    // User profile, stats, settings, tokens, webhooks, notifications
    suspend fun updateUser(
        name: String,
        user: UserWriteDto,
        updateMask: String,
    ): UserDto

    suspend fun getUserStats(userName: String): UserStatsDto

    suspend fun getUserSetting(settingName: String): UserSettingDto

    suspend fun updateUserSetting(
        settingName: String,
        setting: UserSettingDto,
        updateMask: String,
    ): UserSettingDto

    suspend fun listPersonalAccessTokens(userName: String): ListPersonalAccessTokensResponseDto

    suspend fun createPersonalAccessToken(
        userName: String,
        body: CreatePersonalAccessTokenRequestDto,
    ): CreatePersonalAccessTokenResponseDto

    suspend fun deletePersonalAccessToken(name: String)

    suspend fun listUserWebhooks(userName: String): ListUserWebhooksResponseDto

    suspend fun createUserWebhook(userName: String, webhook: UserWebhookDto): UserWebhookDto

    suspend fun deleteUserWebhook(name: String)

    suspend fun listUserNotifications(
        userName: String,
        pageSize: Int = 100,
        filter: String? = null,
    ): ListNotificationsResponseDto

    suspend fun updateUserNotification(
        name: String,
        body: NotificationWriteDto,
        updateMask: String = "status",
    ): UserNotificationDto

    suspend fun deleteUserNotification(name: String)

    // Admin
    suspend fun listUsers(pageSize: Int = 200, showDeleted: Boolean = false): ListUsersResponseDto

    suspend fun createUser(user: UserWriteDto): UserDto

    suspend fun deleteUser(name: String, force: Boolean = false)

    suspend fun getInstanceSetting(settingName: String): InstanceSettingDto

    suspend fun updateInstanceSetting(
        settingName: String,
        setting: InstanceSettingDto,
        updateMask: String,
    ): InstanceSettingDto

    suspend fun getInstanceStats(): InstanceStatsDto
}
