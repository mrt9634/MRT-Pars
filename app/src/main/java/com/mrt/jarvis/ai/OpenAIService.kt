package com.mrt.jarvis.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal client (no extra libraries). Only called for AI_GENERAL when online. */
class OpenAIService(private val apiKey: String, private val model: String) {
    suspend fun ask(system: String, user: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 8000; c.readTimeout = 30000
            c.setRequestProperty("Authorization", "Bearer $apiKey")
            c.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("model", model).put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        }
    }
}
