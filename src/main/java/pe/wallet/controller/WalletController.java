package pe.wallet.controller;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.wallet.model.WalletData.ActivityReceipt;
import pe.wallet.service.WalletService;
import pe.wallet.view.ApiModels.*;

import java.net.URI;
import java.util.UUID;

@Path("/api/wallets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Wallet onboarding mock")
@SecurityScheme(securitySchemeName = "wallet-token", type = SecuritySchemeType.HTTP, scheme = "bearer")
@SecurityRequirement(name = "wallet-token")
public class WalletController {
    @Inject WalletService service;

    @POST
    @Operation(summary = "Iniciar onboarding PE. El cliente genera un token aleatorio y conserva la clave de idempotencia.")
    public Response start(@HeaderParam("Authorization") String authorization,
                          @HeaderParam("Idempotency-Key") String key, @Valid @NotNull StartRequest request) {
        RegistrationResult result = service.start(request, authorization, key);
        return Response.status(result.created() ? 201 : 200)
                .location(URI.create("/api/wallets/" + result.wallet().walletId() + "/onboarding"))
                .entity(result.wallet()).build();
    }

    @GET @Path("/{walletId}/onboarding")
    @Operation(summary = "Consultar pasos, progreso y siguiente accion; sirve tambien para reanudar.")
    public WalletView progress(@PathParam("walletId") UUID id, @HeaderParam("Authorization") String authorization) {
        return service.progress(id, authorization);
    }

    @PUT @Path("/{walletId}/onboarding/profile")
    public WalletView profile(@PathParam("walletId") UUID id, @HeaderParam("Authorization") String authorization,
                              @Valid @NotNull ProfileRequest request) {
        return service.profile(id, authorization, request);
    }

    @POST @Path("/{walletId}/onboarding/verification")
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Verificacion KYC simulada, nunca un alta financiera real.")
    public WalletView verify(@PathParam("walletId") UUID id, @HeaderParam("Authorization") String authorization) {
        return service.verify(id, authorization);
    }

    @PUT @Path("/{walletId}/onboarding/consent")
    public WalletView consent(@PathParam("walletId") UUID id, @HeaderParam("Authorization") String authorization,
                              @Valid @NotNull ConsentRequest request) {
        return service.consent(id, authorization);
    }

    @GET @Path("/{walletId}/payments/entry")
    public PaymentEntry entry(@PathParam("walletId") UUID id, @HeaderParam("Authorization") String authorization) {
        return service.paymentEntry(id, authorization);
    }

    @POST @Path("/{walletId}/activity")
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Registrar PAYMENT_ENTRY_OPENED mock una vez por Idempotency-Key para medir actividad, no pagos reales.")
    public ActivityReceipt activity(@PathParam("walletId") UUID id, @HeaderParam("Authorization") String authorization,
                                    @HeaderParam("Idempotency-Key") String key) {
        return service.activity(id, authorization, key);
    }
}