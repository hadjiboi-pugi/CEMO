package com.example.cemo.data

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

object TranslatorManager {
    private var translator: Translator? = null
    private var currentTargetLang: String? = null
    private val modelManager = RemoteModelManager.getInstance()

    /**
     * Initializes or updates the translator for the current selected language.
     * Manages model lifecycle and downloads.
     */
    suspend fun getTranslator(targetLanguage: String): Translator {
        val targetCode = if (targetLanguage == "Tagalog") TranslateLanguage.TAGALOG else TranslateLanguage.ENGLISH
        
        // Return existing if language hasn't changed
        if (translator != null && currentTargetLang == targetCode) {
            return translator!!
        }

        // Close old translator to free resources
        translator?.close()
        translator = null

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetCode)
            .build()
        
        val newTranslator = Translation.getClient(options)
        
        // Use lenient conditions for model download to ensure it starts
        val conditions = DownloadConditions.Builder()
            // Removed .requireWifi() to allow download on mobile data if needed
            .build()
        
        try {
            Log.d("Translator", "Ensuring model for $targetCode is ready...")
            // check if model is downloaded
            val model = TranslateRemoteModel.Builder(targetCode).build()
            val isDownloaded = modelManager.isModelDownloaded(model).await()
            
            if (!isDownloaded) {
                Log.d("Translator", "Downloading model...")
                newTranslator.downloadModelIfNeeded(conditions).await()
            } else {
                // If it exists but failed previously, it might be corrupt. 
                // We'll try to download it again if it fails to load later.
                newTranslator.downloadModelIfNeeded(conditions).await()
            }
            Log.d("Translator", "Model ready state confirmed.")
        } catch (e: Exception) {
            Log.e("Translator", "Model preparation failed", e)
            // If download fails, we try to delete model and reset so next attempt might fix it
            try {
                val model = TranslateRemoteModel.Builder(targetCode).build()
                modelManager.deleteDownloadedModel(model).await()
            } catch (_: Exception) {}
        }

        translator = newTranslator
        currentTargetLang = targetCode
        return newTranslator
    }

    /**
     * Translates a single piece of text.
     */
    suspend fun translate(text: String, targetLanguage: String): String {
        if (targetLanguage == "English" || text.isBlank()) return text
        
        return try {
            val client = getTranslator(targetLanguage)
            // Wait for translation with a timeout or just await
            client.translate(text).await()
        } catch (e: Exception) {
            Log.e("Translator", "Translation failed for: $text. Error: ${e.message}")
            
            // If it's a model issue, we might want to know
            if (e.message?.contains("model", ignoreCase = true) == true) {
                Log.w("Translator", "Possible model issue detected. Target: $targetLanguage")
                // Don't reset currentTargetLang here to avoid infinite loops if download is impossible
            }
            text
        }
    }
}
