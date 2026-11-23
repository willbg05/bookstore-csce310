package auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Optional;

import javax.sql.DataSource;

import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;


public class AuthService {

    private final DataSource dataSrc;

    public AuthService(DataSource dataSrc) {
        this.dataSrc = dataSrc;
    }

    public Optional<LoginResult> login(String username, String password) throws SQLException {
        Optional<User> user = findByUsername(username);

        if (user.isEmpty() || !pwMatches(password, user.get().passwordHash())) {
            return Optional.empty();
        }

        long userId = user.get().id();
        String token = generateSessionToken();
        insertSession(token, userId);
        return Optional.of(new LoginResult(userId, token));
    }

    public record LoginResult(long userId, String token) {}

    private record User(long id, String passwordHash) {}

    private Optional<User> findByUsername(String username) throws SQLException {
        String sql = "SELECT id, password_hash FROM users WHERE username = ?";

        try (Connection connection = dataSrc.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, username);

                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }

                    long id = rows.getLong("id");
                    String passwordHash = rows.getString("password_hash");
                    return Optional.of(new User(id, passwordHash));

                }
            }
    }

    private static final Argon2Function PASSWORD_HASHER =
        Argon2Function.getInstance(19 * 1024, 2, 1, 32, Argon2.ID);

    private static String hashPassword(String password) {
        return Password.hash(password)
                .addRandomSalt(16)
                .with(PASSWORD_HASHER)
                .getResult();
    }

    private boolean pwMatches(String password, String storedHash) {
        Argon2Function hasher = Argon2Function.getInstanceFromHash(storedHash);
        return Password.check(password, storedHash).with(hasher);
    }

    private static final SecureRandom SESSION_RANDOM = new SecureRandom();

    private static String generateSessionToken() {
        byte[] randomBytes = new byte[32];
        SESSION_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static byte[] hashSessionToken(String token) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void insertSession(String token, long userId) throws SQLException {
        String sql = "INSERT INTO sessions (token_hash, user_id) VALUES (?, ?)";

        try (Connection connection = dataSrc.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, hashSessionToken(token));
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }

    public Optional<Long> getSessionUserId(String token) throws SQLException {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT user_id FROM sessions WHERE token_hash = ?";

        try (Connection connection = dataSrc.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, hashSessionToken(token));

            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(rows.getLong("user_id"));
            }
        }
    }

    public void logout(String token) throws SQLException {
        if (token == null || token.isBlank()) {
            return;
        }

        String sql = "DELETE FROM sessions WHERE token_hash = ?";

        try (Connection connection = dataSrc.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, hashSessionToken(token));
            statement.executeUpdate();
        }
    }
}
