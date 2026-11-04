package com.tideandtea.owner

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tideandtea.owner.data.OwnerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives pushes sent by the notify-owners Supabase Edge Function (supabase/functions).
 * When the app is closed, Android shows the notification by itself; this service
 * handles the app-open case and keeps this phone's token up to date.
 */
class TideMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { OwnerRepository().registerPushToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification ?: return
        show(this, n.title ?: "Tide & Tea", n.body ?: "", message.data["type"], message.data["bookingId"])
    }

    companion object {
        const val CHANNEL = "requests"
        const val EXTRA_TYPE = "type"
        const val EXTRA_BOOKING = "bookingId"

        fun createChannel(context: Context) {
            val ch = NotificationChannel(CHANNEL, "Requests and messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New booking requests, payments and client messages"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        @SuppressLint("MissingPermission") // checked just below
        fun show(context: Context, title: String, body: String, type: String?, bookingId: String?) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TYPE, type)
                putExtra(EXTRA_BOOKING, bookingId)
            }
            val pi = PendingIntent.getActivity(
                context, (bookingId ?: title).hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF3A1D5C.toInt())
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            NotificationManagerCompat.from(context).notify((bookingId ?: title).hashCode(), notification)
        }
    }
}
