package com.uade.mecanicosya.identity.api;

import com.uade.mecanicosya.identity.application.EmailAlreadyRegisteredException;
import com.uade.mecanicosya.identity.application.InvalidCredentialsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail unauthorized(InvalidCredentialsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
        problem.setTitle("Unauthorized");
        return problem;
    }

    // Dos altas simultáneas con el mismo email: la restricción única deja pasar una sola.
    @ExceptionHandler({EmailAlreadyRegisteredException.class, DataIntegrityViolationException.class})
    ProblemDetail conflict(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                exception instanceof EmailAlreadyRegisteredException ? exception.getMessage()
                        : "There is already an account for this email");
        problem.setTitle("Email already registered");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidFields(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The request has " + errors.size() + " invalid field(s)"
        );
        problem.setTitle("Invalid account data");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalidInput(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                exception instanceof IllegalArgumentException ? exception.getMessage()
                        : "The body is not valid JSON or has an unknown role");
        problem.setTitle("Invalid account data");
        return problem;
    }
}
