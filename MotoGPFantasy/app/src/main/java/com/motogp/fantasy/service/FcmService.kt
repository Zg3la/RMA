package com.motogp.fantasy.service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.motogp.fantasy.MainActivity
import com.motogp.fantasy.R

class FcmService : FirebaseMessagingService() {
    override fun onMessageReceived(msg: RemoteMessage) {
        val title = msg.notification?.title ?: msg.data["title"] ?: "MotoGP Fantasy"
        val body = msg.notification?.body ?: msg.data["body"] ?: ""
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(NotificationChannel("motogp", "MotoGP Fantasy", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP }, PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "motogp").setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(pi).build()
        mgr.notify(System.currentTimeMillis().toInt(), n)
    }
}
