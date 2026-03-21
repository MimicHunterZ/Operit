package com.ai.assistance.operit.integrations.http

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.application.ForegroundServiceCompat
import com.ai.assistance.operit.data.preferences.ExternalHttpApiPreferences
import com.ai.assistance.operit.util.AppLogger
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExternalChatHttpServiceState(
    val isRunning: Boolean = false,
    val port: Int? = null,
    val lastError: String? = null
)

class ExternalChatHttpService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var preferences: ExternalHttpApiPreferences
    private var server: ExternalChatHttpServer? = null
    private var currentPort: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        preferences = ExternalHttpApiPreferences.getInstance(applicationContext)
        serviceStateFlow.value = serviceStateFlow.value.copy(lastError = null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START,
            ACTION_REFRESH,
            null -> {
                ensureNotificationChannel()
                ForegroundServiceCompat.startForeground(
                    service = this,
                    notificationId = NOTIFICATION_ID,
                    notification = createNotification(currentPort ?: preferences.getPort()),
                    types = ForegroundServiceCompat.buildTypes(dataSync = true, specialUse = true)
                )
                startOrRestartServer()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        val stoppedPort = currentPort ?: preferences.getPort()
        stopServer()
        serviceScope.cancel()
        serviceStateFlow.value = serviceStateFlow.value.copy(
            isRunning = false,
            port = stoppedPort
        )
        super.onDestroy()
    }

    private fun startOrRestartServer() {
        val config = preferences.getConfigSync()
        if (!config.enabled) {
            AppLogger.i(TAG, "External HTTP API disabled in preferences, stopping service")
            serviceStateFlow.value = ExternalChatHttpServiceState(
                isRunning = false,
                port = config.port,
                lastError = null
            )
            stopSelf()
            return
        }
        if (!ExternalHttpApiPreferences.isValidPort(config.port)) {
            val message = "Invalid port: ${config.port}"
            AppLogger.w(TAG, message)
            serviceStateFlow.value = ExternalChatHttpServiceState(isRunning = false, port = config.port, lastError = message)
            stopSelf()
            return
        }
        if (server != null && currentPort == config.port) {
            serviceStateFlow.value = ExternalChatHttpServiceState(isRunning = true, port = config.port, lastError = null)
            return
        }

        stopServer()

        try {
            val newServer = ExternalChatHttpServer(applicationContext, preferences, serviceScope)
            newServer.startServer()
            server = newServer
            currentPort = config.port
            serviceStateFlow.value = ExternalChatHttpServiceState(isRunning = true, port = config.port, lastError = null)
            notifyForeground(config.port)
        } catch (e: IOException) {
            AppLogger.e(TAG, "Failed to start external HTTP server", e)
            serviceStateFlow.value = ExternalChatHttpServiceState(isRunning = false, port = config.port, lastError = e.message ?: "Failed to start server")
            stopSelf()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Unexpected error while starting external HTTP server", e)
            serviceStateFlow.value = ExternalChatHttpServiceState(isRunning = false, port = config.port, lastError = e.message ?: "Failed to start server")
            stopSelf()
        }
    }

    private fun stopServer() {
        runCatching {
            server?.stopServer()
        }.onFailure { error ->
            AppLogger.e(TAG, "Failed to stop external HTTP server", error)
        }
        server = null
        currentPort = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.external_http_chat_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.external_http_chat_notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(port: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(getString(R.string.external_http_chat_notification_title))
            .setContentText(getString(R.string.external_http_chat_notification_text, port))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(getLaunchIntent())
            .build()
    }

    private fun notifyForeground(port: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(port))
    }

    private fun getLaunchIntent(): PendingIntent {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
    }

    companion object {
        private const val TAG = "ExternalChatHttpSvc"
        private const val CHANNEL_ID = "external_http_chat_service"
        private const val NOTIFICATION_ID = 7114

        const val ACTION_START = "com.ai.assistance.operit.action.START_EXTERNAL_HTTP_CHAT_SERVICE"
        const val ACTION_REFRESH = "com.ai.assistance.operit.action.REFRESH_EXTERNAL_HTTP_CHAT_SERVICE"
        const val ACTION_STOP = "com.ai.assistance.operit.action.STOP_EXTERNAL_HTTP_CHAT_SERVICE"

        private val serviceStateFlow = MutableStateFlow(ExternalChatHttpServiceState())
        val serviceState = serviceStateFlow.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, ExternalChatHttpService::class.java).apply {
                action = ACTION_START
            }
            startServiceCompat(context, intent)
        }

        fun refresh(context: Context) {
            val intent = Intent(context, ExternalChatHttpService::class.java).apply {
                action = ACTION_REFRESH
            }
            startServiceCompat(context, intent)
        }

        fun stop(context: Context) {
            serviceStateFlow.value = ExternalChatHttpServiceState()
            context.stopService(Intent(context, ExternalChatHttpService::class.java))
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
