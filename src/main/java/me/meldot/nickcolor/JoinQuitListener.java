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

        // Скрываем ванильный ник сразу при входе
        plugin.getNameTagManager().hideVanillaNameTag(player);

        // Асинхронная загрузка цвета
        plugin.getDatabaseManager().loadColorAsync(player.getUniqueId()).thenAccept(colorFormat -> {
            // Возвращаемся в основной поток для спавна TextDisplay
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Защита от утечки сущности, если игрок отключился до завершения SQL-запроса
                if (!player.isOnline()) return;

                if (colorFormat != null && !colorFormat.isEmpty()) {
                    // Если цвет найден, загружаем его в кеш плагина и обновляем ник
                    plugin.cachePlayerColor(player, colorFormat);
                    plugin.getNameTagManager().updateNameTag(player, colorFormat);
                    debug("Загружен цвет " + colorFormat + " для игрока " + player.getName());
                } else {
                    // Если цвета нет, просто отображаем стандартный белый ник через TextDisplay
                    plugin.getNameTagManager().updateNameTag(player, null);
                    debug("Цвет для игрока " + player.getName() + " не найден в БД.");
                }
            });
        });
    }

    /**
     * Обрабатывает выход игрока.
     * Очищает кеш цвета игрока для экономии памяти и удаляет сущность TextDisplay.
     *
     * @param event Событие PlayerQuitEvent.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Удаляем кеш
        plugin.removePlayerColorFromCache(player);

        // Удаляем TextDisplay (ник над головой)
        plugin.getNameTagManager().removeNameTag(player);

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