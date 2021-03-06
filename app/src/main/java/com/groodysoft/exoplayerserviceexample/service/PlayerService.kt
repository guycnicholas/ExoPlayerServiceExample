package com.groodysoft.exoplayerserviceexample.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.gson.reflect.TypeToken
import com.groodysoft.exoplayerserviceexample.MainApplication
import com.groodysoft.exoplayerserviceexample.R

private val PACKAGE = MainApplication.context.packageName

val ACTION_METADATA = "$PACKAGE.ACTION_METADATA"
val NOTIFICATION_ACTION = "$PACKAGE.NOTIFICATION_ACTION"
val SERVICE_ACTION_CONTENT_URL = "$PACKAGE.SERVICE_ACTION_CONTENT_URL"
val SERVICE_ACTION_CONTENT_URL_LIST = "$PACKAGE.SERVICE_ACTION_CONTENT_URL_LIST"
val SERVICE_ACTION_PLAY = "$PACKAGE.SERVICE_ACTION_PLAY"

val SERVICE_EXTRA_STRING = "$PACKAGE.SERVICE_EXTRA_STRING"

const val FOREGROUND_SERVICE_NOTIFICATION_ID = 101


class PlayerService : Service() {

    private val logtag: String = PlayerService::class.java.simpleName

    lateinit var player: SimpleExoPlayer

    private lateinit var playerNotificationManager: PlayerNotificationManager

    private val binder = MyLocalBinder()

    private val userAgent = "exoplayer-exoplayerserviceexample"

    override fun onCreate() {
        super.onCreate()

        val trackSelector = DefaultTrackSelector( /* context= */this, AdaptiveTrackSelection.Factory())

        player = SimpleExoPlayer.Builder( /* context= */this)
            .setTrackSelector(trackSelector)
            .build()

        player.addAnalyticsListener(
            MetadataListener(
                trackSelector
            )
        )
        registerReceiver(audioNoisyReceiver, noisyAudioIntentFilter)
        LocalBroadcastManager.getInstance(this).registerReceiver(metadataReceiver, metadataIntentFilter)

        playerNotificationManager = PlayerNotificationManager(this, getChannelId(),
            FOREGROUND_SERVICE_NOTIFICATION_ID,
            DescriptionAdapter
        )
        playerNotificationManager.setPlayer(player)

        // define notification behavior
        playerNotificationManager.setUseNavigationActions(true)
        playerNotificationManager.setFastForwardIncrementMs(0)
        playerNotificationManager.setRewindIncrementMs(0)
        playerNotificationManager.setUseStopAction(false)
        playerNotificationManager.setColorized(true)
        playerNotificationManager.setColor(ContextCompat.getColor(
            MainApplication.context,
            R.color.bkgd_notification
        ))
        playerNotificationManager.setUsePlayPauseActions(true)
        playerNotificationManager.setUseChronometer(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(audioNoisyReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(metadataReceiver)

        playerNotificationManager.setPlayer(null)
        player.release()
    }

    private fun buildMediaSource(url: String): MediaSource {
        val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(this, userAgent)
        val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)
        return mediaSourceFactory.createMediaSource(Uri.parse(url))
    }

    private fun buildMediaSource(urls: List<String>): MediaSource {
        // These factories are used to construct two media sources below
        val dataSourceFactory = DefaultDataSourceFactory(this, userAgent)
        val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)

        val ccms = ConcatenatingMediaSource()

        for (url in urls) {
            val mediaSource = mediaSourceFactory.createMediaSource(Uri.parse(url))
            ccms.addMediaSource(mediaSource)
        }

        return ccms
}

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        Log.i(logtag, intent.action!!)
        when (intent.action) {

            SERVICE_ACTION_CONTENT_URL -> {
                val url = intent.getStringExtra(SERVICE_EXTRA_STRING)
                player.prepare(buildMediaSource(url!!), false, false)
            }
            SERVICE_ACTION_CONTENT_URL_LIST -> {
                val type = object : TypeToken<List<String>>() {}.type
                val jsonUrlList = intent.getStringExtra(SERVICE_EXTRA_STRING)
                val urls: List<String> = MainApplication.gson.fromJson(jsonUrlList, type)
                player.prepare(buildMediaSource(urls), false, false)
            }
            SERVICE_ACTION_PLAY -> {
                player.playWhenReady = true
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class MyLocalBinder : Binder() {
        fun getService() : PlayerService {
            return this@PlayerService
        }
    }

    private fun getChannelId(): String {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel("my_service", "My Background Service")
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            ""
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Suppress("SameParameterValue")
    private fun createNotificationChannel(channelId: String, channelName: String): String{
        val chan = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    private val noisyAudioIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private val audioNoisyReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                if (player.isPlaying) {
                    player.playWhenReady = false
                }
            }
        }
    }

    private val metadataIntentFilter = IntentFilter(ACTION_METADATA)
    private val metadataReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_METADATA == intent.action) {
                playerNotificationManager.invalidate()
            }
        }
    }
}
