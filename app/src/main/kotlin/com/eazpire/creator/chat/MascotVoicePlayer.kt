package com.eazpire.creator.chat

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import com.eazpire.creator.locale.LocaleStore
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * Native mascot voice stack — parity with web `eazy-mascot-tab.js`:
 * pick DE/EN line → speech bubble text → asset audio → TTS fallback.
 * Honors `audio_enabled` / `audio_volume` from [EazySettingsStore].
 */
object MascotVoicePlayer {

    data class VoiceLine(val text: String, val assetPath: String?)

    private val lastKey = mutableMapOf<String, String>()
    private var mediaPlayer: MediaPlayer? = null
    private var sfxPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private val ttsReady = AtomicBoolean(false)

    /** Same fallback copy as web FALLBACK_VOICE_LINES (+ bundled asset filenames). */
    private val FALLBACK: Map<String, Map<String, List<VoiceLine>>> = mapOf(
        "pet" to mapOf(
            "de" to listOf(
                VoiceLine("Hey, Händchen weg — warte, doch nicht. Mehr davon.", "mascot-voice/eazy-voice-mascot-pet-de-01.mp3"),
                VoiceLine("Streichel-XP? Bestechung angenommen. Weiter kraulen.", "mascot-voice/eazy-voice-mascot-pet-de-02.mp3"),
                VoiceLine("Ahh. Genau da. Du hast den Cheat-Code gefunden.", "mascot-voice/eazy-voice-mascot-pet-de-03.mp3"),
                VoiceLine("Weiter so und du erbst meinen Sarkasmus. Herzlichen Glückwunsch.", "mascot-voice/eazy-voice-mascot-pet-de-04.mp3"),
            ),
            "en" to listOf(
                VoiceLine("Hands off — wait, no. Hands on. More of that.", "mascot-voice/eazy-voice-mascot-pet-en-01.mp3"),
                VoiceLine("Pet XP? Bribery accepted. Keep going.", "mascot-voice/eazy-voice-mascot-pet-en-02.mp3"),
                VoiceLine("Ahh. Right there. You found the cheat code.", "mascot-voice/eazy-voice-mascot-pet-en-03.mp3"),
                VoiceLine("Keep that up and you inherit my sarcasm. Congrats.", "mascot-voice/eazy-voice-mascot-pet-en-04.mp3"),
            ),
        ),
        "feed" to mapOf(
            "de" to listOf(
                VoiceLine("Cookie? Für mich? Ich adopte dich. Offiziell.", "mascot-voice/eazy-voice-mascot-feed-de-01.mp3"),
                VoiceLine("Zucker ist mein Benzin. Bitte nachfüllen.", "mascot-voice/eazy-voice-mascot-feed-de-02.mp3"),
                VoiceLine("Ein Keks für Eazy. Fairer Deal. Fast schon ein Raub.", "mascot-voice/eazy-voice-mascot-feed-de-03.mp3"),
                VoiceLine("Nom. Wär das ein Design, wärst du Level neunundneunzig.", "mascot-voice/eazy-voice-mascot-feed-de-04.mp3"),
            ),
            "en" to listOf(
                VoiceLine("Cookie? For me? I officially adopt you.", "mascot-voice/eazy-voice-mascot-feed-en-01.mp3"),
                VoiceLine("Sugar is my fuel. Please refill.", "mascot-voice/eazy-voice-mascot-feed-en-02.mp3"),
                VoiceLine("One cookie for Eazy. Fair trade. Almost a heist.", "mascot-voice/eazy-voice-mascot-feed-en-03.mp3"),
                VoiceLine("Nom. If that was a design, you'd be level ninety-nine.", "mascot-voice/eazy-voice-mascot-feed-en-04.mp3"),
            ),
        ),
        "play" to mapOf(
            "de" to listOf(
                VoiceLine("Game on. Verlier schön — ich brauch den Ego-Boost.", "mascot-voice/eazy-voice-mascot-play-de-01.mp3"),
                VoiceLine("Controller raus. Ich spiel unfair und charmant.", "mascot-voice/eazy-voice-mascot-play-de-02.mp3"),
                VoiceLine("Daily Game? Daily Chaos. Los.", "mascot-voice/eazy-voice-mascot-play-de-03.mp3"),
                VoiceLine("Gewinnen optional. Drama Pflicht.", "mascot-voice/eazy-voice-mascot-play-de-04.mp3"),
            ),
            "en" to listOf(
                VoiceLine("Game on. Lose beautifully — I need the ego boost.", "mascot-voice/eazy-voice-mascot-play-en-01.mp3"),
                VoiceLine("Controllers out. I play dirty and charming.", "mascot-voice/eazy-voice-mascot-play-en-02.mp3"),
                VoiceLine("Daily game? Daily chaos. Go.", "mascot-voice/eazy-voice-mascot-play-en-03.mp3"),
                VoiceLine("Winning optional. Drama mandatory.", "mascot-voice/eazy-voice-mascot-play-en-04.mp3"),
            ),
        ),
        "fart" to mapOf(
            "de" to listOf(
                VoiceLine("Das war ich nicht. Das war… die Atmosphäre.", "mascot-voice/eazy-voice-mascot-fart-de-01.mp3"),
                VoiceLine("Luftqualität: frech. Genau mein Genre.", "mascot-voice/eazy-voice-mascot-fart-de-02.mp3"),
                VoiceLine("Biowaffe freigeschaltet. Bitte, kein Applaus.", "mascot-voice/eazy-voice-mascot-fart-de-03.mp3"),
                VoiceLine("Wenn das Kunst ist, bin ich Picasso. Stinkender Picasso.", "mascot-voice/eazy-voice-mascot-fart-de-04.mp3"),
            ),
            "en" to listOf(
                VoiceLine("Wasn't me. That was… the atmosphere.", "mascot-voice/eazy-voice-mascot-fart-en-01.mp3"),
                VoiceLine("Air quality: spicy. Right up my alley.", "mascot-voice/eazy-voice-mascot-fart-en-02.mp3"),
                VoiceLine("Biological DLC unlocked. Please, no applause.", "mascot-voice/eazy-voice-mascot-fart-en-03.mp3"),
                VoiceLine("If that's art, I'm Picasso. Stinky Picasso.", "mascot-voice/eazy-voice-mascot-fart-en-04.mp3"),
            ),
        ),
    )

    private val FART_SFX = listOf(
        "mascot-sfx/eazy-sound-fart-classic.mp3",
        "mascot-sfx/eazy-sound-fart-quick.mp3",
        "mascot-sfx/eazy-sound-fart-small.mp3",
        "mascot-sfx/eazy-sound-fart-silly.mp3",
        "mascot-sfx/eazy-sound-fart-common.mp3",
        "mascot-sfx/eazy-sound-fart-wet.mp3",
        "mascot-sfx/eazy-sound-fart-long.mp3",
        "mascot-sfx/eazy-sound-fart-raspberry.mp3",
    )

    fun voiceLang(context: Context): String {
        val code = LocaleStore(context).getLanguageCodeSync().lowercase(Locale.ROOT).replace('_', '-')
        return if (code == "de" || code.startsWith("de-")) "de" else "en"
    }

    fun pickLine(context: Context, action: String): Pair<String, VoiceLine>? {
        val lang = voiceLang(context)
        val pool = FALLBACK[action]?.get(lang)
            ?: FALLBACK[action]?.get("en")
            ?: return null
        if (pool.isEmpty()) return null
        val key = "$action:$lang"
        var pick = pool[Random.nextInt(pool.size)]
        var attempts = 0
        while (attempts < 5 && pool.size > 1 && (pick.assetPath ?: pick.text) == lastKey[key]) {
            pick = pool[Random.nextInt(pool.size)]
            attempts++
        }
        lastKey[key] = pick.assetPath ?: pick.text
        return lang to pick
    }

    fun ensureTts(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady.set(status == TextToSpeech.SUCCESS)
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        try {
            sfxPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            sfxPlayer?.release()
        } catch (_: Exception) {
        }
        sfxPlayer = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    /**
     * @return spoken/bubble text, or null if nothing to show
     */
    fun playInteract(
        context: Context,
        action: String,
        audioEnabled: Boolean,
        audioVolume: Int,
    ): String? {
        val picked = pickLine(context, action) ?: return null
        val (lang, line) = picked
        if (!audioEnabled) return line.text

        ensureTts(context)
        val volume = (audioVolume.coerceIn(0, 100) / 100f)

        fun speakVoice() {
            stopVoiceOnly()
            val path = line.assetPath
            if (path.isNullOrBlank()) {
                speakTts(line.text, lang)
                return
            }
            val started = playAsset(context, path, volume) { ok ->
                if (!ok) speakTts(line.text, lang)
            }
            if (!started) speakTts(line.text, lang)
        }

        if (action == "fart") {
            stop()
            val sfx = FART_SFX.randomOrNull()
            if (sfx != null) {
                val sfxStarted = playAsset(context, sfx, volume * 0.55f) { speakVoice() }
                if (!sfxStarted) speakVoice()
            } else {
                speakVoice()
            }
        } else {
            speakVoice()
        }
        return line.text
    }

    private fun stopVoiceOnly() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    private fun playAsset(
        context: Context,
        assetPath: String,
        volume: Float,
        onDone: (ok: Boolean) -> Unit,
    ): Boolean {
        return try {
            val afd = context.assets.openFd(assetPath)
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
                setOnCompletionListener {
                    try {
                        it.release()
                    } catch (_: Exception) {
                    }
                    if (mediaPlayer === it) mediaPlayer = null
                    if (sfxPlayer === it) sfxPlayer = null
                    onDone(true)
                }
                setOnErrorListener { player, _, _ ->
                    try {
                        player.release()
                    } catch (_: Exception) {
                    }
                    if (mediaPlayer === player) mediaPlayer = null
                    if (sfxPlayer === player) sfxPlayer = null
                    onDone(false)
                    true
                }
                prepare()
                start()
            }
            if (assetPath.contains("mascot-sfx")) sfxPlayer = mp else mediaPlayer = mp
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun speakTts(text: String, lang: String) {
        val engine = tts ?: return
        if (!ttsReady.get() || text.isBlank()) return
        try {
            engine.language = if (lang == "de") Locale.GERMAN else Locale.US
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mascot-voice")
        } catch (_: Exception) {
        }
    }
}
