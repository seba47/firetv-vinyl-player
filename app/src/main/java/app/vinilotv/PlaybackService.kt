package app.vinilotv

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Reproduce en segundo plano: la música sigue aunque se salga de la app, y la MediaSession hace
 * que anden los botones de play/pausa del control y "Alexa, pausa". También levanta el servidor
 * web para elegir música desde el celular.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var web: WebServer? = null

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        Crate.init(this)
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(YouTube.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val exo = ExoPlayer.Builder(this)
            // de a pedazos: algunas URLs de YouTube (cliente iOS, "para niños") rechazan pedidos abiertos
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(ChunkedDataSource.Factory(http)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        // Nunca se muestra video: si algún tema viene como video combinado (p. ej. "hechos para
        // niños"), solo se decodifica el audio.
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        Radio.attach(this, exo)

        // "Siguiente" desde la sesión (Alexa, notificación) lo resuelve la radio, aunque el
        // próximo tema todavía no esté cargado en el reproductor.
        val player = object : ForwardingPlayer(exo) {
            override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).build()
            override fun isCommandAvailable(command: Int) =
                command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM || super.isCommandAvailable(command)
            override fun hasNextMediaItem() = true
            override fun seekToNext() = Radio.next()
            override fun seekToNextMediaItem() = Radio.next()
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()

        web = WebServer.start(this)
        Radio.setWebUrl(web?.publicUrl())
        Log.i("ViniloTV", "Página para el celular: ${web?.publicUrl()}")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        web?.stop()
        Radio.detach()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
