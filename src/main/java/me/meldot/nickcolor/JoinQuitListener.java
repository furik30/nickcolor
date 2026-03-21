package me.meldot.nickcolor;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Слушатель событий входа и выхода игроков на сервер.
 */
public class JoinQuitListener implements Listener {

    private final NickColorPlugin plugin;

    public JoinQuitListener(NickColorPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Обрабатывает вход игрока.
     * Асинхронно загружает цвет из базы данных и применяет его.
     *
     * @param event Событие PlayerJoinEvent.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Асинхронная загрузка цвета
        plugin.getDatabaseManager().loadColorAsync(player.getUniqueId()).thenAccept(colorFormat -> {
            if (colorFormat != null && !colorFormat.isEmpty()) {
                // Если цвет найден, загружаем его в кеш плагина
                plugin.cachePlayerColor(player, colorFormat);
                debug("Загружен цвет " + colorFormat + " для игрока " + player.getName());
            } else {
                debug("Цвет для игрока " + player.getName() + " не найден в БД.");
            }
        });
    }

    /**
     * Обрабатывает выход игрока.
     * Очищает кеш цвета игрока для экономии памяти.
     *
     * @param event Событие PlayerQuitEvent.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.removePlayerColorFromCache(player);
        debug("Цвет для " + player.getName() + " удален из кеша при выходе.");
    }

    /**
     * Вспомогательный метод для отладочного вывода.
     *
     * @param message Сообщение.
     */
    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }
}