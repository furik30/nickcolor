package me.meldot.nickcolor;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Управляет никами над головой (NameTag) с помощью сущностей TextDisplay.
 */
public class NameTagManager implements Listener {

    private final NickColorPlugin plugin;
    private final Map<UUID, TextDisplay> playerDisplays = new HashMap<>();

    // Сдвиг по оси Y для позиционирования ника над головой пассажира
    private static final float STANDING_Y_OFFSET = 0.35f;
    private static final float SNEAKING_Y_OFFSET = 0.05f;

    // Имя команды для скрытия ванильных ников
    private static final String HIDDEN_TEAM_NAME = "NC_HIDDEN_NAMETAGS";

    public NameTagManager(NickColorPlugin plugin) {
        this.plugin = plugin;
        setupHiddenTeam();
    }

    /**
     * Создает или получает Scoreboard Team для скрытия ванильных ников.
     */
    private void setupHiddenTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(HIDDEN_TEAM_NAME);
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
    }

    /**
     * Скрывает ванильный ник игрока, добавляя его в специальную Team.
     */
    public void hideVanillaNameTag(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_TEAM_NAME);
        if (team != null && !team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    /**
     * Создает или обновляет TextDisplay для игрока.
     *
     * @param player Игрок.
     * @param colorFormat Формат цвета (MiniMessage) или null/пустая строка.
     */
    public void updateNameTag(Player player, String colorFormat) {
        hideVanillaNameTag(player);

        TextDisplay display = playerDisplays.get(player.getUniqueId());

        if (display == null || display.isDead()) {
            // Создаем новую сущность TextDisplay
            display = player.getWorld().spawn(player.getLocation(), TextDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setBillboard(Display.Billboard.CENTER); // Текст всегда смотрит на камеру
                entity.setShadowed(true); // Ванильная тень текста
                // Настраиваем прозрачность фона по умолчанию (полупрозрачный черный)
                entity.setDefaultBackground(true);
            });

            // Добавляем как пассажира
            player.addPassenger(display);
            playerDisplays.put(player.getUniqueId(), display);
        }

        // Устанавливаем текст
        Component nameComponent;
        if (colorFormat != null && !colorFormat.isEmpty()) {
            nameComponent = ColorUtils.applyFormat(colorFormat, player.getName());
        } else {
            nameComponent = Component.text(player.getName());
        }
        display.text(nameComponent);

        // Устанавливаем правильную позицию (сдвиг)
        applyTransformation(display, player.isSneaking());
    }

    /**
     * Применяет матрицу трансформации для сдвига TextDisplay.
     */
    private void applyTransformation(TextDisplay display, boolean isSneaking) {
        float yOffset = isSneaking ? SNEAKING_Y_OFFSET : STANDING_Y_OFFSET;

        // Создаем матрицу трансформации только со сдвигом по Y
        Matrix4f transformation = new Matrix4f().translate(0, yOffset, 0);
        display.setTransformationMatrix(transformation);

        // Управляем прозрачностью текста при приседании
        if (isSneaking) {
            display.setTextOpacity((byte) 100); // Полупрозрачный (от 0 до 255, где 255 - полностью непрозрачный, байт может переполняться в Java, но 100 ок)
        } else {
            display.setTextOpacity((byte) -1); // 255 (полная непрозрачность)
        }
    }

    /**
     * Удаляет TextDisplay игрока.
     */
    public void removeNameTag(Player player) {
        TextDisplay display = playerDisplays.remove(player.getUniqueId());
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    /**
     * Удаляет все TextDisplay (полезно при выключении плагина).
     */
    public void removeAllNameTags() {
        for (TextDisplay display : playerDisplays.values()) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
        playerDisplays.clear();
    }

    /**
     * Обрабатывает изменение состояния приседания.
     */
    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        TextDisplay display = playerDisplays.get(player.getUniqueId());

        if (display != null && !display.isDead()) {
            applyTransformation(display, event.isSneaking());
        }
    }
}