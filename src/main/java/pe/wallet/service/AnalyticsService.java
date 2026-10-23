package pe.wallet.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.wallet.model.WalletData.*;
import pe.wallet.repository.JsonWalletRepository;
import pe.wallet.view.ApiModels.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class AnalyticsService {
    private static final ZoneId ZONE = ZoneId.of("America/Lima");
    @Inject JsonWalletRepository repository;
    @Inject AccessControl access;

    public Expense cost(CostRequest request, String key) {
        String requestKey = access.requestKey(key);
        YearMonth month = parseMonth(request.month());
        return repository.update(state -> {
            Expense existing = state.expenses.get(requestKey);
            if (existing != null) {
                if (!existing.month().equals(month) || !existing.channel().equals(request.channel())
                        || existing.amountPen().compareTo(request.amountPen()) != 0) {
                    throw new ApiException(409, "IDEMPOTENCY_CONFLICT", "La clave del gasto ya se uso con otros valores.");
                }
                return existing;
            }
            Expense expense = new Expense(UUID.randomUUID(), month, request.channel(), request.amountPen(), Instant.now());
            state.expenses.put(requestKey, expense);
            return expense;
        });
    }

    public MonthlyReport monthly(String requestedMonth, String channel) {
        YearMonth month = parseMonth(requestedMonth);
        if (channel != null && !channel.matches("[a-z][a-z0-9_-]{2,39}")) {
            throw new ApiException(400, "INVALID_CHANNEL", "Usa el mismo identificador de canal que en el onboarding.");
        }
        return repository.read(state -> {
            List<Wallet> cohort = state.wallets.values().stream()
                    .filter(wallet -> "PE".equals(wallet.country()) && (channel == null || channel.equals(wallet.channel()))).toList();
            List<Wallet> activated = cohort.stream().filter(wallet -> wallet.completedAt() != null && inMonth(wallet.completedAt(), month)).toList();
            Set<UUID> cohortIds = cohort.stream().map(Wallet::id).collect(Collectors.toSet());
            long mau = state.events.stream().filter(event -> event.type() == EventType.WALLET_ACTIVITY
                    && inMonth(event.occurredAt(), month) && cohortIds.contains(event.walletId())).map(Event::walletId).distinct().count();
            List<Expense> expenses = state.expenses.values().stream().filter(expense -> expense.month().equals(month)
                    && (channel == null || expense.channel().equals(channel))).toList();
            BigDecimal spend = expenses.isEmpty() ? null : expenses.stream().map(Expense::amountPen).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cac = spend == null || activated.isEmpty() ? null : spend.divide(BigDecimal.valueOf(activated.size()), 2, RoundingMode.HALF_UP);
            return new MonthlyReport(month.toString(), ZONE.getId(), channel == null ? "ALL" : channel, activated.size(), mau,
                    median(activated), spend, cac, !expenses.isEmpty(), 180, 1000, 2000, new BigDecimal("10.00"),
                    List.of("Propuesta demo, pendiente de aprobacion del PO: no es evidencia de metas alcanzadas.",
                            "Duracion: ONBOARDING_STARTED hasta ONBOARDING_COMPLETED, incluye pausas y reanudaciones.",
                            "MAU: wallets unicas con actividad mock PAYMENT_ENTRY_OPENED registrada durante el mes, no altas.",
                            "CAC: gasto PEN registrado del mes/canal dividido entre wallets activadas ese mes/canal.",
                            "Gasto ausente o cero adquisiciones: CAC null. Cargar todos los canales antes de interpretar ALL.",
                            "Meses calendario en America/Lima. Los meses 1-3 se evaluan respecto de una fecha de lanzamiento aun no acordada."));
        });
    }

    public EventPage events(UUID walletId, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 200) {
            throw new ApiException(400, "INVALID_PAGINATION", "Usa offset >= 0 y limit entre 1 y 200.");
        }
        return repository.read(state -> {
            List<Event> events = state.events.stream().filter(event -> walletId == null || walletId.equals(event.walletId())).toList();
            return new EventPage(events.size(), offset, limit, events.stream().skip(offset).limit(limit).toList());
        });
    }

    private Double median(List<Wallet> wallets) {
        double[] durations = wallets.stream().mapToDouble(wallet -> Duration.between(wallet.startedAt(), wallet.completedAt()).toMillis() / 1000.0).sorted().toArray();
        if (durations.length == 0) { return null; }
        int middle = durations.length / 2;
        return durations.length % 2 == 0 ? (durations[middle - 1] + durations[middle]) / 2.0 : durations[middle];
    }

    private boolean inMonth(Instant timestamp, YearMonth month) {
        return YearMonth.from(timestamp.atZone(ZONE)).equals(month);
    }

    private YearMonth parseMonth(String value) {
        try {
            if (value == null || !value.matches("\\d{4}-(0[1-9]|1[0-2])")) { throw new DateTimeParseException("month", "", 0); }
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ApiException(400, "INVALID_MONTH", "Indica month con formato YYYY-MM, por ejemplo 2026-10.");
        }
    }
}