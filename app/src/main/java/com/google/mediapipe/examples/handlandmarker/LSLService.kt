package com.google.mediapipe.examples.handlandmarker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import edu.ucsd.sccn.LSL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LSLService : HandLandmarkerHelper.LandmarkerListener, Service() {
    private val binder = LocalBinder()
    lateinit var mStreamOutlets: Map<String, LSL.StreamOutlet>
    lateinit var wakeLock: WakeLock
    override fun onBind(arg0: Intent?): IBinder? {
        Log.i(TAG, "Service onBind")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, javaClass.canonicalName
        )
        Log.e(TAG, "onCreate")
        wakeLock.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG, "onStartCommand")
        if (!::mStreamOutlets.isInitialized) {
            Log.e("HandLandMarkerHelper", "Setting up streams")
            mStreamOutlets = mapOf(
                "Right" to LSL.StreamOutlet(
                    LSL.StreamInfo(
                        "RightHand " + Build.MODEL,
                        "other",
                        21 * 3,
                        LSL.IRREGULAR_RATE,
                        LSL.ChannelFormat.float32,
                        Build.FINGERPRINT
                    )
                ), "Left" to LSL.StreamOutlet(
                    LSL.StreamInfo(
                        "LeftHand " + Build.MODEL,
                        "other",
                        21 * 3,
                        LSL.IRREGULAR_RATE,
                        LSL.ChannelFormat.float32,
                        Build.FINGERPRINT
                    )
                )
            )
        }
        val NOTIFICATION_CHANNEL_ID = "com.google.mediapipe.examples.handlandmarker"
        val channelName = "MP Hand Landmarker Background Service"
        val chan = NotificationChannel(
            NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT
        )
        chan.lightColor = Color.GREEN
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)!!
        manager!!.createNotificationChannel(chan)
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        val notification =
            notificationBuilder.setOngoing(true).setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("MP Hand Landmarker is running in background!")
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .setCategory(Notification.CATEGORY_SERVICE).build()
        val information_id =
            4711 // this must be unique and not 0, otherwise it does not have a meaning

        startForeground(information_id, notification, FOREGROUND_SERVICE_TYPE_CAMERA or FOREGROUND_SERVICE_TYPE_DATA_SYNC or FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        Toast.makeText(this, "Closing LSL!", Toast.LENGTH_SHORT).show()
        wakeLock.release()
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "HandLandmarker encountered error $error $errorCode")
    }

    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        var result = resultBundle.results[0]
        for (idx in result.handednesses().indices) {
            var samples = mutableListOf<Float>()
            result.worldLandmarks().get(idx).forEach {
                samples.add(it.x())
                samples.add(it.y())
                samples.add(it.z())
            }
            mStreamOutlets.get(result.handednesses().get(idx).get(0).categoryName())?.push_sample(
                samples.toFloatArray(),
                LSL.local_clock() - resultBundle.inferenceTime.toDouble() / 1000
            )
        }
    }

    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods.
        fun getService(): LSLService = this@LSLService
    }

    companion object {
        const val TAG = "LSLService"
    }
}
