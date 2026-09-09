package com.keltruc.mymemos.data.repository

import com.keltruc.mymemos.data.auth.ApiClientRegistry
import com.keltruc.mymemos.data.mapper.toModel
import com.keltruc.mymemos.data.mapper.toRfc3339
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.MemoShare
import com.keltruc.mymemos.network.ApiException
import com.keltruc.mymemos.network.dto.MemoShareDto
import io.ktor.client.plugins.ResponseException
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Public share links. Online only: a link that cannot reach the server is no use anyway. */
@Singleton
class ShareRepository @Inject constructor(
    private val registry: ApiClientRegistry,
    private val json: Json,
) {
    suspend fun list(account: Account, memoRemoteName: String): List<MemoShare> = wrap {
        registry.api(account.serverUrl, account.userResourceName).listMemoShares(memoRemoteName).memoShares
            .map { it.toModel(account.serverUrl) }
    }

    suspend fun create(account: Account, memoRemoteName: String, expiresAt: Instant?): MemoShare = wrap {
        registry.api(account.serverUrl, account.userResourceName)
            .createMemoShare(memoRemoteName, MemoShareDto(expireTime = expiresAt?.toRfc3339()))
            .toModel(account.serverUrl)
    }

    suspend fun delete(account: Account, share: MemoShare) = wrap {
        registry.api(account.serverUrl, account.userResourceName).deleteMemoShare(share.name)
    }

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: ResponseException) {
        throw ApiException.from(e, json)
    }
}
