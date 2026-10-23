package pe.wallet.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AccessControlTest {
    @Test
    void missingAnalyticsSecretDisablesAdministration() {
        AccessControl access = new AccessControl();
        access.analyticsKey = Optional.empty();
        ApiException error = assertThrows(ApiException.class, () -> access.requireAnalytics("anything"));
        assertEquals(503, error.status());
        assertEquals("ANALYTICS_NOT_CONFIGURED", error.code());
    }

    @Test
    void wrongKeyDoesNotGrantAdministrativeAccess() {
        AccessControl access = new AccessControl();
        access.analyticsKey = Optional.of("administrative-test-key");
        assertEquals(401, assertThrows(ApiException.class, () -> access.requireAnalytics("wrong-key")).status());
        assertDoesNotThrow(() -> access.requireAnalytics("administrative-test-key"));
    }
}