package me.meldot.nickcolor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Обработчик команды /nickcolor (алиасы: /nc, /color, /nickc).
 */
public class NickColorCommand implements CommandExecutor, TabCompleter {

    private final NickColorPlugin plugin;

    public NickColorCommand(NickColorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reset":
                handleResetCommand(sender, args);
                break;
            case "presets":
                handlePresetsCommand(sender);
                break;
            case "random":
                handleRandomCommand(sender, args);
                break;
            case "gradient":
                handleGradientCommand(sender, args);
                break;
            case "set":
                handleAdminSetCommand(sender, args);
                break;
            case "reload":
                handleAdminReloadCommand(sender);
                break;
            default:
                // Обработка /nc <hex> или /nc <c1>:<c2>
                handleHexOrShorthandGradientCommand(sender, args[0]);
                break;
        }

        return true;
    }

    /**
     * Отправляет сообщение помощи с описанием команд.
     *
     * @param sender Получатель.
     */
    private void sendHelpMessage(CommandSender sender) {
        String msg = plugin.getConfig().getString("messages.help");
        if (msg != null) {
            sender.sendMessage(ColorUtils.format(msg));
        } else {
            // Фолбэк, если в конфиге нет help сообщения
            sender.sendMessage(ColorUtils.format("<gold><b>Справка по плагину NickColor:</b></gold>"));
            if (sender.hasPermission("nickcolor.use")) {
                sender.sendMessage(ColorUtils.format("<gray>/nc <hex> <white>- Установить цвет (например: #FF5555)</white>"));
                sender.sendMessage(ColorUtils.format("<gray>/nc <hex1>:<hex2> <white>- Установить градиент (например: #FF0000:#00FF00)</white>"));
                sender.sendMessage(ColorUtils.format("<gray>/nc gradient <hex1>:<hex2> <white>- Установить градиент</white>"));
                sender.sendMessage(ColorUtils.format("<gray>/nc random <white>- Установить случайный цвет</white>"));
                sender.sendMessage(ColorUtils.format("<gray>/nc random gradient <white>- Установить случайный градиент</white>"));
                sender.sendMessage(ColorUtils.format("<gray>/nc presets <white>- Меню пресетов</white>"));
                sender.sendMessage(ColorUtils.format("<gray>/nc reset <white>- Сбросить цвет ника</white>"));
            }
            if (sender.hasPermission("nickcolor.admin.set")) {
                sender.sendMessage(ColorUtils.format("<gray>/nc set <игрок> <цвет> <white>- Установить цвет другому игроку</white>"));
                sender.sendMessage(ColorUtils.format("<gray>/nc set gradient <игрок> <c1>:<c2> <white>- Установить градиент другому игроку</white>"));
            }
            if (sender.hasPermission("nickcolor.admin.reload")) {
                sender.sendMessage(ColorUtils.format("<gray>/nc reload <white>- Перезагрузить конфиг</white>"));
            }
        }
    }

    /**
     * Обработка сброса цвета: /nc reset
     */
    private void handleResetCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nickcolor.use")) {
            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player)) {
            sendPlayerOnly(sender);
            return;
        }

        Player player = (Player) sender;
        plugin.resetPlayerColor(player);
        sendMessage(sender, "messages.color-reset");
    }

    /**
     * Обработка пресетов: /nc presets
     */
    private void handlePresetsCommand(CommandSender sender) {
        if (!sender.hasPermission("nickcolor.use")) {
            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player)) {
            sendPlayerOnly(sender);
            return;
        }

        ConfigurationSection presets = plugin.getConfig().getConfigurationSection("presets");
        if (presets == null || presets.getKeys(false).isEmpty()) {
            sendMessage(sender, "messages.no-presets");
            return;
        }

        sendMessage(sender, "messages.presets-header");

        for (String presetName : presets.getKeys(false)) {
            String colors = presets.getString(presetName);
            if (colors == null) continue;

            String[] split = colors.split(":");
            Component presetComponent;

            if (split.length == 1) {
                // Сплошной цвет пресета
                String formatTag = "<" + ColorUtils.ensureHexHasHash(split[0]) + ">";
                presetComponent = ColorUtils.format(formatTag + presetName + "</" + ColorUtils.ensureHexHasHash(split[0]) + ">");
            } else if (split.length == 2) {
                // Градиент пресета
                String formatTag = "<gradient:" + ColorUtils.ensureHexHasHash(split[0]) + ":" + ColorUtils.ensureHexHasHash(split[1]) + ">";
                presetComponent = ColorUtils.format(formatTag + presetName + "</gradient>");
            } else {
                continue;
            }

            // Добавляем кликабельность: по клику выполняется /nc <значение_пресета>
            Component clickMessage = presetComponent.clickEvent(ClickEvent.runCommand("/nc " + colors))
                    .hoverEvent(HoverEvent.showText(ColorUtils.format("<gray>Нажмите, чтобы выбрать пресет <white>" + presetName + "</white>!</gray>")));

            sender.sendMessage(Component.text(" - ").append(clickMessage));
        }
    }

    /**
     * Обработка случайного цвета: /nc random [gradient]
     */
    private void handleRandomCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nickcolor.use")) {
            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player)) {
            sendPlayerOnly(sender);
            return;
        }

        Player player = (Player) sender;

        if (args.length > 1 && args[1].equalsIgnoreCase("gradient")) {
            // Случайный градиент
            String hex1 = ColorUtils.generateRandomHex();
            String hex2 = ColorUtils.generateRandomHex();
            String formatTag = "<gradient:" + hex1 + ":" + hex2 + ">";
            plugin.setPlayerColor(player, formatTag);
            String successMsg = plugin.getConfig().getString("messages.gradient-applied", "<green>Установлен градиент: {gradient}</green>")
                    .replace("{gradient}", formatTag + hex1 + ":" + hex2 + "</gradient>");
            sender.sendMessage(ColorUtils.format(successMsg));
        } else {
            // Случайный сплошной цвет
            String hex = ColorUtils.generateRandomHex();
            String formatTag = "<" + hex + ">";
            plugin.setPlayerColor(player, formatTag);
            String successMsg = plugin.getConfig().getString("messages.color-changed", "<green>Цвет установлен: {color}</green>")
                    .replace("{color}", formatTag + hex + "</" + hex + ">");
            sender.sendMessage(ColorUtils.format(successMsg));
        }
    }

    /**
     * Обработка команды /nc gradient <c1:c2>
     */
    private void handleGradientCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nickcolor.use")) {
            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player)) {
            sendPlayerOnly(sender);
            return;
        }

        if (args.length < 2) {
            sendMessage(sender, "messages.invalid-gradient");
            return;
        }

        String input = args[1];
        Player player = (Player) sender;
        applyGradientToPlayer(player, input, sender);
    }

    /**
     * Обработка прямой команды: /nc #FF5555 или /nc #FF0000:#00FF00
     */
    private void handleHexOrShorthandGradientCommand(CommandSender sender, String input) {
        if (!sender.hasPermission("nickcolor.use")) {
            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player)) {
            sendPlayerOnly(sender);
            return;
        }

        Player player = (Player) sender;

        if (input.contains(":")) {
            // Попытка применить как градиент, если есть двоеточие
            applyGradientToPlayer(player, input, sender);
        } else {
            // Попытка применить как сплошной цвет
            applySolidColorToPlayer(player, input, sender);
        }
    }

    /**
     * Устанавливает сплошной цвет игроку
     */
    private void applySolidColorToPlayer(Player target, String hex, CommandSender sender) {
        if (!ColorUtils.isValidHex(hex)) {
            sendMessage(sender, "messages.invalid-command");
            return;
        }

        String formattedHex = ColorUtils.ensureHexHasHash(hex);
        String formatTag = "<" + formattedHex + ">";
        plugin.setPlayerColor(target, formatTag);

        String successMsg = plugin.getConfig().getString("messages.color-changed", "<green>Цвет установлен: {color}</green>")
                .replace("{color}", formatTag + formattedHex + "</" + formattedHex.replace("#", "") + ">");

        // Отправка сообщения. Если устанавливает админ, то сообщение другое.
        if (target.equals(sender)) {
            sender.sendMessage(ColorUtils.format(successMsg));
        } else {
            sender.sendMessage(ColorUtils.format("<green>Цвет установлен для игрока " + target.getName() + "</green>"));
            target.sendMessage(ColorUtils.format(successMsg));
        }
    }

    /**
     * Устанавливает градиент игроку
     */
    private void applyGradientToPlayer(Player target, String input, CommandSender sender) {
        String[] split = input.split(":");
        if (split.length != 2) {
            sendMessage(sender, "messages.invalid-gradient");
            return;
        }

        String hex1 = split[0];
        String hex2 = split[1];

        if (!ColorUtils.isValidHex(hex1) || !ColorUtils.isValidHex(hex2)) {
            sendMessage(sender, "messages.invalid-gradient");
            return;
        }

        String fHex1 = ColorUtils.ensureHexHasHash(hex1);
        String fHex2 = ColorUtils.ensureHexHasHash(hex2);
        String formatTag = "<gradient:" + fHex1 + ":" + fHex2 + ">";

        plugin.setPlayerColor(target, formatTag);

        String successMsg = plugin.getConfig().getString("messages.gradient-applied", "<green>Установлен градиент: {gradient}</green>")
                .replace("{gradient}", formatTag + fHex1 + ":" + fHex2 + "</gradient>");

        if (target.equals(sender)) {
            sender.sendMessage(ColorUtils.format(successMsg));
        } else {
            sender.sendMessage(ColorUtils.format("<green>Градиент установлен для игрока " + target.getName() + "</green>"));
            target.sendMessage(ColorUtils.format(successMsg));
        }
    }

    /**
     * Обработка админ-команды установки: /nc set <player> <color> | /nc set gradient <player> <c1:c2>
     */
    private void handleAdminSetCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nickcolor.admin.set")) {
            sendNoPermission(sender);
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ColorUtils.format("<red>Использование: /nc set <игрок> <цвет> или /nc set gradient <игрок> <c1:c2></red>"));
            return;
        }

        if (args[1].equalsIgnoreCase("gradient")) {
            // /nc set gradient <игрок> <c1:c2>
            if (args.length < 4) {
                sender.sendMessage(ColorUtils.format("<red>Использование: /nc set gradient <игрок> <c1:c2></red>"));
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sendMessage(sender, "messages.player-not-found");
                return;
            }
            applyGradientToPlayer(target, args[3], sender);
        } else {
            // /nc set <игрок> <цвет>
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sendMessage(sender, "messages.player-not-found");
                return;
            }
            applySolidColorToPlayer(target, args[2], sender);
        }
    }

    /**
     * Обработка админ-команды перезагрузки: /nc reload
     */
    private void handleAdminReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("nickcolor.admin.reload")) {
            sendNoPermission(sender);
            return;
        }

        plugin.reloadConfig();
        
        // Моментально применяем новые настройки NameTagManager для всех игроков онлайн
        for (Player p : Bukkit.getOnlinePlayers()) {
            String color = plugin.getPlayerColor(p);
            plugin.getNameTagManager().updateNameTag(p, color);
        }

        sendMessage(sender, "messages.reloaded");
    }

    /**
     * Вспомогательный метод для отправки сообщений из конфига.
     */
    private void sendMessage(CommandSender sender, String configKey) {
        String msg = plugin.getConfig().getString(configKey);
        if (msg != null && !msg.isEmpty()) {
            sender.sendMessage(ColorUtils.format(msg));
        } else {
            sender.sendMessage(ColorUtils.format("<red>Сообщение " + configKey + " не найдено в конфигурации!</red>"));
        }
    }

    private void sendNoPermission(CommandSender sender) {
        sendMessage(sender, "messages.no-permission");
    }

    private void sendPlayerOnly(CommandSender sender) {
        sendMessage(sender, "messages.player-only");
    }

    // --- Tab Completer ---

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("nickcolor.use")) {
                completions.add("reset");
                completions.add("presets");
                completions.add("random");
                completions.add("gradient");
                completions.add("help");
            }
            if (sender.hasPermission("nickcolor.admin.set")) {
                completions.add("set");
            }
            if (sender.hasPermission("nickcolor.admin.reload")) {
                completions.add("reload");
            }
            return filterCompletions(completions, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("random") && sender.hasPermission("nickcolor.use")) {
                completions.add("gradient");
                return filterCompletions(completions, args[1]);
            }
            if (args[0].equalsIgnoreCase("set") && sender.hasPermission("nickcolor.admin.set")) {
                completions.add("gradient");
                // Добавляем ники онлайн игроков
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
                return filterCompletions(completions, args[1]);
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("gradient") && sender.hasPermission("nickcolor.admin.set")) {
                 for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
                return filterCompletions(completions, args[2]);
            }
        }

        return completions;
    }

    private List<String> filterCompletions(List<String> completions, String start) {
        if (start == null || start.isEmpty()) return completions;
        String lowerStart = start.toLowerCase();
        return completions.stream()
                .filter(c -> c.toLowerCase().startsWith(lowerStart))
                .collect(Collectors.toList());
    }
}