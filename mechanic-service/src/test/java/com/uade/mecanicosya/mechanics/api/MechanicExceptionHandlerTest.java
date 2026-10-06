package com.uade.mecanicosya.mechanics.api;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MechanicExceptionHandlerTest {

    @Test
    void listsEachInvalidFieldWithItsMessage() throws NoSuchMethodException {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "createMechanicRequest");
        result.addError(new FieldError("createMechanicRequest", "email", "must be a well-formed email address"));
        result.addError(new FieldError("createMechanicRequest", "name", "must not be blank"));
        MethodParameter parameter = new MethodParameter(
                MechanicExceptionHandlerTest.class.getDeclaredMethod("listsEachInvalidFieldWithItsMessage"),
                -1
        );

        ProblemDetail problem = new MechanicExceptionHandler()
                .invalidFields(new MethodArgumentNotValidException(parameter, result));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("The request has 2 invalid field(s)");
        assertThat(problem.getProperties()).containsEntry("errors", Map.of(
                "email", "must be a well-formed email address",
                "name", "must not be blank"
        ));
    }
}
