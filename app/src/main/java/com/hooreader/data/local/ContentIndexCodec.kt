package com.hooreader.data.local

import org.json.JSONArray
import org.json.JSONObject

internal object ContentIndexCodec {
    fun encode(index: BookContentIndex): String = JSONObject().apply {
        put("bookId", index.bookId)
        put("contentHash", index.contentHash)
        put("parserVersion", index.parserVersion)
        put("spoolVersion", index.spoolVersion)
        put("complete", index.complete)
        put("recordCount", index.recordCount)
        put("spoolBytes", index.spoolBytes)
        put("spoolHash", index.spoolHash)
        put(
            "chapters",
            JSONArray().apply {
                index.chapterDirectory.forEach {
                    put(JSONArray(listOf(it.chapterIndex, it.firstRecord, it.blockCount)))
                }
            }
        )
        put(
            "checkpoints",
            JSONArray().apply {
                index.checkpoints.forEach { put(JSONArray(listOf(it.recordIndex, it.byteOffset))) }
            }
        )
    }.toString()

    fun decode(text: String): BookContentIndex {
        val json = JSONObject(text)
        val chapters = json.getJSONArray("chapters")
        val checkpoints = json.getJSONArray("checkpoints")
        return BookContentIndex(
            bookId = json.getString("bookId"),
            contentHash = json.getString("contentHash"),
            parserVersion = json.getInt("parserVersion"),
            spoolVersion = json.getInt("spoolVersion"),
            chapterDirectory = List(chapters.length()) {
                val item = chapters.getJSONArray(it)
                ContentChapter(item.getInt(0), item.getInt(1), item.getInt(2))
            },
            checkpoints = List(checkpoints.length()) {
                val item = checkpoints.getJSONArray(it)
                ContentCheckpoint(item.getInt(0), item.getLong(1))
            },
            recordCount = json.getInt("recordCount"),
            spoolBytes = json.getLong("spoolBytes"),
            spoolHash = json.getString("spoolHash"),
            complete = json.getBoolean("complete"),
        )
    }
}
