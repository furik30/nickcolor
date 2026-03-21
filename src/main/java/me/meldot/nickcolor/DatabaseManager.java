package me.meldot.nickcolor;

import org.bukkit.Bukkit;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Класс для работы с базой данных SQLite.
 * Обеспечивает асинхронное выполнение запросов для сохранения и загрузки цветов игроков.
 */
public class DatabaseManager {

    private final NickColorPlugin plugin;
    private final File databaseFile;
    private Connection connection;

    public DatabaseManager(NickColorPlugin plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "database.db");
    }

    /**
     * Инициализирует подключение к базе данных и создает таблицы, если они не существуют.
     */
    public void initialize() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // Загрузка драйвера SQLite
            Class.forName("org.sqlite.JDBC");

            // Подключение к БД
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());

            // Создание таблицы
            try (Statement statement = connection.createStatement()) {
                String createTableSQL = "CREATE TABLE IF NOT EXISTS player_colors (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "player_name VARCHAR(16)," +
                        "color_format TEXT" +
                        ");";
                statement.execute(createTableSQL);
                debug("Выполнен SQL-запрос: " + createTableSQL);
            }

            plugin.getLogger().info("База данных SQLite успешно инициализирована.");

        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("Не найден драйвер SQLite JDBC!");
            e.printStackTrace();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при подключении к базе данных SQLite!");
            e.printStackTrace();
        }
    }

    /**
     * Закрывает подключение к базе данных.
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Подключение к базе данных SQLite закрыто.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при закрытии подключения к базе данных!");
            e.printStackTrace();
        }
    }

    /**
     * Асинхронно сохраняет цвет игрока в базу данных.
     *
     * @param uuid Уникальный идентификатор игрока.
     * @param playerName Имя игрока (для удобства чтения БД).
     * @param colorFormat Формат цвета (строка MiniMessage).
     */
    public void saveColorAsync(UUID uuid, String playerName, String colorFormat) {
        CompletableFuture.runAsync(() -> {
            synchronized (connection) {
                String sql = "INSERT OR REPLACE INTO player_colors (uuid, player_name, color_format) VALUES (?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, playerName);
                    statement.setString(3, colorFormat);
                    statement.executeUpdate();
                    debug("Выполнен SQL-запрос: " + sql + " | Параметры: " + uuid + ", " + playerName + ", " + colorFormat);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Ошибка при сохранении цвета для игрока " + playerName, e);
                }
            }
        });
    }

    /**
     * Асинхронно загружает цвет игрока из базы данных.
     *
     * @param uuid Уникальный идентификатор игрока.
     * @return CompletableFuture, содержащий строку формата цвета (или null, если нет в БД).
     */
    public CompletableFuture<String> loadColorAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (connection) {
                String sql = "SELECT color_format FROM player_colors WHERE uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        debug("Выполнен SQL-запрос: " + sql + " | Параметры: " + uuid);
                        if (resultSet.next()) {
                            return resultSet.getString("color_format");
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Ошибка при загрузке цвета для UUID " + uuid, e);
                }
                return null;
            }
        });
    }

    /**
     * Асинхронно удаляет цвет игрока из базы данных (сброс цвета).
     *
     * @param uuid Уникальный идентификатор игрока.
     */
    public void deleteColorAsync(UUID uuid) {
        CompletableFuture.runAsync(() -> {
            synchronized (connection) {
                String sql = "DELETE FROM player_colors WHERE uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    statement.executeUpdate();
                    debug("Выполнен SQL-запрос: " + sql + " | Параметры: " + uuid);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Ошибка при удалении цвета для UUID " + uuid, e);
                }
            }
        });
    }

    /**
     * Вспомогательный метод для вывода отладочной информации, если дебаг включен в конфиге.
     *
     * @param message Сообщение для вывода.
     */
    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }
}