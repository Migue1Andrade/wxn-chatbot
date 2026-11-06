package com.wxn.chatbot.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException error) {
        return problem(error.getStatus(), error.getMessage());
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> changed(org.springframework.dao.OptimisticLockingFailureException error) {
        return problem(HttpStatus.CONFLICT, "Registro alterado por outro usuário; atualize antes de salvar");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> fields(MethodArgumentNotValidException error) {
        var detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Verifique os campos informados"
        );
        java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();
        error
            .getBindingResult()
            .getFieldErrors()
            .forEach(field -> errors.putIfAbsent(field.getField(), field.getDefaultMessage()));
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                if ("users_username_unique".equals(constraint)) {
                    return problem(HttpStatus.CONFLICT, "Nome de usuário já cadastrado");
                }
                if ("users_email_unique".equals(constraint)) {
                    return problem(HttpStatus.CONFLICT, "E-mail já cadastrado");
                }
                if ("users_cpf_unique".equals(constraint)) {
                    return problem(HttpStatus.CONFLICT, "CPF já cadastrado");
                }
            }
            cause = cause.getCause();
        }
        return problem(HttpStatus.CONFLICT, "Dados duplicados ou vínculo inválido");
    }

    @ExceptionHandler({
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

    @ExceptionHandler(com.wxn.chatbot.whatsapp.provider.ProviderSendException.class)
    ResponseEntity<ProblemDetail> provider(com.wxn.chatbot.whatsapp.provider.ProviderSendException error) {
        return problem(HttpStatus.BAD_GATEWAY, "Operação do provider não confirmada: " + error.code());
    }

    @ExceptionHandler({
        jakarta.validation.ConstraintViolationException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class,
    })
    ResponseEntity<ProblemDetail> validation(Exception error) {
        return problem(HttpStatus.BAD_REQUEST, "Parâmetros inválidos");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NoResourceFoundException error) {
        return problem(HttpStatus.NOT_FOUND, "Recurso não encontrado");
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
