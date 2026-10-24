package com.wxn.chatbot.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException error) {
        return problem(error.getStatus(), error.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException error) {
        return problem(HttpStatus.CONFLICT, "Dados duplicados ou vínculo inválido");
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        IllegalArgumentException.class,
    })
    ResponseEntity<ProblemDetail> invalid(Exception error) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Requisição inválida; verifique os campos obrigatórios e formatos"
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception error) {
        // Never expose database errors, provider credentials or payloads.
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Falha interna ao processar a requisição");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, message));
    }
}
