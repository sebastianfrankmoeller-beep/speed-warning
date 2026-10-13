package com.basti.speedwarning

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.SortedMap
import java.util.TreeMap

class SpeedOverlayService : Service(), LocationListener {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var borderGlowView: View? = null
    private lateinit var locationManager: LocationManager

    private var currentSpeedKmH: Int = 0
    private var speedLimitKmH: Int = 50
    private var warningOffset: Int = 1

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        setupBorderGlow()
        setupFloatingWidget()
        startGpsTracking()
    }

    private fun setupFloatingWidget() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_speed_widget, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 30
        params.y = 200

        windowManager.addView(overlayView, params)
    }

    private fun setupBorderGlow() {
        borderGlowView = View(this)
        borderGlowView?.setBackgroundColor(Color.TRANSPARENT)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(borderGlowView, params)
    }

    private fun startGpsTracking() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0f, this)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        currentSpeedKmH = (location.speed * 3.6).toInt()

        val isMapsInForeground = isAppInForeground("com.google.android.apps.maps")
        overlayView?.visibility = if (isMapsInForeground) View.GONE else View.VISIBLE

        overlayView?.findViewById<TextView>(R.id.txt_speed)?.text = "$currentSpeedKmH"

        if (currentSpeedKmH >= (speedLimitKmH + warningOffset)) {
            triggerRedBorderGlow(true)
        } else {
            triggerRedBorderGlow(false)
        }
    }

    private fun triggerRedBorderGlow(active: Boolean) {
        if (active) {
            borderGlowView?.setBackgroundResource(R.drawable.red_border_glow)
        } else {
            borderGlowView?.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun isAppInForeground(packageName: String): Boolean {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time)
        if (stats != null && stats.isNotEmpty()) {
            val mySortedMap: SortedMap<Long, android.app.usage.UsageStats> = TreeMap()
            for (usageStats in stats) {
                mySortedMap[java.lang.Long.valueOf(usageStats.lastTimeUsed)] = usageStats
            }
            if (!mySortedMap.isEmpty()) {
                val currentApp = mySortedMap[mySortedMap.lastKey()]?.packageName
                return currentApp == packageName
            }
        }
        return false
    }

    private fun startForegroundServiceNotification() {
        val channelId = "speed_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "GPS Speed Monitor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Speed Warning aktiv")
            .setContentText("GPS Überwachung läuft")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
