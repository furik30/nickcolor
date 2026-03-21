package me.meldot.nickcolor;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Класс расширения PlaceholderAPI.
 * Предоставляет плейсхолдеры для сторонних плагинов (например, FlectonePulse).
 */
public class NickColorExpansion extends PlaceholderExpansion {

    private final NickColorPlugin plugin;

    public NickColorExpansion(NickColorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nickcolor";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Jules"; // Или author из plugin.yml
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // Расширение должно оставаться зарегистрированным
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    /**
     * Обработчик плейсхолдеров.
     *
     * @param player Игрок, для которого запрашивается плейсхолдер.
     * @param params Параметры плейсхолдера (то, что идет после %nickcolor_).
     * @return Значение плейсхолдера или null.
     */
    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        String colorFormat = plugin.getPlayerColor(player);

        if (params.equalsIgnoreCase("tag")) {
            // %nickcolor_tag% - возвращает открывающий тег (например, <#FF5555> или <gradient:#ff0000:#00ff00>)
            if (colorFormat != null && !colorFormat.isEmpty()) {
                return colorFormat;
            }
            return ""; // Возвращаем пустую строку, если цвета нет
        }

        if (params.equalsIgnoreCase("name")) {
            // %nickcolor_name% - возвращает полностью отформатированный ник с закрывающими тегами
            if (colorFormat != null && !colorFormat.isEmpty()) {
                if (colorFormat.startsWith("<gradient:")) {
                    return colorFormat + player.getName() + "</gradient>";
                } else {
                    // Извлекаем hex-цвет из тега, например <#FF5555> -> #FF5555
                    String hexColor = colorFormat.replace("<", "").replace(">", "");
                    return colorFormat + player.getName() + "</" + hexColor + ">";
                }
            }
            // Возвращаем просто имя игрока, если цвета нет
            return player.getName();
        }

        return null; // Плейсхолдер не распознан
    }
}