package me.meldot.nickcolor;

import de.myzelyam.api.vanish.PlayerVanishStateChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Слушатель событий от плагинов SuperVanish / PremiumVanish.
 */
public class SuperVanishListener implements Listener {

    private final NickColorPlugin plugin;

    public SuperVanishListener(NickColorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVanishStateChange(PlayerVanishStateChangeEvent event) {
        Player player = Bukkit.getPlayer(event.getUUID());
        if (player != null && player.isOnline()) {
            // Запускаем через 1 тик, чтобы метадата ваниша успела обновиться
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getNameTagManager().refreshVisibility(player, player.getGameMode());
            });
        }
    }
}