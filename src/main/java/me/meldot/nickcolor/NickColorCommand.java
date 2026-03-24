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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Обработчик команды /nickcolor (алиасы: /nc, /color, /nickc).
 * Структура:
 * - /nc set <цвет | c1:c2 | random> [gradient]
 * - /nc reset
 * - /nc presets
 * - /nc admin <set|reset|reload> ...
 */
public class NickColorCommand implements CommandExecutor, TabCompleter {

    private final NickColorPlugin plugin;

    // Список стандартных цветов MiniMessage для автодополнения
    private static final List<String> VANILLA_COLORS = Arrays.asList(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
    );

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
            case "set":
                handlePlayerSetCommand(sender, args);
                break;
            case "reset":
                handlePlayerResetCommand(sender);
                break;
            case "presets":
                handlePresetsCommand(sender);
                break;
            case "admin":
                handleAdminCommand(sender, args);
                break;
            default:
                sender.sendMessage(ColorUtils.format("<red>Неизвестная команда. Введите /nc help для справки.</red>"));
                break;
        }

        return true;
    }

    /**
     * Отправляет сообщение помощи с описанием новой структуры команд.
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ColorUtils.format("<gold><b>Справка по плагину NickColor:</b></gold>"));
        if (sender.hasPermission("nickcolor.use")) {
            sender.sendMessage(ColorUtils.format("<gray>/nc set <hex> <white>- Установить цвет (например: #FF5555)</white>"));
            sender.sendMessage(ColorUtils.format("<gray>/nc set <hex1>:<hex2> <white>- Установить градиент</white>"));
            sender.sendMessage(ColorUtils.format("<gray>/nc set random [gradient] <white>- Случайный цвет или градиент</white>"));
            sender.sendMessage(ColorUtils.format("<gray>/nc presets <white>- Меню готовых пресетов</white>"));
            sender.sendMessage(ColorUtils.format("<gray>/nc reset <white>- Сбросить свой цвет ника</white>"));
        }
        if (sender.hasPermission("nickcolor.admin.set") || sender.hasPermission("nickcolor.admin.reload")) {
            sender.sendMessage(ColorUtils.format("<gold><b>Админ-команды:</b></gold>"));
        }
        if (sender.hasPermission("nickcolor.admin.set")) {
            sender.sendMessage(ColorUtils.format("<gray>/nc admin set <игрок> <цвет|c1:c2|random> [gradient]</gray>"));
            sender.sendMessage(ColorUtils.format("<gray>/nc admin reset <игрок></gray>"));
        }
        if (sender.hasPermission("nickcolor.admin.reload")) {
            sender.sendMessage(ColorUtils.format("<gray>/nc admin reload <white>- Перезагрузить конфиг</white></gray>"));
        }
    }

    // --- БЛОК КОМАНД ИГРОКА ---

    private void handlePlayerSetCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nickcolor.use")) {
            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player)) {
            sendPlayerOnly(sender);
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.format("<red>Использование: /nc set <цвет | c1:c2 | random> [gradient]</red>"));
            return;
        }

        Player player = (Player) sender;
        if (!checkCooldownAndNotify(player)) {
            return;
        }

        String action = args[1].toLowerCase();

        if (action.equals("random")) {
            handleRandomColor(player, args, 2, sender); // Индекс 2 для проверки слова "gradient"
        } else if (action.contains(":")) {
            applyGradientToPlayer(player, action, sender);
        } else {
            applySolidColorToPlayer(player, action, sender);
        }
    }

    private void handlePlayerResetCommand(CommandSender sender) {
        if (!sender.hasPermission("nickcolor.use")) {
            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player)) {
            sendPlayerOnly(sender);
            return;
        }

        Player player = (Player) sender;
        if (!checkCooldownAndNotify(player)) {
            return;
        }

        plugin.resetPlayerColor(player);
        plugin.setCooldown(player);
        sendMessage(sender, "messages.color-reset");
    }

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
                String formatTag = "<" + ColorUtils.ensureHexHasHash(split[0]) + ">";
                presetComponent = ColorUtils.format(formatTag + presetName + "</" + ColorUtils.ensureHexHasHash(split[0]) + ">");
            } else if (split.length >= 2) {
                List<String> formattedHexes = Arrays.stream(split)
                        .map(ColorUtils::ensureHexHasHash)
                        .collect(Collectors.toList());
                String joinedHexes = String.join(":", formattedHexes);
                String formatTag = "<gradient:" + joinedHexes + ">";
                presetComponent = ColorUtils.format(formatTag + presetName + "</gradient>");
            } else {
                continue;
            }

            Component clickMessage = presetComponent.clickEvent(ClickEvent.runCommand("/nc set " + colors))
                    .hoverEvent(HoverEvent.showText(ColorUtils.format("<gray>Нажмите, чтобы выбрать пресет <white>" + presetName + "</white>!</gray>")));

            sender.sendMessage(Component.text(" - ").append(clickMessage));
        }
    }

    // --- БЛОК АДМИН КОМАНД ---

    private void handleAdminCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtils.format("<red>Использование: /nc admin <set | reset | reload></red>"));
            return;
        }

        String adminAction = args[1].toLowerCase();

        switch (adminAction) {
            case "set":
                if (!sender.hasPermission("nickcolor.admin.set")) {
                    sendNoPermission(sender);
                    return;
                }
                if (args.length < 4) {
                    sender.sendMessage(ColorUtils.format("<red>Использование: /nc admin set <игрок> <цвет | c1:c2 | random> [gradient]</red>"));
                    return;
                }
                Player targetSet = Bukkit.getPlayer(args[2]);
                if (targetSet == null) {
                    sendMessage(sender, "messages.player-not-found");
                    return;
                }
                
                String colorAction = args[3].toLowerCase();
                if (colorAction.equals("random")) {
                    handleRandomColor(targetSet, args, 4, sender); // Индекс 4 для проверки "gradient"
                } else if (colorAction.contains(":")) {
                    applyGradientToPlayer(targetSet, colorAction, sender);
                } else {
                    applySolidColorToPlayer(targetSet, colorAction, sender);
                }
                break;

            case "reset":
                if (!sender.hasPermission("nickcolor.admin.set")) {
                    sendNoPermission(sender);
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(ColorUtils.format("<red>Использование: /nc admin reset <игрок></red>"));
                    return;
                }
                Player targetReset = Bukkit.getPlayer(args[2]);
                if (targetReset == null) {
                    sendMessage(sender, "messages.player-not-found");
                    return;
                }
                plugin.resetPlayerColor(targetReset);
                sender.sendMessage(ColorUtils.format("<green>Цвет сброшен для игрока " + targetReset.getName() + "</green>"));
                sendMessage(targetReset, "messages.color-reset");
                break;

            case "reload":
                if (!sender.hasPermission("nickcolor.admin.reload")) {
                    sendNoPermission(sender);
                    return;
                }
                plugin.reloadConfig();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    String color = plugin.getPlayerColor(p);
                    plugin.getNameTagManager().updateNameTag(p, color);
                }
                sendMessage(sender, "messages.reloaded");
                break;

            default:
                sender.sendMessage(ColorUtils.format("<red>Неизвестная админ-команда.</red>"));
                break;
        }
    }

    // --- ВНУТРЕННЯЯ ЛОГИКА ПРИМЕНЕНИЯ ЦВЕТОВ ---

    private void handleRandomColor(Player target, String[] args, int gradientIndex, CommandSender sender) {
        if (args.length > gradientIndex && args[gradientIndex].equalsIgnoreCase("gradient")) {
            String hex1 = ColorUtils.generateRandomHex();
            String hex2 = ColorUtils.generateRandomHex();
            String formatTag = "<gradient:" + hex1 + ":" + hex2 + ">";
            plugin.setPlayerColor(target, formatTag);
            
            String successMsg = plugin.getConfig().getString("messages.gradient-applied", "<green>Установлен градиент: {gradient}</green>")
                    .replace("{gradient}", formatTag + hex1 + ":" + hex2 + "</gradient>");
            notifySuccess(sender, target, successMsg, "Случайный градиент установлен для игрока ");
            if (sender.equals(target)) {
                plugin.setCooldown(target);
            }
        } else {
            String hex = ColorUtils.generateRandomHex();
            String formatTag = "<" + hex + ">";
            plugin.setPlayerColor(target, formatTag);
            
            String successMsg = plugin.getConfig().getString("messages.color-changed", "<green>Цвет установлен: {color}</green>")
                    .replace("{color}", formatTag + hex + "</" + hex.replace("#", "") + ">");
            notifySuccess(sender, target, successMsg, "Случайный цвет установлен для игрока ");
            if (sender.equals(target)) {
                plugin.setCooldown(target);
            }
        }
    }

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
        notifySuccess(sender, target, successMsg, "Цвет установлен для игрока ");
        if (sender.equals(target)) {
            plugin.setCooldown(target);
        }
    }

    private void applyGradientToPlayer(Player target, String input, CommandSender sender) {
        String[] split = input.split(":");
        if (split.length < 2) {
            sendMessage(sender, "messages.invalid-gradient");
            return;
        }

        List<String> formattedHexes = new ArrayList<>();
        for (String hex : split) {
            if (!ColorUtils.isValidHex(hex)) {
                sendMessage(sender, "messages.invalid-gradient");
                return;
            }
            formattedHexes.add(ColorUtils.ensureHexHasHash(hex));
        }

        String joinedHexes = String.join(":", formattedHexes);
        String formatTag = "<gradient:" + joinedHexes + ">";

        plugin.setPlayerColor(target, formatTag);

        String successMsg = plugin.getConfig().getString("messages.gradient-applied", "<green>Установлен градиент: {gradient}</green>")
                .replace("{gradient}", formatTag + joinedHexes + "</gradient>");
        notifySuccess(sender, target, successMsg, "Градиент установлен для игрока ");
        if (sender.equals(target)) {
            plugin.setCooldown(target);
        }
    }

    private void notifySuccess(CommandSender sender, Player target, String targetMessage, String adminPrefix) {
        if (target.equals(sender)) {
            sender.sendMessage(ColorUtils.format(targetMessage));
        } else {
            sender.sendMessage(ColorUtils.format("<green>" + adminPrefix + target.getName() + "</green>"));
            target.sendMessage(ColorUtils.format(targetMessage));
        }
    }

    // --- УТИЛИТЫ ---

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

    private boolean checkCooldownAndNotify(Player player) {
        if (player.hasPermission("nickcolor.bypass.cooldown") || player.hasPermission("nickcolor.admin.set")) {
            return true;
        }
        long cooldownLeft = plugin.getCooldown(player);
        if (cooldownLeft > 0) {
            long secondsLeft = (cooldownLeft / 1000) + 1; // Округляем в большую сторону
            String msg = plugin.getConfig().getString("messages.cooldown", "<red>Подождите {time} сек. перед следующей сменой ника!</red>")
                    .replace("{time}", String.valueOf(secondsLeft));
            player.sendMessage(ColorUtils.format(msg));
            return false;
        }
        return true;
    }

    // --- ТАБ КОМПЛИТЕР ---

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        // /nc ...
        if (args.length == 1) {
            if (sender.hasPermission("nickcolor.use")) {
                completions.add("set");
                completions.add("reset");
                completions.add("presets");
                completions.add("help");
            }
            if (sender.hasPermission("nickcolor.admin.set") || sender.hasPermission("nickcolor.admin.reload")) {
                completions.add("admin");
            }
            return filterCompletions(completions, args[0]);
        }

        // /nc set ... ИЛИ /nc admin ...
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set") && sender.hasPermission("nickcolor.use")) {
                completions.add("random");
                completions.addAll(VANILLA_COLORS);
                return filterCompletions(completions, args[1]);
            }
            if (args[0].equalsIgnoreCase("admin")) {
                if (sender.hasPermission("nickcolor.admin.set")) {
                    completions.add("set");
                    completions.add("reset");
                }
                if (sender.hasPermission("nickcolor.admin.reload")) {
                    completions.add("reload");
                }
                return filterCompletions(completions, args[1]);
            }
        }

        // /nc set random ... ИЛИ /nc admin set/reset ...
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("random") && sender.hasPermission("nickcolor.use")) {
                completions.add("gradient");
                return filterCompletions(completions, args[2]);
            }
            if (args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("reset")) && sender.hasPermission("nickcolor.admin.set")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
                return filterCompletions(completions, args[2]);
            }
        }

        // /nc admin set <игрок> ...
        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("set") && sender.hasPermission("nickcolor.admin.set")) {
                completions.add("random");
                completions.addAll(VANILLA_COLORS);
                return filterCompletions(completions, args[3]);
            }
        }

        // /nc admin set <игрок> random ...
        if (args.length == 5) {
            if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("set") && args[3].equalsIgnoreCase("random") && sender.hasPermission("nickcolor.admin.set")) {
                completions.add("gradient");
                return filterCompletions(completions, args[4]);
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