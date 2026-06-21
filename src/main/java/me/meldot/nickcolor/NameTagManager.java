package me.meldot.nickcolor;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
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
 * Управляет никами над головой (NameTag) с помощью связки Interaction + TextDisplay.
 * Полностью совместим с GSit и оптимизирован под высокую производительность.
 */
public class NameTagManager implements Listener {

    private final NickColorPlugin plugin;
    private final Map<UUID, NameTagEntities> playerDisplays = new HashMap<>();
    private final Set<UUID> allowedDismounts = new HashSet<>();
    private static final String HIDDEN_TEAM_NAME = "NC_HIDDEN_NAMETAGS";

    // Сдвиг по Y для корректировки высоты пассажира. 
    /**
     * Вспомогательный класс для хранения связки сущностей.
     */
    private static class NameTagEntities {
        final Interaction mount;
        final TextDisplay display;

        NameTagEntities(Interaction mount, TextDisplay display) {
            this.mount = mount;
            this.display = display;
        }

        boolean isDead() {
            return mount.isDead() || display.isDead();
        }

        void remove() {
            if (!mount.isDead()) mount.remove();
            if (!display.isDead()) display.remove();
        }
    }

    public NameTagManager(NickColorPlugin plugin) {
        this.plugin = plugin;
        setupHiddenTeam();
        startMountMaintainer();
    }

    /**
     * Контроллер удержания сущностей. Работает каждый тик, но выполняет только атомарные проверки.
    */
    private void startMountMaintainer() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            java.util.List<Player> toRemove = new java.util.ArrayList<>();
            for (Map.Entry<UUID, NameTagEntities> entry : playerDisplays.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) continue;

                NameTagEntities entities = entry.getValue();
                if (entities.isDead()) continue;

                boolean shouldHide = player.isDead()
                        || player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                        || isPlayerVanished(player)
                        || player.hasPotionEffect(PotionEffectType.INVISIBILITY);

                if (shouldHide) {
                    toRemove.add(player);
                    continue;
                }

                // ДОБАВЛЕНО: Не возвращаем mount, если плагин сейчас уступает место для GSit
                if (!player.getPassengers().contains(entities.mount)) {
                    if (!allowedDismounts.contains(player.getUniqueId())) {
                        player.addPassenger(entities.mount);
                    }
                }
                
                // Текст всегда должен сидеть на Interaction
                if (!entities.mount.getPassengers().contains(entities.display)) {
                    entities.mount.addPassenger(entities.display);
                }
            }
            for (Player p : toRemove) {
                removeNameTag(p);
            }
        }, 1L, 1L);
    }

    private void setupHiddenTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(HIDDEN_TEAM_NAME);
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.setCanSeeFriendlyInvisibles(false);
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
        boolean enable = plugin.getConfig().getBoolean("nametags.enable", true);
        boolean customEnabled = plugin.getConfig().getBoolean("nametags.custom-enabled", true);
        boolean vanillaSeeThrough = plugin.getConfig().getBoolean("nametags.vanilla-see-through", true);
        
        if (!enable) {
            NameTagEntities entities = playerDisplays.remove(player.getUniqueId());
            if (entities != null) entities.remove();
            setVanillaNameTagVisible(player, false);
            return;
        }

        boolean hasColor = customEnabled && colorFormat != null && !colorFormat.isEmpty();
        boolean forceTextDisplay = !vanillaSeeThrough;

        if (!hasColor && !forceTextDisplay) {
            removeNameTag(player);
            return;
        }        

        boolean shouldHide = player.isDead()
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || isPlayerVanished(player)
                || player.hasPotionEffect(PotionEffectType.INVISIBILITY);

        if (shouldHide) {
            removeNameTag(player);
            return;
        }
        
        NameTagEntities entities = playerDisplays.get(player.getUniqueId());

        if (entities == null || entities.isDead() || !entities.mount.getWorld().equals(player.getWorld())) {
            if (entities != null) entities.remove();

            // 1. Создаем невидимый хитбокс
            float passengerYOffset = (float) plugin.getConfig().getDouble("nametags.passenger-y-offset");
            Interaction mount = player.getWorld().spawn(player.getLocation(), Interaction.class, entity -> {
                entity.setPersistent(false);
                entity.setInteractionWidth(0f); 
                entity.setInteractionHeight(passengerYOffset); 
            });

            // 2. Создаем сам текст
            TextDisplay display = player.getWorld().spawn(player.getLocation(), TextDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setDefaultBackground(true);
                
                entity.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(),
                        new Vector3f(1f, 1f, 1f),
                        new AxisAngle4f()));
            });

            player.hideEntity(plugin, mount);
            player.hideEntity(plugin, display);
            
            entities = new NameTagEntities(mount, display);
            playerDisplays.put(player.getUniqueId(), entities);

            // Сразу скрываем стандартный ник в основном скорборде сервера
            setVanillaNameTagVisible(player, false);
        }

        Component nameComponent = hasColor
                ? ColorUtils.applyFormat(colorFormat, player.getName()) 
                : Component.text(player.getName());
                
        entities.display.text(nameComponent);
        updateOpacity(entities.display, player.isSneaking());
    }

    private void updateOpacity(TextDisplay display, boolean isSneaking) {
        display.setSeeThrough(false);
        display.setTextOpacity(isSneaking ? (byte) 100 : (byte) -1);
    }

    public void refreshVisibility(Player player, GameMode gameMode) {
        updateNameTag(player, plugin.getPlayerColor(player));
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

        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(HIDDEN_TEAM_NAME);
        if (team != null) team.unregister();
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refreshVisibility(player, player.getGameMode());
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refreshVisibility(player, player.getGameMode());
            }
        }, 1L);
    }

    /**
     * Исправление совместимости с GSit:
     * Освобождаем слот пассажира за мгновение до того, как GSit проверит доступность седла на игроке.
     */

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        // ИСПРАВЛЕНО: Используем getDismounted(), чтобы получить игрока-транспорт
        if (event.getDismounted() instanceof Player player) {
            // Если размонтирование вызвано нашим фиксом для GSit — игнорируем отмену события
            if (allowedDismounts.contains(player.getUniqueId())) {
                return; 
            }
        }

        // Запрещаем случайное или багованное спешивание кастомного ника
        for (NameTagEntities entities : playerDisplays.values()) {
            if (event.getEntity().equals(entities.display) || event.getEntity().equals(entities.mount)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        removeNameTag(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refreshVisibility(player, player.getGameMode()); 
            }
        }, 1L);
    }

    @EventHandler
    public void onEntityPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Проверяем и новый и старый эффект — ловим и добавление и снятие невидимости
        boolean isInvisibilityEvent =
                (event.getNewEffect() != null && event.getNewEffect().getType().equals(PotionEffectType.INVISIBILITY))
             || (event.getOldEffect() != null && event.getOldEffect().getType().equals(PotionEffectType.INVISIBILITY));

        if (!isInvisibilityEvent) return;

        // Эффект ещё не применён/снят в момент события — ждём 1 тик
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) refreshVisibility(player, player.getGameMode());
        }, 1L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Player target) {
            NameTagEntities entities = playerDisplays.get(target.getUniqueId());
            
            if (entities != null && target.getPassengers().contains(entities.mount)) {
                // Разрешаем размонтирование сущности
                allowedDismounts.add(target.getUniqueId());
                
                // РАЗКОММЕНТИРОВАНО: Снимаем прокладку, чтобы GSit увидел пустое место
                target.removePassenger(entities.mount);
                
                // Даем GSit запас времени (2 тика вместо 1), чтобы он точно успел посадить игрока
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    allowedDismounts.remove(target.getUniqueId());
                }, 2L);
            }
        }
    }
}
