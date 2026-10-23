package pe.wallet.controller;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.wallet.model.WalletData.Expense;
import pe.wallet.service.AccessControl;
import pe.wallet.service.AnalyticsService;
import pe.wallet.view.ApiModels.*;

import java.util.UUID;

@Path("/api/analytics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Analitica administrativa mock")
@SecurityScheme(securitySchemeName = "analytics-key", type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER, apiKeyName = "X-Analytics-Key")
@SecurityRequirement(name = "analytics-key")
public class AnalyticsController {
    @Inject AnalyticsService service;
    @Inject AccessControl access;

    @GET @Path("/monthly")
    public MonthlyReport monthly(@HeaderParam("X-Analytics-Key") String key,
                                 @QueryParam("month") String month, @QueryParam("channel") String channel) {
        access.requireAnalytics(key);
        return service.monthly(month, channel);
    }

    @POST @Path("/acquisition-costs")
    public Expense cost(@HeaderParam("X-Analytics-Key") String key, @HeaderParam("Idempotency-Key") String requestKey,
                        @Valid @NotNull CostRequest request) {
        access.requireAnalytics(key);
        return service.cost(request, requestKey);
    }

    @GET @Path("/events")
    public EventPage events(@HeaderParam("X-Analytics-Key") String key, @QueryParam("walletId") UUID walletId,
                            @QueryParam("offset") @DefaultValue("0") int offset,
                            @QueryParam("limit") @DefaultValue("100") int limit) {
        access.requireAnalytics(key);
        return service.events(walletId, offset, limit);
    }
}