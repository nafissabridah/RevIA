package com.revia.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Toutes les exceptions passent par ici : aucune erreur technique brute n'est
 * renvoyée au navigateur, le détail est écrit dans les journaux.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErreurApi> api(ApiException e, HttpServletRequest req) {
        if (e.getCode().status().is5xxServerError()) {
            log.warn("{} {} -> {}", req.getMethod(), req.getRequestURI(), e.getMessage(), e.getCause());
        } else {
            log.debug("{} {} -> {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        }
        return reponse(e.getCode(), e.getMessageUtilisateur(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurApi> validation(MethodArgumentNotValidException e, HttpServletRequest req) {
        Map<String, String> champs = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            champs.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String message = champs.size() == 1 ? champs.values().iterator().next() : ErrorCode.VALIDATION.message();
        if (champs.containsKey("code")) {
            return reponse(ErrorCode.CODE_LONGUEUR, champs.get("code"), req, champs);
        }
        return reponse(ErrorCode.VALIDATION, message, req, champs);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ErreurApi> requeteInvalide(Exception e, HttpServletRequest req) {
        log.debug("Requête invalide {} : {}", req.getRequestURI(), e.getMessage());
        String msg = e instanceof IllegalArgumentException ? e.getMessage() : ErrorCode.VALIDATION.message();
        return reponse(ErrorCode.VALIDATION, msg, req, null);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ErreurApi> interdit(Exception e, HttpServletRequest req) {
        return reponse(ErrorCode.INTERDIT, ErrorCode.INTERDIT.message(), req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErreurApi> introuvable(NoResourceFoundException e, HttpServletRequest req) {
        return reponse(ErrorCode.INTROUVABLE, ErrorCode.INTROUVABLE.message(), req, null);
    }

    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErreurApi> methode(Exception e, HttpServletRequest req) {
        return reponse(ErrorCode.VALIDATION, "Requête non prise en charge.", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErreurApi> inattendue(Exception e, HttpServletRequest req) {
        log.error("Erreur inattendue sur {} {}", req.getMethod(), req.getRequestURI(), e);
        return reponse(ErrorCode.INTERNE, ErrorCode.INTERNE.message(), req, null);
    }

    private ResponseEntity<ErreurApi> reponse(ErrorCode code, String message, HttpServletRequest req,
                                              Map<String, String> champs) {
        return ResponseEntity.status(code.status()).body(ErreurApi.de(code, message, req.getRequestURI(), champs));
    }
}
