package com.uade.mecanicosya.mechanics.api;

import com.uade.mecanicosya.mechanics.application.MechanicAlreadyRegisteredException;
import com.uade.mecanicosya.mechanics.application.MechanicNotFoundException;
import com.uade.mecanicosya.mechanics.domain.MechanicNotApprovedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class MechanicExceptionHandler {

    @ExceptionHandler(MechanicNotFoundException.class)
    ProblemDetail notFound(MechanicNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Mechanic not found");
        return problem;
    }

    @ExceptionHandler(MechanicNotApprovedException.class)
    ProblemDetail notApproved(MechanicNotApprovedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Mechanic pending verification");
        return problem;
    }

    // Dos altas del mismo perfil a la vez, o un email que ya usa otro perfil.
    @ExceptionHandler({MechanicAlreadyRegisteredException.class, DataIntegrityViolationException.class})
    ProblemDetail conflict(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                exception instanceof MechanicAlreadyRegisteredException ? exception.getMessage()
                        : "There is already a mechanic profile for this account or email");
        problem.setTitle("Mechanic already registered");
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
        problem.setTitle("Invalid mechanic data");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidInput(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid mechanic data");
        return problem;
    }
}

