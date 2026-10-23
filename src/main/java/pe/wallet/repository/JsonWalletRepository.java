package pe.wallet.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.wallet.model.WalletData.State;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.Function;

@ApplicationScoped
public class JsonWalletRepository {
    private final Path path;
    private final ObjectMapper mapper;

    @Inject
    public JsonWalletRepository(ObjectMapper mapper, @ConfigProperty(name = "wallet.storage.path") String path) {
        this(Path.of(path), mapper);
    }

    public JsonWalletRepository(Path path, ObjectMapper mapper) {
        this.path = path.toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    public <Result> Result read(Function<State, Result> operation) {
        return execute(operation, false);
    }

    public <Result> Result update(Function<State, Result> operation) {
        return execute(operation, true);
    }

    private synchronized <Result> Result execute(Function<State, Result> operation, boolean write) {
        try {
            Files.createDirectories(path.getParent());
            try (var channel = FileChannel.open(path.resolveSibling(path.getFileName() + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.tryLock()) {
                if (lock == null) { throw new StorageException("El almacen esta ocupado.", null); }
                State state = load();
                Result result = operation.apply(state);
                if (write) { save(state); }
                return result;
            }
        } catch (IOException | java.nio.channels.OverlappingFileLockException exception) {
            throw new StorageException("No se puede acceder al almacen JSON.", exception);
        }
    }

    private State load() throws IOException {
        if (!Files.exists(path)) { return new State(); }
        var document = mapper.readTree(path.toFile());
        if (document == null || !document.isObject() || document.path("schemaVersion").asInt(-1) != 1
                || !document.path("wallets").isObject() || !document.path("registrations").isObject()
                || !document.path("events").isArray() || !document.path("expenses").isObject()
                || !document.path("activities").isObject()) {
            throw new IOException("Estructura JSON incompleta.");
        }
        State state = mapper.treeToValue(document, State.class);
        if (state == null || state.schemaVersion != 1 || state.wallets == null || state.events == null
                || state.registrations == null || state.expenses == null || state.activities == null) {
            throw new IOException("Esquema JSON no valido.");
        }
        return state;
    }

    private void save(State state) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), "wallet-", ".tmp");
        try {
            byte[] content = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(state);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) { channel.write(buffer); }
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) { super(message, cause); }
    }
}