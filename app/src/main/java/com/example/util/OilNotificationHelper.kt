package com.example.util

import android.Manifest
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
import com.example.MainActivity
import com.example.R
import com.example.data.Vehicle
import java.text.NumberFormat
import java.util.Locale

object OilNotificationHelper {

    private const val CHANNEL_ID = "doojicar_oil_alerts"
    private const val CHANNEL_NAME = "엔진 오일 교체 알림"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "차량 엔진 오일 교체 시기 알림"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyDueVehicles(
        context: Context,
        vehicles: List<Vehicle>,
        globalEnabled: Boolean,
        thresholdKm: Long
    ): Int {
        if (!globalEnabled || !hasPermission(context)) return 0
        ensureChannel(context)

        val dueVehicles = vehicles.filter { it.needsOilAttention(thresholdKm) }
        if (dueVehicles.isEmpty()) return 0

        val nf = NumberFormat.getNumberInstance(Locale.KOREA)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        dueVehicles.take(3).forEachIndexed { index, vehicle ->
            val rem = vehicle.remainingOilKm()
            val body = if (rem <= 0L) {
                "${vehicle.plateNumber} (${vehicle.modelName}) · ${nf.format(-rem)} km 초과"
            } else {
                "${vehicle.plateNumber} (${vehicle.modelName}) · ${nf.format(rem)} km 남음"
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("엔진 오일 교체 필요")
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(4000 + index, notification)
            } catch (_: SecurityException) {
                return 0
            }
        }
        return dueVehicles.size
    }

    fun sendSingleVehicleTestAlert(context: Context, vehicle: Vehicle): Boolean {
        if (!hasPermission(context)) return false
        ensureChannel(context)
        val nf = NumberFormat.getNumberInstance(Locale.KOREA)
        val rem = vehicle.remainingOilKm()
        val statusText = if (rem <= 0L) {
            "${nf.format(-rem)} km 초과"
        } else {
            "잔여 ${nf.format(rem)} km"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("오일 알림 · ${vehicle.plateNumber}")
            .setContentText("${vehicle.modelName} · $statusText")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(vehicle.id.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
