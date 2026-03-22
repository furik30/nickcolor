package me.meldot.nickcolor;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Управляет никами над головой (NameTag) с помощью сущностей TextDisplay.
 */
public class NameTagManager implements Listener {

    private final NickColorPlugin plugin;
    private final Map<UUID, TextDisplay> playerDisplays = new HashMap<>();
    private static final String HIDDEN_TEAM_NAME = "NC_HIDDEN_NAMETAGS";

    // Сдвиг по Y для корректировки высоты пассажира. 
    // Значение отрицательное, чтобы опустить ник вниз к голове.
    private static final float PASSENGER_Y_OFFSET = 0.3f; 

    public NameTagManager(NickColorPlugin plugin) {
        this.plugin = plugin;
        setupHiddenTeam();
    }

    private void setupHiddenTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(HIDDEN_TEAM_NAME);
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
    }

    /**
     * Универсальный метод для скрытия/показа ванильного ника.
     */
    public void setVanillaNameTagVisible(Player player, boolean visible) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_TEAM_NAME);
        if (team == null) return;

        if (visible) {
            if (team.hasEntry(player.getName())) team.removeEntry(player.getName());
        } else {
            if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
        }
    }

    public void updateNameTag(Player player, String colorFormat) {
        // Читаем настройки
        boolean customEnabled = plugin.getConfig().getBoolean("nametags.custom-enabled", true);
        boolean vanillaSeeThrough = plugin.getConfig().getBoolean("nametags.vanilla-see-through", true);
        
        boolean hasColor = customEnabled && colorFormat != null && !colorFormat.isEmpty();
        boolean forceTextDisplay = !vanillaSeeThrough;

        // Если цвет не нужен и мы разрешаем просвечивание ванильного ника - возвращаем дефолт
        if (!hasColor && !forceTextDisplay) {
            removeTextDisplayOnly(player);
            setVanillaNameTagVisible(player, true);
            return;
        }        
        setVanillaNameTagVisible(player, false);
        TextDisplay display = playerDisplays.get(player.getUniqueId());

        if (display == null || display.isDead()) {
            display = player.getWorld().spawn(player.getLocation(), TextDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setDefaultBackground(true);
                
                // Применяем визуальный сдвиг вниз
                entity.setTransformation(new Transformation(
                        new Vector3f(0f, PASSENGER_Y_OFFSET, 0f), 
                        new AxisAngle4f(),                        
                        new Vector3f(1f, 1f, 1f),                 
                        new AxisAngle4f()                         
                ));
            });

            player.hideEntity(plugin, display); // Игрок не должен видеть свой TextDisplay
            playerDisplays.put(player.getUniqueId(), display);
        }

        // Форматируем текст
        Component nameComponent = hasColor
                ? ColorUtils.applyFormat(colorFormat, player.getName()) 
                : Component.text(player.getName());
                
        display.text(nameComponent);
        updateOpacity(display, player.isSneaking());

        // Сажаем дисплей на игрока
        if (!player.getPassengers().contains(display)) {
            player.addPassenger(display);
        }
    }

    private void updateOpacity(TextDisplay display, boolean isSneaking) {
        if (isSneaking) {
            display.setTextOpacity((byte) 100);
            display.setSeeThrough(false);
        } else {
            display.setTextOpacity((byte) -1);
            display.setSeeThrough(false);
        }
    }

    public void refreshVisibility(Player player, GameMode gameMode) {
        TextDisplay display = playerDisplays.get(player.getUniqueId());
        if (display == null || display.isDead()) return;

        boolean isSpectator = (gameMode == GameMode.SPECTATOR);
        boolean isVanished = isPlayerVanished(player);

        if (isSpectator || isVanished) {
            display.setViewRange(0f);
        } else {
            display.setViewRange(1.0f);
        }
    }

    @Deprecated
    private boolean isPlayerVanished(Player player) {
        if (player.hasMetadata("vanished")) {
            for (MetadataValue meta : player.getMetadata("vanished")) {
                if (meta.asBoolean()) return true;
            }
        }
        return false;
    }

    /**
     * Удаляет TextDisplay сущность, не трогая скорборд.
     */
    private void removeTextDisplayOnly(Player player) {
        TextDisplay display = playerDisplays.remove(player.getUniqueId());
        if (display != null && !display.isDead()) display.remove();
    }

    /**
     * Полностью удаляет кастомный ник игрока (используется при выходе).
     */
    public void removeNameTag(Player player) {
        removeTextDisplayOnly(player);
        setVanillaNameTagVisible(player, true);
    }

    public void removeAllNameTags() {
        for (TextDisplay display : playerDisplays.values()) {
            if (display != null && !display.isDead()) display.remove();
        }
        playerDisplays.clear();

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_TEAM_NAME);
        if (team != null) team.unregister();
    }

    // --- Обработчики событий (без изменений) ---

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        TextDisplay display = playerDisplays.get(event.getPlayer().getUniqueId());
        if (display != null && !display.isDead()) {
            updateOpacity(display, event.isSneaking());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshVisibility(event.getPlayer(), event.getNewGameMode());
        });
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        TextDisplay display = playerDisplays.get(player.getUniqueId());
        if (display != null && !display.isDead()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.addPassenger(display);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof TextDisplay && playerDisplays.containsValue(event.getEntity())) {
            if (event.getDismounted() instanceof Player) {
                Player player = (Player) event.getDismounted();
                if (player.isDead()) {
                    return; 
                }
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        TextDisplay display = playerDisplays.get(player.getUniqueId());
        if (display != null && !display.isDead()) {
            display.setViewRange(0f); 
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        TextDisplay display = playerDisplays.get(player.getUniqueId());
        if (display != null && !display.isDead()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.addPassenger(display);
                    refreshVisibility(player, player.getGameMode()); 
                }
            }, 1L);
        }
    }
}