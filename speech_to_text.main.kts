#! /usr/bin/env kotlin

@file:DependsOn("com.google.cloud:google-cloud-speech:1.24.11")

import com.google.api.gax.longrunning.OperationFuture
import com.google.cloud.speech.v1.*
import com.google.protobuf.ByteString
import java.nio.file.Files
import java.nio.file.Paths


syncRecognizeFile(args[0])

@Throws(Exception::class)
fun syncRecognizeFile(fileName: String?) {
    SpeechClient.create().let { speech ->
        val path = Paths.get(fileName)
        val data = Files.readAllBytes(path)
        val audioBytes: ByteString = ByteString.copyFrom(data)

        // Configure request with local raw PCM audio
        val config: RecognitionConfig = RecognitionConfig.newBuilder()
            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
            .setLanguageCode("fr-FR")
            .setAudioChannelCount(2)
            .setSampleRateHertz(44100)
            .build()
        val audio: RecognitionAudio = RecognitionAudio.newBuilder().setContent(audioBytes).build()

        // Use non-blocking call for getting file transcription
        // Use non-blocking call for getting file transcription
        val response: OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> =
            speech.longRunningRecognizeAsync(config, audio)

        while (!response.isDone()) {
            val metadata = response.metadata
            metadata.addListener({println("oij")}, {it.run()})
            println("Waiting for response... ${response.peekMetadata()} ${response.getMetadata()} ")
            Thread.sleep(10000)
        }

        val results = response.get().getResultsList()        // Use blocking call to get audio transcript
        for (result in results) {
            // There can be several alternative transcripts for a given chunk of speech. Just use the
            // first (most likely) one here.
            val alternative: SpeechRecognitionAlternative = result.alternativesList.get(0)
            System.out.printf("Transcription: %s%n", alternative.transcript)
        }
    }
}