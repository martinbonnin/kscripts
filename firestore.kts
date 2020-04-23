#!/usr/bin/env kscript

@file:MavenRepository("google", "https://dl.google.com/dl/android/maven2/")
@file:DependsOn("com.google.firebase:firebase-admin:6.12.2")
@file:DependsOn("com.squareup.moshi:moshi:1.9.2")
@file:DependsOn("com.opencsv:opencsv:4.1")
//@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.3.5")

import com.google.auth.oauth2.GoogleCredentials;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient
import com.opencsv.CSVWriter
import com.squareup.moshi.Moshi
import okio.Okio
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter

val serviceAccount = FileInputStream("serviceAccount.json");
val credentials = GoogleCredentials.fromStream(serviceAccount);
val options = FirebaseOptions.Builder()
    .setCredentials(credentials)
    .build();
FirebaseApp.initializeApp(options);

val db = FirestoreClient.getFirestore();

val moshi = Moshi.Builder().build()

val adapter = moshi.adapter(Any::class.java)

val sessionsJson = File("../android-makers-2020/data/database/sessions.json")
val votesJson = File("votes.json")
val voteItemsJson = File("voteItems.json")

fun downloadVotes() {
    val allVotes = db.collection("projects/mMHR63ARZQpPidFQISyc/userVotes").listDocuments().map {
        it.get().get()
    }.mapNotNull {
        if (it.get("status") != "active") {
            null
        } else {
            mapOf(
                "talkId" to it.get("talkId").toString(),
                "voteItemId" to it.get("voteItemId").toString()
            )
        }
    }
    votesJson.writeText(adapter.toJson(allVotes))
}

fun downloadVoteItems() {
    val documentSnapshot = db.document("projects/mMHR63ARZQpPidFQISyc").get().get()
    val allVoteItems = documentSnapshot
        .get("voteItems")
        ?.cast<List<Any>>()
        ?.map {
            mapOf(
                "id" to it.getString("id"),
                "name" to it.getString("name")
            )
        }

    voteItemsJson.writeText(adapter.toJson(allVoteItems))
}

fun merge() {
    val sessions = adapter.fromJson(Okio.buffer(Okio.source(sessionsJson)))!!.cast<Map<String, Any>>()
    val voteItems = adapter.fromJson(Okio.buffer(Okio.source(voteItemsJson)))!!.cast<List<Any>>()
    val votes = adapter.fromJson(Okio.buffer(Okio.source(votesJson)))!!.cast<List<Any>>()

    CSVWriter(FileWriter("allVotes.csv")).apply {

        votes.mapNotNull {vote ->
            val talkId = vote.getString("talkId")
            val voteItemId = vote.getString("voteItemId")

            val talkName = sessions.get(talkId)?.getString("title")
            if (talkName == null) {
                println("no talk found for $talkId")
                return@mapNotNull null
            }
            val voteItemName = voteItems.firstOrNull { it.getString("id") == voteItemId }?.getString("name")
            if (voteItemName == null) {
                println("no voteItem found for $voteItemId")
                return@mapNotNull null
            }
            arrayOf(talkName, voteItemName)
        }.sortedBy {
            it.get(0) + it.get(1)
        }.forEach {
            writeNext(it)
        }

        close()
    }

}

merge()

fun Any.getAny(key: String): Any? {
    return (this as Map<String, Any>).get(key)
}

fun Any.getObject(key: String): Map<String, Any>? {
    return getAny(key) as Map<String, Any>?
}

fun Any.getList(key: String): List<Any>? {
    return getAny(key) as List<Any>?
}

fun Any.getString(key: String): String? {
    return getAny(key) as String?
}

fun <T> Any.cast(): T {
    return this as T
}