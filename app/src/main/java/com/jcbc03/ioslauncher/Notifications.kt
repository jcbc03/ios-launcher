package com.jcbc03.ioslauncher

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Notif(val key: String, val pkg: String, val title: String, val text: String, val time: Long,
                 val intent: PendingIntent?, val clearable: Boolean)

object NotifStore {
    private val _list = MutableStateFlow<List<Notif>>(emptyList())
    val list: StateFlow<List<Notif>> = _list
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts
    @Volatile var service: LauncherNotificationService? = null

    fun update(sbns: Array<StatusBarNotification>?) {
        val l = sbns.orEmpty().filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }.map {
            val e = it.notification.extras
            Notif(it.key, it.packageName, e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
                e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "", it.postTime, it.notification.contentIntent, it.isClearable)
        }.sortedByDescending { it.time }
        _list.value = l
        _counts.value = l.filter { it.clearable }.groupingBy { it.pkg }.eachCount()
    }

    fun dismiss(n: Notif) { service?.cancelNotification(n.key) }
    fun clearAll() { service?.cancelAllNotifications() }

    fun hasAccess(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(ComponentName(ctx, LauncherNotificationService::class.java).flattenToString())
    }
}

class LauncherNotificationService : NotificationListenerService() {
    override fun onListenerConnected() { NotifStore.service = this; refresh() }
    override fun onListenerDisconnected() { NotifStore.service = null }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    private fun refresh() = NotifStore.update(try { activeNotifications } catch (_: Exception) { null })
}
