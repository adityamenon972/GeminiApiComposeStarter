package com.fahim.geminiApiComposeStarter.data

import android.util.Log
import com.fahim.geminiApiComposeStarter.data.security.EncryptedKeyDataStore
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.CancellationException

private const val TAG = "GeminiRepository"
private const val DEFAULT_MODEL = "gemini-3.6-flash"

class GeminiRepositoryImpl(
    private val keyProvider: suspend () -> String,
    private val modelName: String = DEFAULT_MODEL,
) : GeminiRepository {

    constructor(
        keyDataStore: EncryptedKeyDataStore,
        modelName: String = DEFAULT_MODEL,
    ) : this(
        keyProvider = {
            keyDataStore.getDecryptedKey()
                ?: throw IllegalStateException("Gemini API key is not available or decrypted key is null")
        },
        modelName = modelName,
    )

    constructor(
        apiKey: String,
        modelName: String = DEFAULT_MODEL,
    ) : this(
        keyProvider = { apiKey },
        modelName = modelName,
    )

    @Volatile
    private var generativeModel: GenerativeModel? = null

    private suspend fun getOrCreateModel(): GenerativeModel {
        generativeModel?.let { return it }
        val key = keyProvider()
        return GenerativeModel(modelName = modelName, apiKey = key).also {
            generativeModel = it
        }
    }

    override suspend fun generateText(prompt: String): Result<String> = try {
        val model = getOrCreateModel()
        val response = model.generateContent(prompt)
        val text = response.text?.takeIf { it.isNotBlank() }
        if (text != null) {
            Result.success(text)
        } else {
            Result.failure(IllegalStateException("Empty response from Gemini"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "generateContent failed", e)
        Result.failure(e)
    }
}
