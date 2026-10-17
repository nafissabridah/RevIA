package com.revia.error;

/** Exception métier convertie en réponse JSON propre par {@link GlobalExceptionHandler}. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final String messageUtilisateur;

    public ApiException(ErrorCode code) {
        this(code, code.message(), null);
    }

    public ApiException(ErrorCode code, String messageUtilisateur) {
        this(code, messageUtilisateur, null);
    }

    public ApiException(ErrorCode code, String messageUtilisateur, Throwable cause) {
        super(code.name() + " : " + messageUtilisateur, cause);
        this.code = code;
        this.messageUtilisateur = messageUtilisateur;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getMessageUtilisateur() {
        return messageUtilisateur;
    }
}
