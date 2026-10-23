package pe.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.wallet.model.WalletData.*;
import pe.wallet.repository.JsonWalletRepository;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnalyticsServiceTest {
    @TempDir Path directory;
    private AnalyticsService service;

    @BeforeEach
    void prepare() {
        service = new AnalyticsService();
        service.repository = new JsonWalletRepository(directory.resolve("wallets.json"), new ObjectMapper().findAndRegisterModules());
        service.access = new AccessControl();
    }

    @Test
    void computesExactMedianMauAndCacWithTheLimaMonthBoundary() {
        Instant completed = Instant.parse("2026-10-15T18:00:00Z");
        service.repository.update(state -> {
            for (int duration : new int[]{60, 180, 300}) {
                UUID id = UUID.randomUUID();
                state.wallets.put(id, new Wallet(id, "test-hash", "PE", "organic", "Demo", id + "@wallet.test", Status.ACTIVE,
                        List.of(Step.values()), completed.minusSeconds(duration), completed));
                if (duration != 300) {
                    state.events.add(new Event(UUID.randomUUID(), id, EventType.WALLET_ACTIVITY, null, completed));
                    state.events.add(new Event(UUID.randomUUID(), id, EventType.WALLET_ACTIVITY, null, Instant.parse("2026-11-01T03:00:00Z")));
                }
                if (duration == 300) {
                    state.events.add(new Event(UUID.randomUUID(), id, EventType.WALLET_ACTIVITY, null, Instant.parse("2026-11-01T06:00:00Z")));
                }
            }
            state.expenses.put("expense", new Expense(UUID.randomUUID(), YearMonth.of(2026, 10), "organic", new BigDecimal("30.00"), completed));
            return null;
        });
        var report = service.monthly("2026-10", "organic");
        assertEquals(3, report.newActivatedUsers());
        assertEquals(2, report.monthlyActiveUsers());
        assertEquals(180.0, report.medianOnboardingSeconds());
        assertEquals(0, new BigDecimal("10.00").compareTo(report.cacPenPerNewActivatedUser()));
        assertEquals(1, service.monthly("2026-11", "organic").monthlyActiveUsers());
    }

    @Test
    void missingDataIsNotReportedAsZeroCostOrAReachedTarget() {
        var report = service.monthly("2026-10", null);
        assertEquals(0, report.newActivatedUsers());
        assertEquals(0, report.monthlyActiveUsers());
        assertNull(report.medianOnboardingSeconds());
        assertNull(report.acquisitionSpendPen());
        assertNull(report.cacPenPerNewActivatedUser());
        assertFalse(report.acquisitionSpendRecorded());
    }

    @Test
    void filtersAttributionByChannelAndKeepsCacNullWhenThereAreNoAcquisitions() {
        service.repository.update(state -> {
            state.expenses.put("expense", new Expense(UUID.randomUUID(), YearMonth.of(2026, 10), "referral", new BigDecimal("20.00"), Instant.now()));
            return null;
        });
        assertNull(service.monthly("2026-10", "organic").acquisitionSpendPen());
        var referral = service.monthly("2026-10", "referral");
        assertTrue(referral.acquisitionSpendRecorded());
        assertNull(referral.cacPenPerNewActivatedUser());
    }
}