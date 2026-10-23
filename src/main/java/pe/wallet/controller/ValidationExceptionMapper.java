package pe.wallet.controller;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import pe.wallet.view.ApiModels.ApiError;

import java.time.Instant;
import java.util.TreeMap;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override
    public Response toResponse(ConstraintViolationException exception) {
        var fields = new TreeMap<String, String>();
        exception.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            fields.put(path.substring(path.lastIndexOf('.') + 1), violation.getMessage());
        });
        return Response.status(400).type("application/json").header("Cache-Control", "no-store")
                .entity(new ApiError("VALIDATION_ERROR", "Corrige los campos indicados antes de avanzar.", fields, Instant.now())).build();
    }
}