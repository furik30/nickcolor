package me.meldot.nickcolor;

import org.bukkit.plugin.java.JavaPlugin;

public class NickColorPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Этот код выполняется в момент запуска сервера
        getLogger().info("Плагин NickColor успешно загружен и готов к работе!");
    }

    @Override
    public void onDisable() {
        // Этот код выполняется при выключении или перезагрузке сервера
        getLogger().info("Плагин NickColor выключен.");
    }
}