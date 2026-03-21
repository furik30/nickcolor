package me.meldot.nickcolor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Random;
import java.util.regex.Pattern;

/**
 * Утилиты для работы с цветами и форматами MiniMessage.
 */
public class ColorUtils {

    // Паттерн для валидации HEX цвета (с решеткой или без)
    private static final Pattern HEX_PATTERN = Pattern.compile("^(#)?[0-9a-fA-F]{6}$");
    private static final Random RANDOM = new Random();

    /**
     * Форматирует строку (сообщение или ник) с применением MiniMessage.
     *
     * @param text Исходный текст (с MiniMessage тегами).
     * @return Отформатированный Component.
     */
    public static Component format(String text) {
        if (text == null) return Component.empty();
        return MiniMessage.miniMessage().deserialize(text);
    }

    /**
     * Возвращает чистый текст (без форматирования) из Component.
     *
     * @param component Компонент.
     * @return Чистый текст.
     */
    public static String getPlainText(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Создает строку сплошного цвета в формате MiniMessage.
     *
     * @param hex HEX-код цвета.
     * @param text Текст для раскраски.
     * @return Строка MiniMessage, например <#FF0000>Текст</#FF0000>
     */
    public static String createSolidColorFormat(String hex, String text) {
        String formattedHex = ensureHexHasHash(hex);
        return "<" + formattedHex + ">" + text + "</" + formattedHex + ">";
    }

    /**
     * Создает строку градиента в формате MiniMessage.
     *
     * @param hex1 Первый HEX-код.
     * @param hex2 Второй HEX-код.
     * @param text Текст для раскраски.
     * @return Строка MiniMessage, например <gradient:#FF0000:#00FF00>Текст</gradient>
     */
    public static String createGradientFormat(String hex1, String hex2, String text) {
        String formattedHex1 = ensureHexHasHash(hex1);
        String formattedHex2 = ensureHexHasHash(hex2);
        return "<gradient:" + formattedHex1 + ":" + formattedHex2 + ">" + text + "</gradient>";
    }

    /**
     * Генерирует случайный HEX-цвет.
     *
     * @return Случайный HEX-цвет в формате #RRGGBB.
     */
    public static String generateRandomHex() {
        int nextInt = RANDOM.nextInt(0xffffff + 1);
        return String.format("#%06x", nextInt);
    }

    /**
     * Проверяет, является ли строка валидным HEX-цветом.
     *
     * @param hex Строка для проверки (с решеткой или без, или названия ванильных цветов).
     * @return true, если валидный, иначе false.
     */
    public static boolean isValidHex(String hex) {
        if (hex == null || hex.isEmpty()) return false;
        // Поддержка ванильных цветов через MiniMessage
        if (isVanillaColor(hex)) return true;
        return HEX_PATTERN.matcher(hex).matches();
    }

    /**
     * Проверяет, является ли строка стандартным цветом MiniMessage.
     *
     * @param color Название цвета.
     * @return true, если это стандартный цвет.
     */
    private static boolean isVanillaColor(String color) {
        switch (color.toLowerCase()) {
            case "black":
            case "dark_blue":
            case "dark_green":
            case "dark_aqua":
            case "dark_red":
            case "dark_purple":
            case "gold":
            case "gray":
            case "dark_gray":
            case "blue":
            case "green":
            case "aqua":
            case "red":
            case "light_purple":
            case "yellow":
            case "white":
                return true;
            default:
                return false;
        }
    }

    /**
     * Убеждается, что HEX-цвет начинается с решетки. Если передано название цвета, возвращает как есть.
     *
     * @param hex HEX-код.
     * @return HEX-код с решеткой или название цвета.
     */
    public static String ensureHexHasHash(String hex) {
        if (isVanillaColor(hex)) return hex.toLowerCase();
        if (!hex.startsWith("#")) {
            return "#" + hex;
        }
        return hex;
    }

    /**
     * Применяет формат цвета к тексту.
     * Если формат - это просто цвет (например, <#FF5555> или <gradient:#FF0000:#00FF00>),
     * он оборачивает текст в этот формат.
     *
     * @param format Формат (тег открытия). Если это готовая строка с текстом, это не будет работать ожидаемо для новых строк.
     *               В нашем случае format в БД будет храниться как <#FF5555> (только открывающий тег)
     *               или <gradient:#111111:#222222>.
     * @param text Текст для форматирования.
     * @return Форматированный Component.
     */
    public static Component applyFormat(String format, String text) {
        if (format == null || format.isEmpty()) {
            return Component.text(text);
        }
        // Если формат - это готовая строка MiniMessage с тегами, то
        // <tag>text</tag>
        // В БД мы сохраняем именно тег, например "<#FF5555>" или "<gradient:#ff0000:#00ff00>"
        // Чтобы избежать проблем, мы можем просто склеить: тег + текст
        // MiniMessage сам закроет тег в конце строки.
        return format(format + text);
    }
}