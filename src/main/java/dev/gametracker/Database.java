package dev.gametracker;

import java.sql.*;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Database {
    private static final Path DATABASE_DIRECTORY = getDatabaseDirectory();
    private static final Path DATABASE_PATH = DATABASE_DIRECTORY.resolve("game-tracker.db");
    private static final Path LEGACY_DATABASE_PATH = Path.of("game-tracker.db").toAbsolutePath().normalize();
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");
    private static final String URL = "jdbc:sqlite:" + DATABASE_PATH;

    private static Path getDatabaseDirectory() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.startsWith("windows")) {
            String appData = System.getenv("APPDATA");
            Path roaming = appData == null || appData.isBlank()
                    ? Path.of(System.getProperty("user.home"), "AppData", "Roaming")
                    : Path.of(appData);
            return roaming.resolve("GameTracker");
        }
        if (osName.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "GameTracker");
        }
        String dataHome = System.getenv("XDG_DATA_HOME");
        Path base = dataHome == null || dataHome.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share")
                : Path.of(dataHome);
        return base.resolve("gametracker");
    }

    public void initialize() {
        preparePrivateStorage();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS games (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        platform TEXT NOT NULL,
                        hours REAL NOT NULL DEFAULT 0,
                        source TEXT NOT NULL DEFAULT 'Manual',
                        process_match TEXT NOT NULL DEFAULT ''
                    )
                    """);
            if (!hasColumn(c, "games", "process_match")) {
                s.executeUpdate("ALTER TABLE games ADD COLUMN process_match TEXT NOT NULL DEFAULT ''");
            }
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        game_id INTEGER NOT NULL REFERENCES games(id),
                        started_at INTEGER NOT NULL,
                        ended_at INTEGER,
                        last_checkpoint_at INTEGER NOT NULL,
                        duration_seconds REAL NOT NULL DEFAULT 0,
                        source TEXT NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialize the local game database.", e);
        }
    }

    private void preparePrivateStorage() {
        try {
            Files.createDirectories(DATABASE_DIRECTORY);
            boolean supportsPosix = Files.getFileStore(DATABASE_DIRECTORY).supportsFileAttributeView("posix");
            if (supportsPosix) Files.setPosixFilePermissions(DATABASE_DIRECTORY, DIRECTORY_PERMISSIONS);
            if (!Files.exists(DATABASE_PATH)) {
                if (Files.exists(LEGACY_DATABASE_PATH)
                        && !LEGACY_DATABASE_PATH.equals(DATABASE_PATH.toAbsolutePath().normalize())) {
                    try {
                        Files.move(LEGACY_DATABASE_PATH, DATABASE_PATH, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(LEGACY_DATABASE_PATH, DATABASE_PATH);
                    }
                    if (supportsPosix) Files.setPosixFilePermissions(DATABASE_PATH, FILE_PERMISSIONS);
                } else {
                    if (supportsPosix) {
                        Files.createFile(DATABASE_PATH,
                                PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
                    } else {
                        Files.createFile(DATABASE_PATH);
                    }
                }
            }
            if (supportsPosix) Files.setPosixFilePermissions(DATABASE_PATH, FILE_PERMISSIONS);
        } catch (IOException | UnsupportedOperationException e) {
            throw new IllegalStateException(
                    "Could not create private game data storage in " + DATABASE_DIRECTORY, e);
        }
    }

    private Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(URL);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
        }
        return c;
    }

    private boolean hasColumn(Connection c, String table, String column) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (r.next()) {
                if (column.equalsIgnoreCase(r.getString("name"))) return true;
            }
        }
        return false;
    }

    public List<Game> getGames() {
        List<Game> out = new ArrayList<>();
        try (Connection c = connect();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT * FROM games ORDER BY name COLLATE NOCASE")) {
            while (r.next()) {
                out.add(new Game(r.getInt("id"), r.getString("name"), r.getString("platform"),
                        r.getDouble("hours"), r.getString("source"), r.getString("process_match")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load the game library.", e);
        }
        return out;
    }

    public void addGame(String name, String platform) {
        addGame(name, platform, "");
    }

    public void addGame(String name, String platform, String processMatch) {
        try (Connection c = connect();
             PreparedStatement p = c.prepareStatement(
                     "INSERT INTO games(name,platform,process_match) VALUES(?,?,?)")) {
            p.setString(1, name);
            p.setString(2, platform);
            p.setString(3, processMatch);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not add the game.", e);
        }
    }

    public void updateProcessMatch(int id, String processMatch) {
        try (Connection c = connect();
             PreparedStatement p = c.prepareStatement("UPDATE games SET process_match=? WHERE id=?")) {
            p.setString(1, processMatch);
            p.setInt(2, id);
            if (p.executeUpdate() == 0) throw new IllegalArgumentException("The selected game no longer exists.");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update the game's process name.", e);
        }
    }

    public void addHours(int id, double hours, String source) {
        try (Connection c = connect();
             PreparedStatement p = c.prepareStatement("UPDATE games SET hours=hours+?, source=? WHERE id=?")) {
            p.setDouble(1, hours);
            p.setString(2, source);
            p.setInt(3, id);
            if (p.executeUpdate() == 0) throw new IllegalArgumentException("The selected game no longer exists.");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update game playtime.", e);
        }
    }

    public void upsertSteam(String name, double hours) {
        try (Connection c = connect()) {
            try (PreparedStatement q = c.prepareStatement(
                    "SELECT id FROM games WHERE name=? COLLATE NOCASE AND platform='Steam'")) {
                q.setString(1, name);
                try (ResultSet r = q.executeQuery()) {
                    if (r.next()) {
                        try (PreparedStatement u = c.prepareStatement(
                                "UPDATE games SET hours=?,source='Steam Web API' WHERE id=?")) {
                            u.setDouble(1, hours);
                            u.setInt(2, r.getInt(1));
                            u.executeUpdate();
                        }
                        return;
                    }
                }
            }
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO games(name,platform,hours,source) VALUES(?,'Steam',?,'Steam Web API')")) {
                p.setString(1, name);
                p.setDouble(2, hours);
                p.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save imported Steam playtime.", e);
        }
    }

    public int startSession(int gameId, long startedAt, String source) {
        try (Connection c = connect()) {
            try (PreparedStatement q = c.prepareStatement("SELECT id FROM sessions WHERE ended_at IS NULL");
                 ResultSet r = q.executeQuery()) {
                if (r.next()) throw new IllegalStateException("A session is already running.");
            }
            try (PreparedStatement p = c.prepareStatement("""
                    INSERT INTO sessions(game_id,started_at,last_checkpoint_at,source)
                    VALUES(?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                p.setInt(1, gameId);
                p.setLong(2, startedAt);
                p.setLong(3, startedAt);
                p.setString(4, source);
                p.executeUpdate();
                try (ResultSet keys = p.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
            throw new SQLException("SQLite did not return a session id.");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not start the play session.", e);
        }
    }

    public void checkpointSession(int sessionId, long checkpointAt) {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            int gameId;
            long lastCheckpoint;
            try (PreparedStatement q = c.prepareStatement(
                    "SELECT game_id,last_checkpoint_at FROM sessions WHERE id=? AND ended_at IS NULL")) {
                q.setInt(1, sessionId);
                try (ResultSet r = q.executeQuery()) {
                    if (!r.next()) throw new IllegalStateException("The active session no longer exists.");
                    gameId = r.getInt("game_id");
                    lastCheckpoint = r.getLong("last_checkpoint_at");
                }
            }
            double elapsedSeconds = Math.max(0, checkpointAt - lastCheckpoint) / 1000.0;
            try (PreparedStatement p = c.prepareStatement("""
                    UPDATE sessions
                    SET duration_seconds=duration_seconds+?,last_checkpoint_at=?
                    WHERE id=? AND ended_at IS NULL
                    """)) {
                p.setDouble(1, elapsedSeconds);
                p.setLong(2, Math.max(checkpointAt, lastCheckpoint));
                p.setInt(3, sessionId);
                p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement(
                    "UPDATE games SET hours=hours+?,source='Locally tracked session' WHERE id=?")) {
                p.setDouble(1, elapsedSeconds / 3600.0);
                p.setInt(2, gameId);
                p.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save play session progress.", e);
        }
    }

    public void finishSession(int sessionId, long endedAt) {
        checkpointSession(sessionId, endedAt);
        try (Connection c = connect();
             PreparedStatement p = c.prepareStatement(
                     "UPDATE sessions SET ended_at=? WHERE id=? AND ended_at IS NULL")) {
            p.setLong(1, endedAt);
            p.setInt(2, sessionId);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not finish the play session.", e);
        }
    }

    public ActiveSession getActiveSession() {
        try (Connection c = connect();
             PreparedStatement p = c.prepareStatement("""
                     SELECT s.id,s.game_id,s.started_at,g.name
                     FROM sessions s JOIN games g ON g.id=s.game_id
                     WHERE s.ended_at IS NULL ORDER BY s.started_at DESC LIMIT 1
                     """);
             ResultSet r = p.executeQuery()) {
            if (r.next()) return new ActiveSession(r.getInt("id"), r.getInt("game_id"),
                    r.getLong("started_at"), r.getString("name"));
            return null;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not check for an active session.", e);
        }
    }

    public void recoverInterruptedSessions() {
        try (Connection c = connect();
             Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE sessions SET ended_at=last_checkpoint_at WHERE ended_at IS NULL");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not recover interrupted play sessions.", e);
        }
    }

    public double getHoursBetween(long from, long to) {
        String sql = """
                SELECT COALESCE(SUM(
                    CASE WHEN COALESCE(ended_at,last_checkpoint_at)>? AND started_at<?
                    THEN (MIN(COALESCE(ended_at,last_checkpoint_at),?) - MAX(started_at,?)) / 3600000.0
                    ELSE 0 END
                ),0) FROM sessions
                """;
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, from);
            p.setLong(2, to);
            p.setLong(3, to);
            p.setLong(4, from);
            try (ResultSet r = p.executeQuery()) {
                return r.getDouble(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not calculate playtime statistics.", e);
        }
    }

    public List<PlaySession> getRecentSessions(int limit) {
        List<PlaySession> out = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement p = c.prepareStatement("""
                     SELECT s.id,g.name,s.started_at,s.ended_at,s.duration_seconds,s.source
                     FROM sessions s JOIN games g ON g.id=s.game_id
                     ORDER BY s.started_at DESC LIMIT ?
                     """)) {
            p.setInt(1, limit);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    long ended = r.getLong("ended_at");
                    Long endedAt = r.wasNull() ? null : ended;
                    out.add(new PlaySession(r.getInt("id"), r.getString("name"),
                            r.getLong("started_at"), endedAt,
                            r.getDouble("duration_seconds"), r.getString("source")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load session history.", e);
        }
        return out;
    }

    public record ActiveSession(int id, int gameId, long startedAt, String gameName) {}
}
