package com.uade.mecanicosya.assistance.api;

import com.uade.mecanicosya.assistance.application.ClientNotFoundException;
import com.uade.mecanicosya.assistance.application.DuplicateClientException;
import com.uade.mecanicosya.assistance.application.VehicleNotFoundException;
import com.uade.mecanicosya.assistance.domain.InvalidStatusTransitionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class AssistanceExceptionHandler {

    @ExceptionHandler({
            ClientNotFoundException.class,
            VehicleNotFoundException.class
    })
    ProblemDetail notFound(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Resource not found");
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
        problem.setTitle("Invalid assistance data");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidInput(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid assistance data");
        return problem;
    }

    @ExceptionHandler({
            InvalidStatusTransitionException.class,
            DuplicateClientException.class,
            DataIntegrityViolationException.class
    })
    ProblemDetail conflict(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Conflict with the current state");
        return problem;
    }

}
