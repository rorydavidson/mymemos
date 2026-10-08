package com.keltruc.mymemos.web.sync

import com.keltruc.mymemos.data.text.Tags
import com.keltruc.mymemos.network.ApiException
import com.keltruc.mymemos.network.api.MemosApi
import com.keltruc.mymemos.network.dto.*
import kotlin.time.Clock
import kotlin.time.Instant

/** Just enough of a Memos server (tags derived as the server does), in memory, for the sync engine's tests. */
class FakeServer : MemosApi {
    val memos = LinkedHashMap<String, MemoDto>()
    private var seq = 0
    private var clock = 1_700_000_000_000L

    private fun tick(): String = Instant.fromEpochMilliseconds(clock.also { clock += 1000 }).toString()

    private fun missing(): Nothing = throw ApiException(404, 5, "not found")

    /** Another device's edit. */
    fun editElsewhere(name: String, content: String) {
        memos[name] = memos.getValue(name).copy(content = content, tags = Tags.extract(content), updateTime = tick())
    }

    override suspend fun createMemo(memo: MemoWriteDto, memoId: String?): MemoDto {
        val name = "memos/m${++seq}"
        val t = tick()
        val dto = MemoDto(
            name = name, creator = "users/1", content = memo.content.orEmpty(), visibility = memo.visibility ?: "PRIVATE",
            pinned = memo.pinned ?: false, createTime = memo.createTime ?: t, updateTime = t,
            tags = Tags.extract(memo.content.orEmpty()),
        )
        memos[name] = dto
        return dto
    }

    override suspend fun getMemo(name: String): MemoDto = memos[name] ?: missing()

    override suspend fun updateMemo(name: String, memo: MemoWriteDto, updateMask: String): MemoDto {
        val m = memos[name] ?: missing()
        val next = m.copy(
            content = memo.content ?: m.content,
            tags = Tags.extract(memo.content ?: m.content),
            pinned = memo.pinned ?: m.pinned,
            visibility = memo.visibility ?: m.visibility,
            state = memo.state ?: m.state,
            updateTime = tick(),
        )
        memos[name] = next
        return next
    }

    override suspend fun deleteMemo(name: String) {
        memos.remove(name) ?: missing()
    }

    override suspend fun listMemos(pageSize: Int, pageToken: String?, state: String?, orderBy: String?, filter: String?): ListMemosResponseDto =
        ListMemosResponseDto(memos.values.filter { state == null || it.state == state })

    override suspend fun listShortcuts(userName: String) = ListShortcutsResponseDto()
    override suspend fun getUser(name: String) = UserDto(name = name, username = "rory")
    override suspend fun setMemoRelations(memoName: String, body: SetMemoRelationsRequestDto) = Unit

    override suspend fun getInstanceProfile(): InstanceProfileDto = TODO()
    override suspend fun signIn(body: SignInRequestDto): SignInResponseDto = TODO()
    override suspend fun refreshToken(): RefreshTokenResponseDto = TODO()
    override suspend fun signOut() = TODO()
    override suspend fun getCurrentUser(): GetCurrentUserResponseDto = TODO()
    override suspend fun createAttachment(attachment: AttachmentCreateDto): AttachmentDto = TODO()
    override suspend fun listMemoAttachments(memoName: String): ListAttachmentsResponseDto = TODO()
    override suspend fun setMemoAttachments(memoName: String, body: SetMemoAttachmentsRequestDto) = TODO()
    override suspend fun deleteAttachment(name: String) = TODO()
    override suspend fun listMemoComments(memoName: String, pageSize: Int): ListMemoCommentsResponseDto = TODO()
    override suspend fun createMemoComment(memoName: String, comment: MemoWriteDto): MemoDto = TODO()
    override suspend fun listMemoReactions(memoName: String): ListReactionsResponseDto = TODO()
    override suspend fun upsertMemoReaction(memoName: String, body: UpsertReactionRequestDto): ReactionDto = TODO()
    override suspend fun deleteMemoReaction(reactionName: String) = TODO()
    override suspend fun createMemoShare(memoName: String, share: MemoShareDto): MemoShareDto = TODO()
    override suspend fun listMemoShares(memoName: String): ListMemoSharesResponseDto = TODO()
    override suspend fun deleteMemoShare(shareName: String) = TODO()
    override suspend fun createShortcut(userName: String, shortcut: ShortcutDto): ShortcutDto = TODO()
    override suspend fun updateShortcut(name: String, shortcut: ShortcutDto, updateMask: String): ShortcutDto = TODO()
    override suspend fun deleteShortcut(name: String) = TODO()
    override suspend fun updateUser(name: String, user: UserWriteDto, updateMask: String): UserDto = TODO()
    override suspend fun getUserStats(userName: String): UserStatsDto = TODO()
    override suspend fun getUserSetting(settingName: String): UserSettingDto = TODO()
    override suspend fun updateUserSetting(settingName: String, setting: UserSettingDto, updateMask: String): UserSettingDto = TODO()
    override suspend fun listPersonalAccessTokens(userName: String): ListPersonalAccessTokensResponseDto = TODO()
    override suspend fun createPersonalAccessToken(userName: String, body: CreatePersonalAccessTokenRequestDto): CreatePersonalAccessTokenResponseDto = TODO()
    override suspend fun deletePersonalAccessToken(name: String) = TODO()
    override suspend fun listUserWebhooks(userName: String): ListUserWebhooksResponseDto = TODO()
    override suspend fun createUserWebhook(userName: String, webhook: UserWebhookDto): UserWebhookDto = TODO()
    override suspend fun deleteUserWebhook(name: String) = TODO()
    override suspend fun listUserNotifications(userName: String, pageSize: Int, filter: String?): ListNotificationsResponseDto = TODO()
    override suspend fun updateUserNotification(name: String, body: NotificationWriteDto, updateMask: String): UserNotificationDto = TODO()
    override suspend fun deleteUserNotification(name: String) = TODO()
    override suspend fun listUsers(pageSize: Int, showDeleted: Boolean): ListUsersResponseDto = TODO()
    override suspend fun createUser(user: UserWriteDto): UserDto = TODO()
    override suspend fun deleteUser(name: String, force: Boolean) = TODO()
    override suspend fun getInstanceSetting(settingName: String): InstanceSettingDto = TODO()
    override suspend fun updateInstanceSetting(settingName: String, setting: InstanceSettingDto, updateMask: String): InstanceSettingDto = TODO()
    override suspend fun getInstanceStats(): InstanceStatsDto = TODO()
}
