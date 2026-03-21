package me.meldot.nickcolor;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Главный класс плагина NickColorPlugin.
 * Управляет инициализацией, конфигурацией, командами, событиями и Scoreboard Teams.
 */
public class NickColorPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;

    // Кеш цветов игроков для быстрого доступа (чтобы не запрашивать БД при каждом сообщении в чат)
    private final Map<UUID, String> playerColors = new HashMap<>();

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
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        // Загрузка цветов для всех онлайн-игроков (полезно при /reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            databaseManager.loadColorAsync(p.getUniqueId()).thenAccept(color -> {
                if (color != null && !color.isEmpty()) {
                    Bukkit.getScheduler().runTask(this, () -> applyColorFormatToPlayer(p, color));
                }
            });
        }

        getLogger().info("Плагин NickColor (SQLite, MiniMessage) успешно загружен!");
    }

    @Override
    public void onDisable() {
        // Закрываем БД
        if (databaseManager != null) {
            databaseManager.close();
        }

        // Очищаем Scoreboard Teams для всех онлайн игроков
        for (Player p : Bukkit.getOnlinePlayers()) {
            removeScoreboardTeam(p);
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
     * Устанавливает цвет игрока (сохраняет в БД, кеш, применяет к Tab и NameTag).
     *
     * @param player Игрок.
     * @param colorFormat Формат цвета (например, <#FF5555> или <gradient:#FF0000:#00FF00>).
     */
    public void setPlayerColor(Player player, String colorFormat) {
        // Сохраняем в кеш
        playerColors.put(player.getUniqueId(), colorFormat);

        // Сохраняем в БД (асинхронно)
        databaseManager.saveColorAsync(player.getUniqueId(), player.getName(), colorFormat);

        // Применяем к игроку (TabList, NameTag)
        applyColorFormatToPlayer(player, colorFormat);
    }

    /**
     * Применяет цвет (или градиент) к игроку: в TabList и NameTag (Scoreboard).
     *
     * @param player Игрок.
     * @param colorFormat Формат цвета (MiniMessage).
     */
    public void applyColorFormatToPlayer(Player player, String colorFormat) {
        // Кешируем (особенно полезно при загрузке при входе)
        playerColors.put(player.getUniqueId(), colorFormat);

        // Формируем компонент имени
        Component formattedName = ColorUtils.applyFormat(colorFormat, player.getName());

        // Применяем к TabList
        player.playerListName(formattedName);

        // Применяем к NameTag (Scoreboard Teams)
        updateScoreboardTeam(player, colorFormat);
    }

    /**
     * Сбрасывает цвет игрока (удаляет из БД, кеша и восстанавливает дефолт).
     *
     * @param player Игрок.
     */
    public void resetPlayerColor(Player player) {
        playerColors.remove(player.getUniqueId());
        databaseManager.deleteColorAsync(player.getUniqueId());

        // Сброс TabList к дефолту
        player.playerListName(null);

        // Удаление Scoreboard Team
        removeScoreboardTeam(player);
    }

    /**
     * Создает или обновляет Scoreboard Team для уникального NameTag.
     * Чтобы цвет применялся к NameTag (над головой), мы ставим префикс,
     * который будет раскрашивать имя. Для этого используем метод prefix()
     * и передаем только тег формата цвета (например, "<#FF5555>").
     * Но в PaperMC 1.21+ Team.prefix/suffix принимают Component.
     * К счастью, если префикс сам по себе имеет цвет, он должен применяться к имени.
     * Если не сработает (градиенты могут работать странно), мы можем полностью
     * менять Team prefix и color (стандартный Bukkit NameTag API не позволяет менять
     * имя на градиентное напрямую, префикс - лучший способ).
     */
    private void updateScoreboardTeam(Player player, String colorFormat) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "NC_" + player.getUniqueId().toString().substring(0, 12); // Уникальное имя (макс. 16 симв в старых версиях, но в новых норм)

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        // Префикс - это тег форматирования.
        // Чтобы сработало раскрашивание самого ника (если это не градиент, а сплошной цвет),
        // можно использовать team.color(). Но для градиентов team.color() не поможет.
        // Поэтому мы ставим префикс, который содержит начало градиента или цвета.
        // Но префикс это Component. Обычный Component префикс просто прибавится перед ником.
        // К сожалению, Bukkit Scoreboard Team prefix не может раскрашивать сам ник градиентом
        // в новых версиях (компоненты изолированы).
        // Поэтому, лучший способ изменить цвет НАД ГОЛОВОЙ - это вообще скрыть стандартный ник и написать его префиксом и суффиксом,
        // ИЛИ смириться с тем, что градиент будет только в префиксе.
        // Попробуем установить Component цвета в prefix. В некоторых клиентах это красит и ник.

        // В PaperMC есть Player#customName(), но он не всегда отображается без плагинов типа TAB.
        // Для Scoreboard:
        Component coloredName = ColorUtils.applyFormat(colorFormat, player.getName());

        // Устанавливаем префикс (если хотим, чтобы префикс был пуст, а ник цветным -
        // это сложно без пакетов. Попробуем установить префиксом пустую строку с цветом).
        team.prefix(ColorUtils.format(colorFormat));

        // ВАЖНО: Ванильный клиент не поддерживает градиенты в самом имени (Scoreboard Teams
        // поддерживают только 16 базовых цветов через team.color(NamedTextColor)).
        // Префикс может быть с градиентом, но само имя - нет.
        // Чтобы обойти это: мы можем очистить suffix и покрасить prefix, но имя все равно будет белым.
        // В рамках API Bukkit/Paper нет идеального способа покрасить NameTag градиентом без пакетов (ProtocolLib).
        // Но мы делаем лучшее, что позволяет API: устанавливаем префикс, который содержит цвет/градиент.
        // В современных клиентах (1.20+) префикс с MiniMessage может окрасить все последующее имя.

        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }

        debug("Обновлена Team " + teamName + " для игрока " + player.getName() + " (формат: " + colorFormat + ")");
    }

    /**
     * Удаляет Scoreboard Team для игрока (при выходе или сбросе цвета).
     *
     * @param player Игрок.
     */
    public void removeScoreboardTeam(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "NC_" + player.getUniqueId().toString().substring(0, 12);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
            debug("Удалена Team " + teamName + " для игрока " + player.getName());
        }
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