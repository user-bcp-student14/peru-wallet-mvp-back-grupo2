package pe.wallet.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WalletData {
    private WalletData() {}

    public enum Step { PROFILE, VERIFICATION, CONSENT }
    public enum Status { ONBOARDING, PENDING_VERIFICATION, ACTIVE }
    public enum EventType { ONBOARDING_STARTED, STEP_COMPLETED, ONBOARDING_COMPLETED, WALLET_ACTIVITY }

    public record Wallet(UUID id, String accessTokenHash, String country, String channel,
                         String displayName, String email, Status status, List<Step> completedSteps,
                         Instant startedAt, Instant completedAt) {}

    public record Event(UUID id, UUID walletId, EventType type, Step step, Instant occurredAt) {}
    public record Expense(UUID id, YearMonth month, String channel, BigDecimal amountPen, Instant recordedAt) {}
    public record ActivityReceipt(UUID eventId, Instant occurredAt) {}

    public static final class State {
        public int schemaVersion = 1;
        public Map<UUID, Wallet> wallets = new LinkedHashMap<>();
        public Map<String, UUID> registrations = new LinkedHashMap<>();
        public List<Event> events = new ArrayList<>();
        public Map<String, Expense> expenses = new LinkedHashMap<>();
        public Map<String, ActivityReceipt> activities = new LinkedHashMap<>();
    }
}