package com.keltruc.mymemos.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.keltruc.mymemos.MainActivity
import com.keltruc.mymemos.R

/** Pinned and latest memos, with a quick-capture button. */
class RecentWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = WidgetEntryPoint.get(context)
        val account = entry.accountRepository().activeAccountOrNull()
        val memos = account?.let { entry.memoRepository().recentForWidget(it.id, 12) }.orEmpty()

        provideContent {
            GlanceTheme {
                Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(14.dp)) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            context.getString(R.string.app_name),
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_add),
                            contentDescription = context.getString(R.string.new_memo),
                            modifier = GlanceModifier.size(28.dp).clickable(
                                actionStartActivity(
                                    Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_MEMO)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                                ),
                            ),
                        )
                    }
                    Spacer(GlanceModifier.size(8.dp))
                    if (account == null) {
                        Text(context.getString(R.string.widget_sign_in), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                    } else {
                        LazyColumn {
                            items(memos, itemId = { it.localId.hashCode().toLong() }) { memo ->
                                Column(
                                    GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(
                                        actionStartActivity(
                                            Intent(context, MainActivity::class.java)
                                                .setAction(MainActivity.ACTION_OPEN_MEMO)
                                                .putExtra(MainActivity.EXTRA_MEMO_LOCAL_ID, memo.localId)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                                        ),
                                    ),
                                ) {
                                    Text(
                                        (if (memo.pinned) "📌 " else "") + memo.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                                        maxLines = 2,
                                        style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class RecentWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentWidget()
}
