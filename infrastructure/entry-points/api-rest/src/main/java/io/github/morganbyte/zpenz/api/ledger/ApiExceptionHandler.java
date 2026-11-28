package io.github.morganbyte.zpenz.api.ledger;

import io.github.morganbyte.zpenz.model.ledger.AccountNotFoundException;
import io.github.morganbyte.zpenz.model.ledger.EntryAlreadyReversedException;
import io.github.morganbyte.zpenz.model.ledger.EntryIsReversalException;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKeyReusedException;
import io.github.morganbyte.zpenz.model.ledger.InsufficientFundsException;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Errores en formato Problem Details (RFC 9457) con un {@code code} estable (ADR-0009). Los
 * errores propios de Spring MVC (cuerpo ilegible, cabecera faltante, ruta inexistente) los
 * resuelve la clase base; aquí solo se les agrega el {@code code}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** Un campo que no pasó la validación. */
    public record FieldProblem(String field, String message) {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "invalid-request");
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail accountNotFound(AccountNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage(), "account-not-found");
    }

    @ExceptionHandler(JournalEntryNotFoundException.class)
    ProblemDetail entryNotFound(JournalEntryNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage(), "entry-not-found");
    }

    @ExceptionHandler(EntryAlreadyReversedException.class)
    ProblemDetail alreadyReversed(EntryAlreadyReversedException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage(), "entry-already-reversed");
    }

    @ExceptionHandler(EntryIsReversalException.class)
    ProblemDetail isReversal(EntryIsReversalException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage(), "entry-is-reversal");
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail insufficientFunds(InsufficientFundsException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), "insufficient-funds");
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail keyReused(IdempotencyKeyReusedException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), "idempotency-key-reused");
    }

    /** Un cuerpo JSON que no pasa Bean Validation: se reportan todos los campos, no solo el primero. */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldProblem(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(validationFailed(errors));
    }

    /** Una cabecera o un parámetro que no pasa Bean Validation, como {@code Idempotency-Key} o {@code limit}. */
    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = new ArrayList<>();
        e.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            var header = result.getMethodParameter().getParameterAnnotation(
                    org.springframework.web.bind.annotation.RequestHeader.class);
            var param = result.getMethodParameter().getParameterAnnotation(
                    org.springframework.web.bind.annotation.RequestParam.class);
            if (header != null) {
                name = header.value();
            } else if (param != null && !param.name().isEmpty()) {
                name = param.name();
            }
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(new FieldProblem(error instanceof FieldError f ? f.getField() : name, error.getDefaultMessage()));
            }
        });
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(validationFailed(errors));
    }

    /**
     * Agrega el {@code code} a los errores que resuelve Spring MVC. La clase base arma el cuerpo
     * dentro de este método, así que se completa después de llamarla.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception e, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(e, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            problem.setProperty("code", switch (status.value()) {
                case 404 -> "not-found";
                case 405 -> "method-not-allowed";
                case 406, 415 -> "unsupported-media-type";
                default -> status.is4xxClientError() ? "invalid-request" : "internal-error";
            });
        }
        return response;
    }

    private static ProblemDetail validationFailed(List<FieldProblem> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "La petición tiene campos inválidos", "validation-failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
