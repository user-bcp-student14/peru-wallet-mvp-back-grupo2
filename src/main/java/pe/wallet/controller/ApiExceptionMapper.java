package pe.wallet.controller;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import pe.wallet.repository.JsonWalletRepository.StorageException;
import pe.wallet.service.ApiException;
import pe.wallet.view.ApiModels.ApiError;

import java.time.Instant;
import java.util.Map;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof ApiException api) { return response(api.status(), api.code(), api.getMessage()); }
        if (exception instanceof StorageException) {
            return response(503, "STORAGE_UNAVAILABLE", "No se pudo acceder al almacen. Conserva el archivo y revisa permisos, formato o bloqueo.");
        }
        if (exception instanceof WebApplicationException web) {
            return response(web.getResponse().getStatus(), "HTTP_ERROR", "Solicitud no valida para este recurso. Revisa ruta, parametros y JSON.");
        }
        LOG.errorf("Error no previsto: %s", exception.getClass().getSimpleName());
        return response(500, "INTERNAL_ERROR", "No se pudo completar la operacion.");
    }

    private Response response(int status, String code, String message) {
        return Response.status(status).type("application/json")
                .header("Cache-Control", "no-store")
                .entity(new ApiError(code, message, Map.of(), Instant.now())).build();
    }
}