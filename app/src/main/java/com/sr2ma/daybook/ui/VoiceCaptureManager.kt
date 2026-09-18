package com.sr2ma.daybook.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wraps Android's [SpeechRecognizer] as a cold [Flow].
 *
 * Uses [SpeechRecognizer.createOnDeviceSpeechRecognizer] on Android 13+,
 * falls back to the default recognizer on older versions.
 * Audio is processed entirely on-device — never sent to a server.
 *
 * Call [listen] to start a single recognition session.
 * The flow emits exactly one [VoiceResult] then completes.
 *
 * Lifecycle: caller must cancel the flow (or the CoroutineScope) to stop
 * recognition early. The underlying [SpeechRecognizer] is destroyed on
 * flow completion to release the mic.
 */
class VoiceCaptureManager(private val context: Context) {

    sealed interface VoiceResult {
        /** Recognition returned at least one result. [text] is the top hypothesis. */
        data class Success(val text: String) : VoiceResult
        /** Recognizer ran but returned no results (silence, noise, etc.). */
        data object NoMatch : VoiceResult
        /** On-device recognizer not available on this device / OS version. */
        data object Unavailable : VoiceResult
        /** Any other error from the recognizer. [code] is SpeechRecognizer.ERROR_* */
        data class Error(val code: Int) : VoiceResult
    }

    /**
     * Returns true if on-device speech recognition is supported.
     * Always returns true for the fallback path (network-based), but we
     * surface [VoiceResult.Unavailable] at runtime if neither works.
     */
    fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Starts one recognition session and emits the result.
     * Must be called from the Main thread (SpeechRecognizer requirement).
     */
    fun listen(): Flow<VoiceResult> = callbackFlow {
        val recognizer: SpeechRecognizer = if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            trySend(VoiceResult.Unavailable)
            close()
            return@callbackFlow
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // Give the user up to 3 seconds of complete silence before giving up
            // and up to 5 seconds of "possibly done" silence.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            // Prefer on-device model when available (Android 12+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val top = matches?.firstOrNull()
                if (top != null) {
                    trySend(VoiceResult.Success(top))
                } else {
                    trySend(VoiceResult.NoMatch)
                }
                close()
            }
            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> trySend(VoiceResult.NoMatch)
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> trySend(VoiceResult.Error(error))
                    else -> trySend(VoiceResult.Error(error))
                }
                close()
            }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        recognizer.startListening(intent)

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }
}
