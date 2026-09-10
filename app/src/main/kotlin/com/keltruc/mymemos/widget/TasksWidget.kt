package com.keltruc.mymemos.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.keltruc.mymemos.MainActivity
import com.keltruc.mymemos.R
import com.keltruc.mymemos.data.text.TaskLine

/** Every unticked task across your memos. Tapping a box ticks it through the outbox. */
class TasksWidget : GlanceAppWidget() {

    data class Task(val memoLocalId: String, val lineIndex: Int, val text: String)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = WidgetEntryPoint.get(context)
        val account = entry.accountRepository().activeAccountOrNull()
        val tasks = account?.let { acc ->
            entry.memoRepository().memosWithOpenTasks(acc.id).flatMap { memo ->
                memo.content.lines().mapIndexedNotNull { i, line ->
                    taskLine.matchEntire(line)?.takeIf { it.groupValues[2] == " " }?.let { Task(memo.localId, i, it.groupValues[3]) }
                }
            }
        }.orEmpty()

        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(14.dp),
                ) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            context.getString(R.string.widget_tasks_title),
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Text(
                            tasks.size.toString(),
                            style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold),
                        )
                    }
                    Spacer(GlanceModifier.size(8.dp))
                    if (account == null) {
                        Text(context.getString(R.string.widget_sign_in), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                    } else if (tasks.isEmpty()) {
                        Text(context.getString(R.string.widget_tasks_empty), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                    } else {
                        LazyColumn {
                            items(tasks, itemId = { (it.memoLocalId + it.lineIndex).hashCode().toLong() }) { task ->
                                Row(
                                    GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_widget_checkbox),
                                        contentDescription = null,
                                        modifier = GlanceModifier.size(22.dp).clickable(
                                            actionRunCallback<ToggleTaskAction>(
                                                actionParametersOf(memoKey to task.memoLocalId, lineKey to task.lineIndex),
                                            ),
                                        ),
                                    )
                                    Spacer(GlanceModifier.width(10.dp))
                                    Text(
                                        task.text,
                                        maxLines = 2,
                                        style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
                                        modifier = GlanceModifier.defaultWeight().clickable(
                                            actionStartActivity(
                                                Intent(context, MainActivity::class.java)
                                                    .setAction(MainActivity.ACTION_OPEN_MEMO)
                                                    .putExtra(MainActivity.EXTRA_MEMO_LOCAL_ID, task.memoLocalId)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                                            ),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        val memoKey = ActionParameters.Key<String>("memo")
        val lineKey = ActionParameters.Key<Int>("line")
        private val taskLine = Regex("^(\\s*)[-*] \\[([ xX])] (.*)$")
        @Suppress("unused") private val keepColor = Color.Unspecified
        @Suppress("unused") private val keepProvider: ColorProvider? = null
    }
}

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val memoId = parameters[TasksWidget.memoKey] ?: return
        val line = parameters[TasksWidget.lineKey] ?: return
        val repo = WidgetEntryPoint.get(context).memoRepository()
        val memo = repo.observeMemoOnce(memoId) ?: return
        TaskLine.toggle(memo.content, line, true)?.let { repo.updateContent(memoId, it) }
        TasksWidget().updateAll(context)
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}
