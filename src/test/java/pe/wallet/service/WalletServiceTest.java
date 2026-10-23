package pe.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.wallet.model.WalletData.Status;
import pe.wallet.model.WalletData.Step;
import pe.wallet.repository.JsonWalletRepository;
import pe.wallet.view.ApiModels.ProfileRequest;
import pe.wallet.view.ApiModels.StartRequest;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WalletServiceTest {
    @TempDir Path directory;
    private WalletService service;
    private String authorization;
    private UUID id;

    @BeforeEach
    void prepare() {
        service = new WalletService();
        service.repository = new JsonWalletRepository(directory.resolve("wallets.json"), new ObjectMapper().findAndRegisterModules());
        service.access = new AccessControl();
        service.mockKycEnabled = true;
        authorization = "Bearer " + "a".repeat(64);
        id = service.start(new StartRequest("PE", "organic"), authorization, "request-12345678").wallet().walletId();
        service.profile(id, authorization, new ProfileRequest("Demo", "demo@wallet.test"));
    }

    @Test
    void pendingVerificationNeverActivatesTheWallet() {
        service.mockKycResult = "PENDING";
        assertEquals(Status.PENDING_VERIFICATION, service.verify(id, authorization).status());
        ApiException failure = assertThrows(ApiException.class, () -> service.consent(id, authorization));
        assertEquals(409, failure.status());
        assertEquals(Step.VERIFICATION, service.progress(id, authorization).currentStep());
    }

    @Test
    void rejectedVerificationDoesNotAdvanceOrCreateCompletedEvents() {
        service.mockKycResult = "REJECTED";
        ApiException failure = assertThrows(ApiException.class, () -> service.verify(id, authorization));
        assertEquals(422, failure.status());
        assertEquals(Step.VERIFICATION, service.progress(id, authorization).currentStep());
        int events = service.repository.read(state -> state.events.size());
        assertEquals(2, events);
    }

    @Test
    void disabledMockProviderFailsClosed() {
        service.mockKycEnabled = false;
        service.mockKycResult = "APPROVED";
        ApiException failure = assertThrows(ApiException.class, () -> service.verify(id, authorization));
        assertEquals(503, failure.status());
        assertFalse(service.progress(id, authorization).paymentsAvailable());
    }

    @Test
    void providerCanResolveAPreviouslyPendingVerification() {
        service.mockKycResult = "PENDING";
        service.verify(id, authorization);
        service.mockKycResult = "APPROVED";
        assertEquals(Step.CONSENT, service.verify(id, authorization).currentStep());
        assertEquals(Status.ACTIVE, service.consent(id, authorization).status());
    }
}