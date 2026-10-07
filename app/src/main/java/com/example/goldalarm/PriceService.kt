package com.example.goldalarm

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class PriceService : Service() {

    private var running = false
    private var player: MediaPlayer? = null
    private var last: Double? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_ALARM" -> { stopAlarm(); return START_STICKY }
            "STOP_SERVICE" -> {
                running = false
                stopAlarm()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        createChannels()
        ServiceCompat.startForeground(
            this, 1, monitorNote("در حال اتصال..."),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (!running) {
            running = true
            Thread { loop() }.start()
        }
        return START_STICKY
    }

    private fun loop() {
        while (running) {
            try {
                val price = fetchPrice()
                sendBroadcast(Intent("GOLD_PRICE").setPackage(packageName).putExtra("p", price))
                getSystemService(NotificationManager::class.java)
                    .notify(1, monitorNote("قیمت انس: $price"))
                check(price)
                last = price
            } catch (e: Exception) {
                // خطای شبکه: دور بعد دوباره تلاش می‌کنیم
            }
            Thread.sleep(10_000)
        }
    }

    // این تابع تنها جاییه که به تریدینگ‌ویو وصل می‌شه
    private fun fetchPrice(): Double {
        val url = URL("https://scanner.tradingview.com/symbol?symbol=OANDA%3AXAUUSD&fields=close&no_404=true")
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        val text = c.inputStream.bufferedReader().readText()
        return JSONObject(text).getDouble("close")
    }

    private fun check(price: Double) {
        val prefs = getSharedPreferences("gold", Context.MODE_PRIVATE)
        val targets = prefs.getString("targets", "")!!
            .split(",").filter { it.isNotBlank() }.map { it.toDouble() }.toMutableList()
        val prev = last
        val hit = targets.filter { t ->
            if (prev == null) abs(price - t) < 0.01
            else (prev < t && price >= t) || (prev > t && price <= t)
        }
        if (hit.isEmpty()) return
        targets.removeAll(hit.toSet())
        prefs.edit().putString("targets", targets.joinToString(",")).apply()
        sendBroadcast(Intent("GOLD_PRICE").setPackage(packageName).putExtra("p", price))
        startAlarm(price, hit.first())
    }

    private fun startAlarm(price: Double, target: Double) {
        stopAlarm()
        player = MediaPlayer().apply {
            setDataSource(this@PriceService, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
            isLooping = true
            prepare()
            start()
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, PriceService::class.java).setAction("STOP_ALARM"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, "alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("آلارم طلا")
            .setContentText("قیمت به $target رسید (الان: $price)")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, "خاموش کردن آلارم", stop)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(2, n)
    }

    private fun stopAlarm() {
        player?.run { if (isPlaying) stop(); release() }
        player = null
        getSystemService(NotificationManager::class.java).cancel(2)
    }

    private fun monitorNote(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 2, Intent(this, PriceService::class.java).setAction("STOP_SERVICE"),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "monitor")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("آلارم طلا فعاله")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "توقف", stop)
            .build()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("monitor", "پایش قیمت", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel("alarm", "آلارم قیمت", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onDestroy() {
        running = false
        stopAlarm()
        super.onDestroy()
    }
}
