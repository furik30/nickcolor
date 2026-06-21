package me.meldot.nickcolor;

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
        // Через 1 тик, чтобы SuperVanish успел выставить метадату vanished
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.getNameTagManager().updateNameTag(player, null);
        });

        // Асинхронная загрузка цвета
        plugin.getDatabaseManager().loadColorAsync(player.getUniqueId()).thenAccept(colorFormat -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (colorFormat != null && !colorFormat.isEmpty()) {
                    plugin.cachePlayerColor(player, colorFormat);
                    debug("Загружен цвет " + colorFormat + " для игрока " + player.getName());
                }
                // updateNameTag внутри уже вызывает refreshVisibility — ваниш/спектатор учтутся
                plugin.getNameTagManager().updateNameTag(player, colorFormat);
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

        // Удаляем TextDisplay (ник над головой)
        plugin.getNameTagManager().removeNameTag(player);

        // Задерживаем очистку кеша цвета на 3 секунды (60 тиков),
        // чтобы плагины сообщений об уходе успели прочесть плейсхолдер цвета игрока.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                plugin.removePlayerColorFromCache(player);
                debug("Данные игрока " + player.getName() + " выгружены из памяти.");
            }
        }, 60L);
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