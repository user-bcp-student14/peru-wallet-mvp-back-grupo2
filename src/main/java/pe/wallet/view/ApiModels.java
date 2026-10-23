package pe.wallet.view;

import jakarta.validation.constraints.*;
import pe.wallet.model.WalletData.Event;
import pe.wallet.model.WalletData.Status;
import pe.wallet.model.WalletData.Step;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {}

    public record StartRequest(
            @NotBlank @Pattern(regexp = "PE", message = "La cohorte mock admite solo PE.") String country,
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{2,39}", message = "Usa un canal de 3 a 40 caracteres: organic, referral o campaign_demo.") String channel) {}

    public record ProfileRequest(
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Email @Size(max = 120)
            @Pattern(regexp = "(?i).+@[^@]+\\.test", message = "Usa un correo ficticio terminado en .test.") String email) {}

    public record ConsentRequest(
            @NotNull @AssertTrue(message = "Debes aceptar los terminos de la demo para continuar.") Boolean accepted) {}

    public record CostRequest(
            @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Usa YYYY-MM.") String month,
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{2,39}") String channel,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amountPen) {}

    public record StepView(Step name, int position, boolean completed, boolean current) {}
    public record WalletView(UUID walletId, Status status, String country, String channel,
                             List<StepView> steps, Step currentStep, String nextAction,
                             Instant startedAt, Instant completedAt, boolean paymentsAvailable, boolean mock) {}
    public record RegistrationResult(WalletView wallet, boolean created) {}
    public record PaymentEntry(UUID walletId, boolean available, String currency, String mode, String message) {}
    public record EventPage(int total, int offset, int limit, List<Event> events) {}
    public record MonthlyReport(String month, String timezone, String channel, long newActivatedUsers,
                                long monthlyActiveUsers, Double medianOnboardingSeconds,
                                BigDecimal acquisitionSpendPen, BigDecimal cacPenPerNewActivatedUser,
                                boolean acquisitionSpendRecorded, int targetMedianSeconds,
                                int targetMauMinimum, int targetMauMaximum, BigDecimal targetCacPen,
                                List<String> definitions) {}
    public record ApiError(String code, String message, Map<String, String> fields, Instant timestamp) {}
}