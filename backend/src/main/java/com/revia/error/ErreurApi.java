package com.revia.error;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Corps JSON de toutes les réponses d'erreur. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErreurApi(
        String code,
        String message,
        int status,
        String path,
        Instant timestamp,
        Map<String, String> champs) {

    public static ErreurApi de(ErrorCode code, String message, String path, Map<String, String> champs) {
        return new ErreurApi(code.name(), message, code.status().value(), path, Instant.now(), champs);
    }
}
