package me.meldot.nickcolor;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

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
    public String getIdentifier() {
        return "nickcolor";
    }

    @Override
    public String getAuthor() {
        // Берем список авторов из plugin.yml через современный Paper API
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        // Берем версию напрямую из plugin.yml через современный Paper API
        return plugin.getPluginMeta().getVersion();
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
    public String onPlaceholderRequest(Player player, String params) {
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

        if (params.equalsIgnoreCase("cooldown")) {
            // %nickcolor_cooldown% - возвращает оставшееся время кулдауна в секундах
            long left = plugin.getCooldown(player) / 1000;
            return String.valueOf(left);
        }

        return null; // Плейсхолдер не распознан
    }
}