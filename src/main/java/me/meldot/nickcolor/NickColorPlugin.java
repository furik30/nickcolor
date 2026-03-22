package me.meldot.nickcolor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Главный класс плагина NickColorPlugin.
 * Управляет инициализацией, базой данных, кешированием и взаимодействием с PlaceholderAPI.
 */
public class NickColorPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private NameTagManager nameTagManager;

    // Потокобезопасный кеш цветов игроков для быстрого доступа (запрашивается FlectonePulse асинхронно)
    private final Map<UUID, String> playerColors = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // Создание конфига по умолчанию
        saveDefaultConfig();
        debug("Конфиг по умолчанию создан.");

        // Инициализация менеджера базы данных (SQLite)
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        // Инициализация менеджера ников над головой
        nameTagManager = new NameTagManager(this);

        // Регистрация команд
        NickColorCommand cmdExecutor = new NickColorCommand(this);
        getCommand("nickcolor").setExecutor(cmdExecutor);
        getCommand("nickcolor").setTabCompleter(cmdExecutor);

        // Регистрация слушателей событий
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(nameTagManager, this);

        // Загрузка цветов для всех онлайн-игроков (полезно при /reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            databaseManager.loadColorAsync(p.getUniqueId()).thenAccept(color -> {
                Bukkit.getScheduler().runTask(this, () -> {
                    if (color != null && !color.isEmpty()) {
                        cachePlayerColor(p, color);
                    }
                    nameTagManager.updateNameTag(p, color);
                });
            });
        }

        // Регистрация PlaceholderAPI Expansion
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new NickColorExpansion(this).register();
            getLogger().info("PlaceholderAPI расширение успешно зарегистрировано!");
        } else {
            getLogger().warning("PlaceholderAPI не найден! Плейсхолдеры не будут работать.");
        }

        // Интеграция с SuperVanish / PremiumVanish
        if (Bukkit.getPluginManager().getPlugin("SuperVanish") != null || 
            Bukkit.getPluginManager().getPlugin("PremiumVanish") != null) {
            getServer().getPluginManager().registerEvents(new SuperVanishListener(this), this);
            getLogger().info("Интеграция с SuperVanish/PremiumVanish включена! Ники будут скрываться в ванише.");
        } else {
            getLogger().info("SuperVanish/PremiumVanish не найден! Ники не будут скрываться в ванише.");
        }

        getLogger().info("Плагин NickColor (SQLite, MiniMessage, TextDisplay) успешно загружен!");
    }

    @Override
    public void onDisable() {
        // Закрываем БД
        if (databaseManager != null) {
            databaseManager.close();
        }

        // Удаляем все TextDisplay сущности, чтобы они не оставались в мире
        if (nameTagManager != null) {
            nameTagManager.removeAllNameTags();
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
     * Возвращает менеджер ников над головой.
     * @return NameTagManager.
     */
    public NameTagManager getNameTagManager() {
        return nameTagManager;
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

        // Обновляем NameTag (TextDisplay)
        nameTagManager.updateNameTag(player, colorFormat);
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

        // Обновляем NameTag (сбрасываем на стандартный)
        nameTagManager.updateNameTag(player, null);
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