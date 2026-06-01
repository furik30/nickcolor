package me.meldot.nickcolor;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Управляет никами над головой (NameTag) с помощью связки ArmorStand + TextDisplay.
 * Поддерживает уступку места для GSit и полностью блокирует мигание ванильных ников.
 */
public class NameTagManager implements Listener {

    private final NickColorPlugin plugin;
    private final Map<UUID, NameTagEntities> playerDisplays = new HashMap<>();
    private final Set<UUID> allowedDismounts = new HashSet<>();
    private static final String HIDDEN_TEAM_NAME = "NC_HIDDEN_NAMETAGS";

    // Сдвиг по Y для корректировки высоты пассажира. 
    private static final float PASSENGER_Y_OFFSET = 0.3f; 

    /**
     * Вспомогательный класс для хранения связки сущностей.
     */
    private static class NameTagEntities {
        final ArmorStand stand;
        final TextDisplay display;

        NameTagEntities(ArmorStand stand, TextDisplay display) {
            this.stand = stand;
            this.display = display;
        }

        boolean isDead() {
            return stand.isDead() || display.isDead();
        }

        void remove() {
            if (!stand.isDead()) stand.remove();
            if (!display.isDead()) display.remove();
        }
    }

    public NameTagManager(NickColorPlugin plugin) {
        this.plugin = plugin;
        setupHiddenTeam();
        startPassengerTrafficController();
        startVanillaTagBlocker();
    }

    /**
     * Умный контроллер, который уступает место другим пассажирам (например, GSit).
     */
    private void startPassengerTrafficController() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, NameTagEntities> entry : playerDisplays.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) continue;

                NameTagEntities entities = entry.getValue();
                if (entities.isDead()) continue;

                // Проверяем, есть ли другие пассажиры (например, игрок от GSit)
                boolean hasForeignPassenger = player.getPassengers().stream()
                        .anyMatch(p -> !p.equals(entities.stand));

                // Всегда возвращаем стойку на место (если её сбили)
                if (!player.getPassengers().contains(entities.stand) && !player.isDead()) {
                    player.addPassenger(entities.stand);
                    refreshVisibility(player, player.getGameMode());
                }
                
                // Текст всегда должен сидеть на стойке
                if (!entities.stand.getPassengers().contains(entities.display)) {
                    entities.stand.addPassenger(entities.display);
                }

                // Плавное смещение ника вверх, если кто-то сидит на голове
                float targetOffset = hasForeignPassenger ? PASSENGER_Y_OFFSET : PASSENGER_Y_OFFSET;
                updateDisplayOffset(entities.display, targetOffset);
            }
        }, 1L, 1L);
    }

    private void updateDisplayOffset(TextDisplay display, float targetY) {
        Transformation current = display.getTransformation();
        if (Math.abs(current.getTranslation().y - targetY) > 0.01f) {
            display.setInterpolationDuration(1);
            display.setInterpolationDelay(0);
            display.setTransformation(new Transformation(
                    new Vector3f(0f, targetY, 0f), 
                    current.getLeftRotation(),                        
                    current.getScale(),                 
                    current.getRightRotation()                         
            ));
        }
    }

    private void startVanillaTagBlocker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!playerDisplays.containsKey(player.getUniqueId())) continue;

                blockVanillaNameTag(player, mainScoreboard);
                
                for (Player observer : Bukkit.getOnlinePlayers()) {
                    blockVanillaNameTag(player, observer.getScoreboard());
                }
            }
        }, 5L, 5L); 
    }

    private void blockVanillaNameTag(Player player, Scoreboard scoreboard) {
        if (scoreboard == null) return;
        Team team = scoreboard.getEntryTeam(player.getName());
        
        if (team == null) {
            Team hiddenTeam = scoreboard.getTeam(HIDDEN_TEAM_NAME);
            if (hiddenTeam != null && !hiddenTeam.hasEntry(player.getName())) {
                hiddenTeam.addEntry(player.getName());
            }
        } else {
            if (team.getOption(Team.Option.NAME_TAG_VISIBILITY) != Team.OptionStatus.NEVER) {
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            }
        }
    }

    private void setupHiddenTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(HIDDEN_TEAM_NAME);
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
    }

    public void setVanillaNameTagVisible(Player target, boolean visible) {
        Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = mainScoreboard.getTeam(HIDDEN_TEAM_NAME);
        if (team == null) return;

        if (visible) {
            if (team.hasEntry(target.getName())) team.removeEntry(target.getName());
        } else {
            if (!team.hasEntry(target.getName())) team.addEntry(target.getName());
        }
    }

    public void updateNameTag(Player player, String colorFormat) {
        boolean customEnabled = plugin.getConfig().getBoolean("nametags.custom-enabled", true);
        boolean vanillaSeeThrough = plugin.getConfig().getBoolean("nametags.vanilla-see-through", true);
        
        boolean hasColor = customEnabled && colorFormat != null && !colorFormat.isEmpty();
        boolean forceTextDisplay = !vanillaSeeThrough;

        if (!hasColor && !forceTextDisplay) {
            removeNameTag(player);
            return;
        }        
        
        NameTagEntities entities = playerDisplays.get(player.getUniqueId());

        if (entities == null || entities.isDead()) {
            if (entities != null) entities.remove();

            // 1. Невидимая прокладка (ArmorStand)
            ArmorStand stand = player.getWorld().spawn(player.getLocation(), ArmorStand.class, entity -> {
                entity.setPersistent(false);
                entity.setVisible(false);
                entity.setMarker(true); 
                entity.setSmall(true); // Small ставит центр чуть ниже, что дает нам простор для оффсета
                entity.setBasePlate(false);
                entity.setGravity(false);
            });

            // 2. Сам текст
            TextDisplay display = player.getWorld().spawn(player.getLocation(), TextDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setDefaultBackground(true);
                
                // Применяем визуальный сдвиг вверх, чтобы приподнять ник от ArmorStand
                entity.setTransformation(new Transformation(
                        new Vector3f(0f, PASSENGER_Y_OFFSET, 0f), 
                        new AxisAngle4f(),                        
                        new Vector3f(1f, 1f, 1f),                 
                        new AxisAngle4f()                         
                ));
            });

            player.hideEntity(plugin, stand);
            player.hideEntity(plugin, display);
            
            entities = new NameTagEntities(stand, display);
            playerDisplays.put(player.getUniqueId(), entities);
        }

        Component nameComponent = hasColor
                ? ColorUtils.applyFormat(colorFormat, player.getName()) 
                : Component.text(player.getName());
                
        entities.display.text(nameComponent);
        updateOpacity(entities.display, player.isSneaking());
    }

    private void updateOpacity(TextDisplay display, boolean isSneaking) {
        if (isSneaking) {
            display.setTextOpacity((byte) 100);
            display.setSeeThrough(false);
        } else {
            display.setTextOpacity((byte) 255);
            display.setSeeThrough(true);
        }
    }

    public void refreshVisibility(Player player, GameMode gameMode) {
        NameTagEntities entities = playerDisplays.get(player.getUniqueId());
        if (entities == null || entities.isDead()) return;

        boolean isSpectator = (gameMode == GameMode.SPECTATOR);
        boolean isVanished = isPlayerVanished(player);

        if (isSpectator || isVanished) {
            entities.display.setViewRange(0f);
        } else {
            entities.display.setViewRange(1.0f);
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

    public void removeNameTag(Player player) {
        NameTagEntities entities = playerDisplays.remove(player.getUniqueId());
        if (entities != null) {
            entities.remove();
        }
        setVanillaNameTagVisible(player, true);
    }

    public void removeAllNameTags() {
        for (NameTagEntities entities : playerDisplays.values()) {
            if (entities != null) entities.remove();
        }
        playerDisplays.clear();

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_TEAM_NAME);
        if (team != null) team.unregister();
        
        for (Player observer : Bukkit.getOnlinePlayers()) {
            Team observerTeam = observer.getScoreboard().getTeam(HIDDEN_TEAM_NAME);
            if (observerTeam != null) observerTeam.unregister();
        }
    }

    // --- Обработчики событий ---

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        NameTagEntities entities = playerDisplays.get(event.getPlayer().getUniqueId());
        if (entities != null && !entities.isDead()) {
            updateOpacity(entities.display, event.isSneaking());
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
        NameTagEntities entities = playerDisplays.get(player.getUniqueId());
        if (entities != null && !entities.isDead()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    if (!entities.stand.getPassengers().contains(entities.display)) {
                        entities.stand.addPassenger(entities.display);
                    }
                    if (!player.getPassengers().contains(entities.stand)) {
                        player.addPassenger(entities.stand);
                    }
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        // Защита матрешки: TextDisplay не может слезть с ArmorStand
        for (NameTagEntities entities : playerDisplays.values()) {
            if (event.getEntity().equals(entities.display)) {
                event.setCancelled(true);
                return;
            }
        }
        
        // Разрешаем ArmorStand слезть с игрока ТОЛЬКО если это запросил наш плагин для GSit
        if (event.getEntity() instanceof ArmorStand stand) {
            for (Map.Entry<UUID, NameTagEntities> entry : playerDisplays.entrySet()) {
                if (stand.equals(entry.getValue().stand)) {
                    if (event.getDismounted() instanceof Player player) {
                        if (player.isDead()) return; 
                        
                        if (allowedDismounts.remove(player.getUniqueId())) {
                            return;
                        }
                    }
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        NameTagEntities entities = playerDisplays.get(player.getUniqueId());
        if (entities != null && !entities.isDead()) {
            entities.display.setViewRange(0f); 
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        NameTagEntities entities = playerDisplays.get(player.getUniqueId());
        if (entities != null && !entities.isDead()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    if (!player.getPassengers().contains(entities.stand)) {
                        player.addPassenger(entities.stand);
                    }
                    refreshVisibility(player, player.getGameMode()); 
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Player target) {
            NameTagEntities entities = playerDisplays.get(target.getUniqueId());
            if (entities != null && target.getPassengers().contains(entities.stand)) {
                // Временно освобождаем место ДО того, как GSit проверит занятость игрока. Ник при этом не исчезает, а наш таймер вернет стойку обратно в следующем тике!
                allowedDismounts.add(target.getUniqueId());
                target.removePassenger(entities.stand);
            }
        }
    }
}