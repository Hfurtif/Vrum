package com.example.network

sealed class DiagnosticApiException(message: String) : Exception(message) {
    class NoInternet : DiagnosticApiException(
        "Pas de connexion internet. Vérifie ton réseau Wi-Fi ou mobile et réessaie."
    )

    class InvalidApiKey(provider: String) : DiagnosticApiException(
        "Clé API $provider invalide ou expirée. Vérifie ta clé dans l'écran ⚙️."
    )

    class InsufficientCredit(provider: String) : DiagnosticApiException(
        "Crédit ou quota API épuisé pour $provider. Vérifie ton solde sur le portail $provider."
    )

    class AnalysisTimeout : DiagnosticApiException(
        "L'analyse a dépassé la limite de 2 minutes. Réessaie avec une bonne connexion."
    )

    class ProviderError(provider: String, details: String) : DiagnosticApiException(
        "Erreur $provider : $details"
    )
}
