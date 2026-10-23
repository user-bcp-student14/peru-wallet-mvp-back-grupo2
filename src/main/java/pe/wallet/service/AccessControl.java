package pe.wallet.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@ApplicationScoped
public class AccessControl {
    @ConfigProperty(name = "wallet.analytics-key")
    Optional<String> analyticsKey;

    public String tokenHash(String authorization) {
        if (authorization == null || !authorization.matches("Bearer [A-Za-z0-9_-]{43,128}")) {
            throw new ApiException(401, "WALLET_TOKEN_REQUIRED", "Envia Authorization: Bearer y el token de esta wallet (43 a 128 caracteres).");
        }
        return hash(authorization.substring(7));
    }

    public String requestKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,128}")) {
            throw new ApiException(400, "IDEMPOTENCY_KEY_REQUIRED", "Envia Idempotency-Key de 8 a 128 caracteres alfanumericos, guion o guion bajo.");
        }
        return hash(key);
    }

    public void requireAnalytics(String suppliedKey) {
        String configuredKey = analyticsKey.orElse("");
        if (configuredKey.isBlank()) {
            throw new ApiException(503, "ANALYTICS_NOT_CONFIGURED", "Configura WALLET_ANALYTICS_KEY para habilitar analitica.");
        }
        if (suppliedKey == null || !same(hash(configuredKey), hash(suppliedKey))) {
            throw new ApiException(401, "ANALYTICS_UNAUTHORIZED", "Envia una X-Analytics-Key valida.");
        }
    }

    public static boolean same(String first, String second) {
        return first != null && second != null && MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible.", exception);
        }
    }
}