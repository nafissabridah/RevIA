package com.revia.error;

import org.springframework.http.HttpStatus;

/**
 * Catalogue des erreurs renvoyées au front-end. Les messages sont ceux de l'annexe
 * « Gestion des erreurs de l'API » de la présentation : aucune erreur technique brute
 * n'est affichée, le détail part dans les journaux.
 */
public enum ErrorCode {
    VALIDATION(HttpStatus.BAD_REQUEST, "Certaines informations sont invalides."),
    CODE_LONGUEUR(HttpStatus.BAD_REQUEST, "Collez entre 1 et 2 000 lignes de code."),
    NON_AUTHENTIFIE(HttpStatus.UNAUTHORIZED, "Votre session a expiré. Reconnectez-vous."),
    IDENTIFIANTS_INVALIDES(HttpStatus.UNAUTHORIZED, "E-mail ou mot de passe incorrect."),
    COMPTE_BLOQUE(HttpStatus.FORBIDDEN, "Votre compte est bloqué. Contactez l'administrateur."),
    INTERDIT(HttpStatus.FORBIDDEN, "Vous n'avez pas accès à cette ressource."),
    INTROUVABLE(HttpStatus.NOT_FOUND, "Élément introuvable."),
    EMAIL_DEJA_UTILISE(HttpStatus.CONFLICT, "Un compte existe déjà avec cet e-mail."),
    QUOTA_UTILISATEUR(HttpStatus.TOO_MANY_REQUESTS, "Vous avez atteint vos 50 revues du jour."),
    DEBIT_LIMITE(HttpStatus.TOO_MANY_REQUESTS, "Trop de demandes en peu de temps. Patientez quelques secondes."),
    LLM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "L'analyse prend plus de temps que prévu. Réessayez dans un instant."),
    LLM_QUOTA(HttpStatus.SERVICE_UNAVAILABLE, "Le service d'analyse est très sollicité. Réessayez dans une minute."),
    LLM_CLE_ABSENTE(HttpStatus.SERVICE_UNAVAILABLE, "Service d'analyse indisponible. L'administrateur a été prévenu."),
    LLM_REPONSE_INVALIDE(HttpStatus.BAD_GATEWAY, "Analyse incomplète : la réponse de l'IA n'a pas pu être validée. Réessayez."),
    LLM_INDISPONIBLE(HttpStatus.SERVICE_UNAVAILABLE, "Service d'analyse momentanément indisponible. Réessayez dans un instant."),
    LLM_IMAGE_NON_SUPPORTEE(HttpStatus.UNPROCESSABLE_ENTITY,
            "Ce fournisseur d'IA ne sait pas lire les images : collez plutôt le diagramme en Mermaid, BPMN ou draw.io."),
    WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "Signature de webhook invalide."),
    PAIEMENT_INDISPONIBLE(HttpStatus.SERVICE_UNAVAILABLE, "Le service de paiement est momentanément indisponible. Réessayez plus tard."),
    GITHUB_INDISPONIBLE(HttpStatus.BAD_GATEWAY, "GitHub est momentanément injoignable. Réessayez dans un instant."),
    GITHUB_DEJA_LIEE(HttpStatus.CONFLICT, "Cette installation GitHub est déjà reliée à un autre compte RevIA."),
    GITHUB_SATURE(HttpStatus.SERVICE_UNAVAILABLE, "Trop de pull requests en attente de revue. Réessayez plus tard."),
    INTERNE(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur inattendue s'est produite. Réessayez plus tard.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
