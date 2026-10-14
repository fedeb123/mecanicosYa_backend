package com.uade.mecanicosya.dispatch.api;

import com.uade.mecanicosya.dispatch.application.DispatchNotFoundException;
import com.uade.mecanicosya.dispatch.application.MechanicDirectoryUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DispatchExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchExceptionHandler.class);

    @ExceptionHandler(DispatchNotFoundException.class)
    ProblemDetail notFound(DispatchNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Dispatch not found");
        return problem;
    }

    @ExceptionHandler(MechanicDirectoryUnavailableException.class)
    ProblemDetail mechanicServiceUnavailable(MechanicDirectoryUnavailableException exception) {
        LOGGER.warn("Dispatch could not reach mechanic-service", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "mechanic-service is not available; retry the dispatch later"
        );
        problem.setTitle("Mechanic directory unavailable");
        return problem;
    }
}
