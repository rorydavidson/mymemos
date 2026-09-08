package com.keltruc.mymemos.ui.admin

import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.AccountSettingsRepository
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.InstanceGeneral
import com.keltruc.mymemos.model.InstanceStats
import com.keltruc.mymemos.model.User
import com.keltruc.mymemos.ui.account.RemoteListViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val repo: AccountSettingsRepository,
) : RemoteListViewModel<List<User>>(accountRepository) {
    override suspend fun load(account: Account) = repo.users(account)
    fun create(username: String, password: String, role: String) = act { repo.createUser(it, username, password, role) }
    fun setArchived(user: User, archived: Boolean) = act { repo.setUserArchived(it, user, archived) }
    fun delete(user: User) = act { repo.deleteUser(it, user) }
}

@HiltViewModel
class AdminInstanceViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val repo: AccountSettingsRepository,
) : RemoteListViewModel<AdminInstanceViewModel.Data>(accountRepository) {
    data class Data(val general: InstanceGeneral, val stats: InstanceStats?)

    override suspend fun load(account: Account) = Data(repo.instanceGeneral(account), runCatching { repo.instanceStats(account) }.getOrNull())
    fun save(general: InstanceGeneral) = act { repo.updateInstanceGeneral(it, general) }
}
