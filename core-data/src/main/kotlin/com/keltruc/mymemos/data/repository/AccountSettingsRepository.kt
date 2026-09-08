package com.keltruc.mymemos.data.repository

import com.keltruc.mymemos.data.auth.ApiClientRegistry
import com.keltruc.mymemos.data.mapper.parseInstant
import com.keltruc.mymemos.data.mapper.toModel
import com.keltruc.mymemos.database.dao.AccountDao
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.InstanceGeneral
import com.keltruc.mymemos.model.InstanceStats
import com.keltruc.mymemos.model.Notification
import com.keltruc.mymemos.model.PersonalAccessToken
import com.keltruc.mymemos.model.User
import com.keltruc.mymemos.model.UserPreferences
import com.keltruc.mymemos.model.UserStats
import com.keltruc.mymemos.model.Visibility
import com.keltruc.mymemos.model.Webhook
import com.keltruc.mymemos.network.ApiException
import com.keltruc.mymemos.network.api.MemosApi
import com.keltruc.mymemos.network.dto.CreatePersonalAccessTokenRequestDto
import com.keltruc.mymemos.network.dto.CustomProfileDto
import com.keltruc.mymemos.network.dto.InstanceGeneralSettingDto
import com.keltruc.mymemos.network.dto.InstanceSettingDto
import com.keltruc.mymemos.network.dto.NotificationWriteDto
import com.keltruc.mymemos.network.dto.UserGeneralSettingDto
import com.keltruc.mymemos.network.dto.TagsSettingDto
import com.keltruc.mymemos.network.dto.TagMetadataDto
import com.keltruc.mymemos.network.dto.ColorDto
import com.keltruc.mymemos.model.NoteColour
import com.keltruc.mymemos.network.dto.UserSettingDto
import com.keltruc.mymemos.network.dto.UserWebhookDto
import com.keltruc.mymemos.network.dto.UserWriteDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything about the account and the instance that is not a memo. All of it is
 * online-only: these screens are read on demand and edits go straight to the server.
 */
@Singleton
class AccountSettingsRepository @Inject constructor(
    private val registry: ApiClientRegistry,
    private val accountDao: AccountDao,
    private val json: Json,
) {
    private val _unreadNotifications = MutableStateFlow(0)
    val unreadNotifications: StateFlow<Int> = _unreadNotifications

    private fun api(account: Account): MemosApi = registry.api(account.serverUrl, account.userResourceName)

    // ---- profile ---------------------------------------------------------------------

    suspend fun profile(account: Account): User = wrap { api(account).getUser(account.userResourceName).toModel() }

    suspend fun updateProfile(account: Account, displayName: String, description: String, email: String): User = wrap {
        val updated = api(account).updateUser(
            account.userResourceName,
            UserWriteDto(name = account.userResourceName, displayName = displayName, description = description, email = email),
            "display_name,description,email",
        )
        accountDao.getById(account.id)?.let { accountDao.update(it.copy(displayName = updated.displayName.ifEmpty { updated.username }, avatarUrl = updated.avatarUrl)) }
        updated.toModel()
    }

    suspend fun changePassword(account: Account, newPassword: String) = wrap {
        api(account).updateUser(account.userResourceName, UserWriteDto(name = account.userResourceName, password = newPassword), "password")
        Unit
    }

    // ---- server-side preferences -----------------------------------------------------

    suspend fun preferences(account: Account): UserPreferences = wrap {
        val s = api(account).getUserSetting("${account.userResourceName}/settings/GENERAL").generalSetting
        UserPreferences(
            locale = s?.locale.orEmpty(),
            defaultVisibility = runCatching { Visibility.valueOf(s?.memoVisibility.orEmpty()) }.getOrDefault(Visibility.PRIVATE),
        )
    }

    suspend fun setDefaultVisibility(account: Account, visibility: Visibility) = wrap {
        val name = "${account.userResourceName}/settings/GENERAL"
        api(account).updateUserSetting(
            name,
            UserSettingDto(name = name, generalSetting = UserGeneralSettingDto(memoVisibility = visibility.name)),
            "general_setting.memo_visibility",
        )
        Unit
    }

    /** Sets or clears the server's background colour for a tag (the web UI paints it). */
    suspend fun setTagColour(account: Account, tag: String, colour: NoteColour?) = wrap {
        val name = "${account.userResourceName}/settings/TAGS"
        val current = runCatching { api(account).getUserSetting(name).tagsSetting?.tags }.getOrNull().orEmpty().toMutableMap()
        if (colour == null) current.remove(tag) else {
            val hex = colour.hex
            current[tag] = TagMetadataDto(ColorDto(((hex shr 16) and 0xFF) / 255f, ((hex shr 8) and 0xFF) / 255f, (hex and 0xFF) / 255f))
        }
        api(account).updateUserSetting(name, UserSettingDto(name = name, tagsSetting = TagsSettingDto(current)), "tags")
        Unit
    }

    // ---- stats -----------------------------------------------------------------------

    suspend fun stats(account: Account): UserStats = wrap {
        val dto = api(account).getUserStats(account.userResourceName)
        UserStats(
            totalMemos = dto.totalMemoCount,
            links = dto.memoTypeStats?.linkCount ?: 0,
            code = dto.memoTypeStats?.codeCount ?: 0,
            todos = dto.memoTypeStats?.todoCount ?: 0,
            undone = dto.memoTypeStats?.undoCount ?: 0,
            tagCounts = dto.tagCount,
            createdTimes = dto.memoCreatedTimestamps.map { parseInstant(it) },
        )
    }

    // ---- access tokens ---------------------------------------------------------------

    suspend fun tokens(account: Account): List<PersonalAccessToken> = wrap {
        api(account).listPersonalAccessTokens(account.userResourceName).personalAccessTokens.map {
            PersonalAccessToken(it.name, it.description, parseInstant(it.createdAt), it.expiresAt?.let(::parseInstant), it.lastUsedAt?.let(::parseInstant))
        }
    }

    /** Returns the raw token; the server never shows it again. */
    suspend fun createToken(account: Account, description: String, expiresInDays: Int?): String = wrap {
        api(account).createPersonalAccessToken(account.userResourceName, CreatePersonalAccessTokenRequestDto(description, expiresInDays)).token
    }

    suspend fun deleteToken(account: Account, token: PersonalAccessToken) = wrap { api(account).deletePersonalAccessToken(token.name) }

    /** The token this app signed in with, so the list can warn before revoking it. */
    suspend fun ownTokenName(account: Account): String? = registry.tokenStore(account.serverUrl, account.userResourceName).mintedTokenName()

    // ---- webhooks --------------------------------------------------------------------

    suspend fun webhooks(account: Account): List<Webhook> = wrap {
        api(account).listUserWebhooks(account.userResourceName).webhooks.map { Webhook(it.name, it.url, it.displayName, parseInstant(it.createTime)) }
    }

    suspend fun createWebhook(account: Account, displayName: String, url: String): Webhook = wrap {
        api(account).createUserWebhook(account.userResourceName, UserWebhookDto(url = url, displayName = displayName))
            .let { Webhook(it.name, it.url, it.displayName, parseInstant(it.createTime)) }
    }

    suspend fun deleteWebhook(account: Account, webhook: Webhook) = wrap { api(account).deleteUserWebhook(webhook.name) }

    // ---- notifications ---------------------------------------------------------------

    suspend fun notifications(account: Account): List<Notification> = wrap {
        val list = api(account).listUserNotifications(account.userResourceName).notifications.map { n ->
            val payload = n.memoComment ?: n.memoMention
            Notification(
                name = n.name,
                senderUsername = n.senderUser?.username ?: n.sender.substringAfterLast('/'),
                unread = n.status == "UNREAD",
                createTime = parseInstant(n.createTime),
                type = n.type,
                memoRemoteName = payload?.relatedMemo?.ifEmpty { payload.memo }.orEmpty(),
                memoSnippet = payload?.memoSnippet.orEmpty(),
                relatedSnippet = payload?.relatedMemoSnippet.orEmpty(),
            )
        }
        _unreadNotifications.value = list.count { it.unread }
        list
    }

    suspend fun refreshUnreadCount(account: Account) {
        runCatching { notifications(account) }
    }

    suspend fun markRead(account: Account, notification: Notification) = wrap {
        api(account).updateUserNotification(notification.name, NotificationWriteDto(notification.name, "ARCHIVED"))
        _unreadNotifications.value = (_unreadNotifications.value - 1).coerceAtLeast(0)
    }

    suspend fun deleteNotification(account: Account, notification: Notification) = wrap {
        api(account).deleteUserNotification(notification.name)
        if (notification.unread) _unreadNotifications.value = (_unreadNotifications.value - 1).coerceAtLeast(0)
    }

    // ---- admin -----------------------------------------------------------------------

    suspend fun users(account: Account): List<User> = wrap { api(account).listUsers().users.map { it.toModel() } }

    suspend fun createUser(account: Account, username: String, password: String, role: String): User = wrap {
        api(account).createUser(UserWriteDto(username = username, password = password, role = role)).toModel()
    }

    suspend fun setUserArchived(account: Account, user: User, archived: Boolean): User = wrap {
        api(account).updateUser(user.name, UserWriteDto(name = user.name, state = if (archived) "ARCHIVED" else "NORMAL"), "state").toModel()
    }

    suspend fun deleteUser(account: Account, user: User) = wrap { api(account).deleteUser(user.name) }

    suspend fun instanceGeneral(account: Account): InstanceGeneral = wrap {
        val g = api(account).getInstanceSetting("instance/settings/GENERAL").generalSetting ?: InstanceGeneralSettingDto()
        InstanceGeneral(
            title = g.customProfile?.title.orEmpty(),
            description = g.customProfile?.description.orEmpty(),
            disallowRegistration = g.disallowUserRegistration,
            disallowPasswordAuth = g.disallowPasswordAuth,
            disallowChangeUsername = g.disallowChangeUsername,
            disallowChangeNickname = g.disallowChangeNickname,
            weekStartDayOffset = g.weekStartDayOffset,
        )
    }

    suspend fun updateInstanceGeneral(account: Account, general: InstanceGeneral) = wrap {
        val current = api(account).getInstanceSetting("instance/settings/GENERAL").generalSetting ?: InstanceGeneralSettingDto()
        api(account).updateInstanceSetting(
            "instance/settings/GENERAL",
            InstanceSettingDto(
                name = "instance/settings/GENERAL",
                generalSetting = current.copy(
                    disallowUserRegistration = general.disallowRegistration,
                    disallowPasswordAuth = general.disallowPasswordAuth,
                    disallowChangeUsername = general.disallowChangeUsername,
                    disallowChangeNickname = general.disallowChangeNickname,
                    weekStartDayOffset = general.weekStartDayOffset,
                    customProfile = CustomProfileDto(
                        title = general.title,
                        description = general.description,
                        logoUrl = current.customProfile?.logoUrl.orEmpty(),
                    ),
                ),
            ),
            "general_setting",
        )
        Unit
    }

    suspend fun instanceStats(account: Account): InstanceStats = wrap {
        val s = api(account).getInstanceStats()
        InstanceStats(s.database?.driver.orEmpty(), s.database?.sizeBytes ?: 0, s.localStorageBytes)
    }

    private inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: HttpException) {
        throw ApiException.from(e, json)
    }
}
