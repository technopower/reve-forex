package com.reve.forex.notifications
import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.reve.forex.MainActivity
class ReveFirebaseMessagingService: FirebaseMessagingService(){
 companion object{const val CHANNEL="reve_forex_signals"}
 override fun onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Forex Signal Alerts",NotificationManager.IMPORTANCE_HIGH))}
 override fun onNewToken(token:String){/* Send token to authenticated backend. */}
 override fun onMessageReceived(m:RemoteMessage){
  val i=Intent(this,MainActivity::class.java)
  val p=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val n=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(m.data["title"]?:"REVE FOREX").setContentText(m.data["body"]?:"New signal").setContentIntent(p).setAutoCancel(true).build()
  NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(),n)
 }
}
