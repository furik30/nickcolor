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

    public void hideVanillaNameTag(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_TEAM_NAME);
        if (team != null && !team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    public void showVanillaNameTag(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_TEAM_NAME);
        if (team != null && team.hasEntry(player.getName())) {
            team.removeEntry(player.getName());
        }
    }

    public void updateNameTag(Player player, String colorFormat) {
        if (!plugin.getConfig().getBoolean("custom-nametags", true) || colorFormat == null || colorFormat.isEmpty()) {
            removeNameTag(player);
            showVanillaNameTag(player);
            return;
        }

        hideVanillaNameTag(player);
        TextDisplay display = playerDisplays.get(player.getUniqueId());

        if (display == null || display.isDead()) {
            display = player.getWorld().spawn(player.getLocation(), TextDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setDefaultBackground(true);
                
                // Применяем визуальный сдвиг вниз, не меняя физическую точку привязки
                entity.setTransformation(new Transformation(
                        new Vector3f(0f, PASSENGER_Y_OFFSET, 0f), // Сдвиг (Translation)
                        new AxisAngle4f(),                        // Левое вращение (нет)
                        new Vector3f(1f, 1f, 1f),                 // Масштаб (1.0)
                        new AxisAngle4f()                         // Правое вращение (нет)
                ));
            });

            player.hideEntity(plugin, display);
            playerDisplays.put(player.getUniqueId(), display);
        }

        // Устанавливаем текст
        Component nameComponent = (colorFormat != null && !colorFormat.isEmpty()) 
                ? ColorUtils.applyFormat(colorFormat, player.getName()) 
                : Component.text(player.getName());
                
        display.text(nameComponent);
        updateOpacity(display, player.isSneaking());

        // Сажаем дисплей на игрока, если он вдруг слез
        if (!player.getPassengers().contains(display)) {
            player.addPassenger(display);
        }
    }

    private void updateOpacity(TextDisplay display, boolean isSneaking) {
        if (isSneaking) {
            display.setTextOpacity((byte) 100);
            display.setSeeThrough(false); // Чтобы ник скрывался за блоками
        } else {
            display.setTextOpacity((byte) -1); // Дефолтная прозрачность (255)
            
            display.setSeeThrough(false); // Чтобы ник скрывался за блоками
        }
    }

    public void refreshVisibility(Player player, GameMode gameMode) {
        TextDisplay display = playerDisplays.get(player.getUniqueId());
        if (display == null || display.isDead()) return;

        boolean isSpectator = (gameMode == GameMode.SPECTATOR);
        boolean isVanished = isPlayerVanished(player);

        if (isSpectator || isVanished) {
            display.setViewRange(0f); // Полностью прячем сущность (не рендерится)
        } else {
            display.setViewRange(1.0f); // Стандартная видимость
        }
    }

    // Проверка ваниша через универсальную метадату (работает с 90% плагинов на ваниш)
    @Deprecated
    private boolean isPlayerVanished(Player player) {
        if (player.hasMetadata("vanished")) {
            for (MetadataValue meta : player.getMetadata("vanished")) {
                if (meta.asBoolean()) return true;
            }
        }
        return false;
    }

    public void removeNameTag(Player player) {
        TextDisplay display = playerDisplays.remove(player.getUniqueId());
        if (display != null && !display.isDead()) display.remove();
    }

    public void removeAllNameTags() {
        for (TextDisplay display : playerDisplays.values()) {
            if (display != null && !display.isDead()) display.remove();
        }
        playerDisplays.clear();

        // При выключении плагина восстанавливаем все ванильные ники (удаляя скрытую команду)
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_TEAM_NAME);
        if (team != null) team.unregister();
    }

    // --- Обработчики событий ---

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        TextDisplay display = playerDisplays.get(event.getPlayer().getUniqueId());
        if (display != null && !display.isDead()) {
            // Больше не нужно телепортировать! Изменяем только прозрачность.
            // Клиент сам плавно опустит пассажира при приседании игрока.
            updateOpacity(display, event.isSneaking());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        // Запускаем через тик, чтобы режим точно применился
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
            // Если тот, с кого слезают - игрок, и он мертв, то разрешаем отцепиться!
            if (event.getDismounted() instanceof Player) {
                Player player = (Player) event.getDismounted();
                if (player.isDead()) {
                    return; // Прерываем проверку, не отменяем ивент
                }
            }
            // В остальных случаях (баги клиента) не даем нику отвалиться
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        TextDisplay display = playerDisplays.get(player.getUniqueId());
        if (display != null && !display.isDead()) {
            // Ник сам отцепится благодаря фиксу выше. 
            // Нам остается только сделать его невидимым, чтобы он не висел в воздухе над местом смерти.
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
                    // Сажаем обратно на голову
                    player.addPassenger(display);
                    // Пересчитываем видимость (вдруг он возродился в спектаторе)
                    refreshVisibility(player, player.getGameMode()); 
                }
            }, 1L);
        }
    }
}