package com.fahim.geminiApiComposeStarter.data

import android.util.Log
import com.fahim.geminiApiComposeStarter.data.local.ChatMessageDao
import com.fahim.geminiApiComposeStarter.data.local.ChatMessageEntity
import com.fahim.geminiApiComposeStarter.data.security.EncryptedKeyDataStore
import com.fahim.geminiApiComposeStarter.ui.chat.ChatMessage
import com.fahim.geminiApiComposeStarter.ui.chat.Participant
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private const val TAG = "GeminiRepository"
private const val DEFAULT_MODEL = "gemini-3.6-flash"

class GeminiRepositoryImpl(
    private val keyProvider: suspend () -> String,
    private val chatMessageDao: ChatMessageDao? = null,
    private val modelName: String = DEFAULT_MODEL,
) : GeminiRepository {

    constructor(
        keyDataStore: EncryptedKeyDataStore,
        chatMessageDao: ChatMessageDao? = null,
        modelName: String = DEFAULT_MODEL,
    ) : this(
        keyProvider = {
            keyDataStore.getDecryptedKey()
                ?: throw IllegalStateException("Gemini API key is not available or decrypted key is null")
        },
        chatMessageDao = chatMessageDao,
        modelName = modelName,
    )

    constructor(
        apiKey: String,
        chatMessageDao: ChatMessageDao? = null,
        modelName: String = DEFAULT_MODEL,
    ) : this(
        keyProvider = { apiKey },
        chatMessageDao = chatMessageDao,
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

    override fun getChatHistory(): Flow<List<ChatMessage>> {
        return chatMessageDao?.getAllMessages()?.map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    text = entity.text,
                    participant = if (entity.participant == "USER") Participant.USER else Participant.MODEL,
                    timestamp = entity.timestamp,
                )
            }
        } ?: flowOf(emptyList())
    }

    override suspend fun saveMessage(message: ChatMessage) {
        chatMessageDao?.insertMessage(
            ChatMessageEntity(
                id = message.id,
                text = message.text,
                participant = message.participant.name,
                timestamp = message.timestamp,
            )
        )
    }

    override suspend fun clearHistory() {
        chatMessageDao?.clearAllMessages()
    }
}
