package me.meldot.nickcolor;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Слушатель событий чата (PaperMC AsyncChatEvent).
 * Заменяет стандартное отображение ника игрока в чате на форматированное.
 */
public class ChatListener implements Listener {

    private final NickColorPlugin plugin;

    public ChatListener(NickColorPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Форматирует сообщение чата, применяя цвет/градиент к имени игрока.
     *
     * @param event PaperMC AsyncChatEvent.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String colorFormat = plugin.getPlayerColor(player);

        if (colorFormat != null && !colorFormat.isEmpty()) {
            // Форматируем имя игрока
            Component displayName = ColorUtils.applyFormat(colorFormat, player.getName());

            // Создаем новый рендерер чата (Paper API)
            event.renderer((source, sourceDisplayName, message, viewer) -> {
                // Возвращаем формат: <ЦветнойНик>: <Сообщение>
                // Можно добавить настройку формата чата в config.yml, если это необходимо
                return displayName
                        .append(Component.text(": "))
                        .append(message);
            });

            debug("Применен цвет " + colorFormat + " к нику " + player.getName() + " в чате.");
        }
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