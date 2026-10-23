package pe.wallet.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.wallet.model.WalletData.*;
import pe.wallet.repository.JsonWalletRepository;
import pe.wallet.view.ApiModels.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

@ApplicationScoped
public class WalletService {
    @Inject JsonWalletRepository repository;
    @Inject AccessControl access;
    @ConfigProperty(name = "wallet.mock-kyc.enabled") boolean mockKycEnabled;
    @ConfigProperty(name = "wallet.mock-kyc.result") String mockKycResult;

    public RegistrationResult start(StartRequest request, String authorization, String idempotencyKey) {
        String tokenHash = access.tokenHash(authorization);
        String key = access.requestKey(idempotencyKey);
        return repository.update(state -> {
            UUID existingId = state.registrations.get(key);
            if (existingId != null) {
                Wallet existing = requireWallet(state, existingId, tokenHash);
                if (!existing.country().equals(request.country()) || !existing.channel().equals(request.channel())) {
                    throw new ApiException(409, "IDEMPOTENCY_CONFLICT", "La clave ya se uso con otros datos. Reutiliza la solicitud original.");
                }
                return new RegistrationResult(view(existing), false);
            }
            boolean tokenUsed = state.wallets.values().stream().anyMatch(wallet -> AccessControl.same(wallet.accessTokenHash(), tokenHash));
            if (tokenUsed) {
                throw new ApiException(409, "TOKEN_ALREADY_USED", "Ese token ya pertenece a una wallet. Consulta su progreso o reutiliza la clave original.");
            }
            Instant now = Instant.now();
            Wallet wallet = new Wallet(UUID.randomUUID(), tokenHash, request.country(), request.channel(), null, null,
                    Status.ONBOARDING, List.of(), now, null);
            state.wallets.put(wallet.id(), wallet);
            state.registrations.put(key, wallet.id());
            event(state, wallet, EventType.ONBOARDING_STARTED, null, now);
            return new RegistrationResult(view(wallet), true);
        });
    }

    public WalletView progress(UUID id, String authorization) {
        String tokenHash = access.tokenHash(authorization);
        return repository.read(state -> view(requireWallet(state, id, tokenHash)));
    }

    public WalletView profile(UUID id, String authorization, ProfileRequest request) {
        String tokenHash = access.tokenHash(authorization);
        String name = request.displayName().strip();
        String email = request.email().strip().toLowerCase(java.util.Locale.ROOT);
        if (name.isEmpty()) { throw new ApiException(400, "INVALID_PROFILE", "Ingresa un nombre ficticio no vacio."); }
        return repository.update(state -> {
            Wallet wallet = requireWallet(state, id, tokenHash);
            if (wallet.completedSteps().contains(Step.PROFILE)) {
                if (!Objects.equals(wallet.displayName(), name) || !Objects.equals(wallet.email(), email)) {
                    throw new ApiException(409, "STEP_ALREADY_COMPLETED", "El perfil ya fue confirmado. No se puede cambiar en este flujo.");
                }
                return view(wallet);
            }
            requireStep(wallet, Step.PROFILE);
            boolean duplicateEmail = state.wallets.values().stream().anyMatch(other -> !other.id().equals(id) && email.equals(other.email()));
            if (duplicateEmail) { throw new ApiException(409, "PROFILE_CONFLICT", "Ese correo de prueba ya esta registrado. Usa otro correo ficticio."); }
            Wallet updated = new Wallet(id, wallet.accessTokenHash(), wallet.country(), wallet.channel(), name, email,
                    Status.ONBOARDING, List.of(Step.PROFILE), wallet.startedAt(), null);
            state.wallets.put(id, updated);
            event(state, updated, EventType.STEP_COMPLETED, Step.PROFILE, Instant.now());
            return view(updated);
        });
    }

    public WalletView verify(UUID id, String authorization) {
        String tokenHash = access.tokenHash(authorization);
        return repository.update(state -> {
            Wallet wallet = requireWallet(state, id, tokenHash);
            if (wallet.completedSteps().contains(Step.VERIFICATION)) { return view(wallet); }
            requireStep(wallet, Step.VERIFICATION);
            if (!mockKycEnabled) {
                throw new ApiException(503, "KYC_NOT_CONFIGURED", "Solo existe un proveedor KYC simulado. La activacion real no esta habilitada.");
            }
            if ("REJECTED".equals(mockKycResult)) {
                throw new ApiException(422, "MOCK_VERIFICATION_REJECTED", "La verificacion simulada fue rechazada. No se activo la wallet.");
            }
            if ("PENDING".equals(mockKycResult)) {
                Wallet pending = new Wallet(id, wallet.accessTokenHash(), wallet.country(), wallet.channel(), wallet.displayName(), wallet.email(),
                        Status.PENDING_VERIFICATION, wallet.completedSteps(), wallet.startedAt(), null);
                state.wallets.put(id, pending);
                return view(pending);
            }
            if (!"APPROVED".equals(mockKycResult)) {
                throw new ApiException(503, "INVALID_KYC_CONFIGURATION", "El resultado KYC mock debe ser APPROVED, REJECTED o PENDING.");
            }
            Wallet updated = new Wallet(id, wallet.accessTokenHash(), wallet.country(), wallet.channel(), wallet.displayName(), wallet.email(),
                    Status.ONBOARDING, List.of(Step.PROFILE, Step.VERIFICATION), wallet.startedAt(), null);
            state.wallets.put(id, updated);
            event(state, updated, EventType.STEP_COMPLETED, Step.VERIFICATION, Instant.now());
            return view(updated);
        });
    }

    public WalletView consent(UUID id, String authorization) {
        String tokenHash = access.tokenHash(authorization);
        return repository.update(state -> {
            Wallet wallet = requireWallet(state, id, tokenHash);
            if (wallet.status() == Status.ACTIVE) { return view(wallet); }
            requireStep(wallet, Step.CONSENT);
            Instant now = Instant.now();
            Wallet updated = new Wallet(id, wallet.accessTokenHash(), wallet.country(), wallet.channel(), wallet.displayName(), wallet.email(),
                    Status.ACTIVE, List.of(Step.values()), wallet.startedAt(), now);
            state.wallets.put(id, updated);
            event(state, updated, EventType.STEP_COMPLETED, Step.CONSENT, now);
            event(state, updated, EventType.ONBOARDING_COMPLETED, null, now);
            return view(updated);
        });
    }

    public PaymentEntry paymentEntry(UUID id, String authorization) {
        String tokenHash = access.tokenHash(authorization);
        return repository.read(state -> {
            Wallet wallet = requireWallet(state, id, tokenHash);
            requireActive(wallet);
            return new PaymentEntry(id, true, "PEN", "MOCK", "Punto de entrada disponible. Esta demo no procesa pagos reales.");
        });
    }

    public ActivityReceipt activity(UUID id, String authorization, String idempotencyKey) {
        String tokenHash = access.tokenHash(authorization);
        String key = id + ":" + access.requestKey(idempotencyKey);
        return repository.update(state -> {
            Wallet wallet = requireWallet(state, id, tokenHash);
            requireActive(wallet);
            ActivityReceipt existing = state.activities.get(key);
            if (existing != null) { return existing; }
            Event activity = event(state, wallet, EventType.WALLET_ACTIVITY, null, Instant.now());
            ActivityReceipt receipt = new ActivityReceipt(activity.id(), activity.occurredAt());
            state.activities.put(key, receipt);
            return receipt;
        });
    }

    private Wallet requireWallet(State state, UUID id, String tokenHash) {
        Wallet wallet = state.wallets.get(id);
        if (wallet == null) { throw new ApiException(404, "WALLET_NOT_FOUND", "No se encontro la wallet solicitada."); }
        if (!AccessControl.same(wallet.accessTokenHash(), tokenHash)) {
            throw new ApiException(401, "WALLET_UNAUTHORIZED", "El token no permite acceder a esta wallet.");
        }
        return wallet;
    }

    private void requireActive(Wallet wallet) {
        if (wallet.status() != Status.ACTIVE) { throw new ApiException(409, "WALLET_NOT_ACTIVE", "Completa perfil, verificacion y consentimiento antes de acceder a pagos."); }
    }

    private void requireStep(Wallet wallet, Step expected) {
        if (currentStep(wallet) != expected) { throw new ApiException(409, "STEP_OUT_OF_ORDER", "Completa primero el paso " + currentStep(wallet) + "."); }
    }

    private Step currentStep(Wallet wallet) {
        return Arrays.stream(Step.values()).filter(step -> !wallet.completedSteps().contains(step)).findFirst().orElse(null);
    }

    private WalletView view(Wallet wallet) {
        Step current = currentStep(wallet);
        String nextAction = current == null ? "Abre el punto de entrada de pagos mock."
                : switch (current) {
                    case PROFILE -> "Completa tu nombre ficticio y correo .test.";
                    case VERIFICATION -> wallet.status() == Status.PENDING_VERIFICATION
                            ? "Verificacion mock pendiente. Reintenta cuando el proveedor simulado resuelva."
                            : "Solicita la verificacion simulada del perfil.";
                    case CONSENT -> "Acepta los terminos de la demo para activar la wallet mock.";
                };
        List<StepView> steps = IntStream.range(0, Step.values().length).mapToObj(position -> {
            Step step = Step.values()[position];
            return new StepView(step, position + 1, wallet.completedSteps().contains(step), step == current);
        }).toList();
        return new WalletView(wallet.id(), wallet.status(), wallet.country(), wallet.channel(), steps, current, nextAction,
                wallet.startedAt(), wallet.completedAt(), wallet.status() == Status.ACTIVE, true);
    }

    private Event event(State state, Wallet wallet, EventType type, Step step, Instant now) {
        Event event = new Event(UUID.randomUUID(), wallet.id(), type, step, now);
        state.events.add(event);
        return event;
    }
}