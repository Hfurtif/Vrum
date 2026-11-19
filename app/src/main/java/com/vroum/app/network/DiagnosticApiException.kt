package com.vroum.app.network

sealed class DiagnosticApiException(
    message: String,
    val httpCode: Int? = null,
    val apiDetails: String? = null
) : Exception(message) {

    fun formatTechnicalDetails(): String? {
        val parts = buildList {
            if (httpCode != null) add("Code HTTP : $httpCode")
            if (!apiDetails.isNullOrBlank()) add("Message API : ${apiDetails.trim()}")
        }
        return if (parts.isEmpty()) null else parts.joinToString("\n")
    }

    class NoInternet : DiagnosticApiException(
        message = "Pas de connexion internet. Vérifie ton réseau Wi-Fi ou mobile et réessaie.",
        apiDetails = "Aucune connexion réseau active détectée sur l'appareil."
    )

    class InvalidApiKey(
        provider: String,
        httpCode: Int? = null,
        apiDetails: String? = null
    ) : DiagnosticApiException(
        message = "Clé API $provider invalide ou non autorisée. Vérifie ta clé dans l'écran ⚙️.",
        httpCode = httpCode,
        apiDetails = apiDetails
    )

    class InsufficientCredit(
        provider: String,
        httpCode: Int? = null,
        apiDetails: String? = null
    ) : DiagnosticApiException(
        message = "Quota ou crédit API dépassé pour $provider. Réessaie plus tard ou vérifie ton compte $provider.",
        httpCode = httpCode,
        apiDetails = apiDetails
    )

    class ServerOverloaded(
        provider: String,
        httpCode: Int? = null,
        apiDetails: String? = null
    ) : DiagnosticApiException(
        message = "Le serveur $provider est temporairement surchargé (nouvel essai effectué). Réessaie dans quelques instants.",
        httpCode = httpCode,
        apiDetails = apiDetails
    )

    class UnreadablePhoto(
        details: String? = null
    ) : DiagnosticApiException(
        message = "Photo illisible ou format d'image non pris en charge. Essaie une autre photo ou continue sans photo.",
        apiDetails = details
    )

    class InvalidResponse(
        provider: String,
        details: String? = null,
        httpCode: Int? = null
    ) : DiagnosticApiException(
        message = "Réponse non conforme reçue de $provider. Réessaie ou continue sans photo.",
        httpCode = httpCode,
        apiDetails = details
    )

    class AnalysisTimeout(
        details: String? = null
    ) : DiagnosticApiException(
        message = "Délai d'attente dépassé lors de la communication avec l'IA. Vérifie ta connexion et réessaie.",
        apiDetails = details
    )

    class ProviderError(
        provider: String,
        details: String,
        httpCode: Int? = null,
        apiDetails: String? = null
    ) : DiagnosticApiException(
        message = "Erreur $provider : $details",
        httpCode = httpCode,
        apiDetails = apiDetails ?: details
    )
}
