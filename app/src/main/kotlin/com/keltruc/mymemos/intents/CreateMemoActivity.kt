package com.keltruc.mymemos.intents

import androidx.activity.ComponentActivity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.keltruc.mymemos.MainActivity
import com.keltruc.mymemos.R
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.model.Visibility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Automation entry point (Tasker, Shortcuts, adb). No UI.
 *
 *   am start -a com.keltruc.mymemos.action.CREATE_MEMO \
 *      --es content "Call the plumber #home" --es visibility PRIVATE --ez pinned false --ez open false
 *
 * With `open` true the editor opens prefilled instead of saving straight away.
 *
 * Callers need the `com.keltruc.mymemos.permission.CREATE_MEMO` permission (declared in the
 * manifest, granted by the user), and a silent save is never PUBLIC: anything world-visible
 * has to go through the editor so the user sees it first.
 */
@AndroidEntryPoint
class CreateMemoActivity : ComponentActivity() {
    @Inject lateinit var memoRepository: MemoRepository
    @Inject lateinit var accountRepository: AccountRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = intent.getStringExtra(EXTRA_CONTENT).orEmpty()
        val open = intent.getBooleanExtra(EXTRA_OPEN, false)
        if (open || content.isBlank()) {
            startActivity(
                Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_MEMO)
                    .putExtra(MainActivity.EXTRA_TEXT, content)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            finish()
            return
        }
        val visibility = runCatching { Visibility.valueOf(intent.getStringExtra(EXTRA_VISIBILITY).orEmpty()) }.getOrDefault(Visibility.PRIVATE)
            .let { if (it == Visibility.PUBLIC) Visibility.PROTECTED else it }
        val pinned = intent.getBooleanExtra(EXTRA_PINNED, false)
        val app = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val account = accountRepository.activeAccountOrNull()
            if (account == null) {
                launch(Dispatchers.Main) { Toast.makeText(app, R.string.widget_sign_in, Toast.LENGTH_SHORT).show() }
                return@launch
            }
            memoRepository.create(account.id, content, visibility, pinned)
            launch(Dispatchers.Main) { Toast.makeText(app, R.string.offline_saved, Toast.LENGTH_SHORT).show() }
        }
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        const val ACTION = "com.keltruc.mymemos.action.CREATE_MEMO"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_VISIBILITY = "visibility"
        const val EXTRA_PINNED = "pinned"
        const val EXTRA_OPEN = "open"
    }
}
