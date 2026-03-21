package me.meldot.nickcolor;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Главный класс плагина NickColorPlugin.
 * Управляет инициализацией, базой данных, кешированием и взаимодействием с PlaceholderAPI.
 */
public class NickColorPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;

    // Потокобезопасный кеш цветов игроков для быстрого доступа (запрашивается FlectonePulse асинхронно)
    private final Map<UUID, String> playerColors = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // Создание конфига по умолчанию
        saveDefaultConfig();

        // Инициализация менеджера базы данных (SQLite)
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        // Регистрация команд
        NickColorCommand cmdExecutor = new NickColorCommand(this);
        getCommand("nickcolor").setExecutor(cmdExecutor);
        getCommand("nickcolor").setTabCompleter(cmdExecutor);

        // Регистрация слушателей событий
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);

        // Загрузка цветов для всех онлайн-игроков (полезно при /reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            databaseManager.loadColorAsync(p.getUniqueId()).thenAccept(color -> {
                if (color != null && !color.isEmpty()) {
                    cachePlayerColor(p, color);
                }
            });
        }

        // Регистрация PlaceholderAPI Expansion
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new NickColorExpansion(this).register();
            getLogger().info("PlaceholderAPI расширение успешно зарегистрировано!");
        } else {
            getLogger().warning("PlaceholderAPI не найден! Плейсхолдеры не будут работать.");
        }

        getLogger().info("Плагин NickColor (SQLite, MiniMessage) успешно загружен!");
    }

    @Override
    public void onDisable() {
        // Закрываем БД
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("Плагин NickColor выключен.");
    }

    /**
     * Возвращает экземпляр менеджера базы данных.
     * @return DatabaseManager.
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Возвращает цвет игрока (формат MiniMessage).
     *
     * @param player Игрок.
     * @return Строка формата цвета (например, <#FF5555>). Может быть null.
     */
    public String getPlayerColor(Player player) {
        return playerColors.get(player.getUniqueId());
    }

    /**
     * Устанавливает цвет игрока (сохраняет в БД и кеш).
     *
     * @param player Игрок.
     * @param colorFormat Формат цвета (например, <#FF5555> или <gradient:#FF0000:#00FF00>).
     */
    public void setPlayerColor(Player player, String colorFormat) {
        // Сохраняем в кеш
        playerColors.put(player.getUniqueId(), colorFormat);

        // Сохраняем в БД (асинхронно)
        databaseManager.saveColorAsync(player.getUniqueId(), player.getName(), colorFormat);
    }

    /**
     * Кеширует цвет игрока без сохранения в БД (используется при загрузке).
     *
     * @param player Игрок.
     * @param colorFormat Формат цвета.
     */
    public void cachePlayerColor(Player player, String colorFormat) {
        playerColors.put(player.getUniqueId(), colorFormat);
    }

    /**
     * Удаляет цвет игрока из кеша.
     *
     * @param player Игрок.
     */
    public void removePlayerColorFromCache(Player player) {
        playerColors.remove(player.getUniqueId());
    }

    /**
     * Сбрасывает цвет игрока (удаляет из БД и кеша).
     *
     * @param player Игрок.
     */
    public void resetPlayerColor(Player player) {
        playerColors.remove(player.getUniqueId());
        databaseManager.deleteColorAsync(player.getUniqueId());
    }

    /**
     * Вспомогательный метод для отладки.
     */
    private void debug(String message) {
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("[DEBUG] " + message);
        }
    }
}