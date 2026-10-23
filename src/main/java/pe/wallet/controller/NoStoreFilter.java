package pe.wallet.controller;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NoStoreFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (request.getUriInfo().getPath().startsWith("api/")) {
            response.getHeaders().putSingle("Cache-Control", "no-store");
        }
    }
}