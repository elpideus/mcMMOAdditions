package com.elpideus.mcMMOAdditions.config

import com.elpideus.mcMMOAdditions.Logging
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object MainConfig {

    private lateinit var plugin: JavaPlugin
    private lateinit var configFile: File
    lateinit var config: FileConfiguration
    var PREFIX = (ConfigSanitizer.MainConfigSanitizer.Expected.PREFIX.default as ExpectedConfigField).value as String

    fun initialize(plugin: JavaPlugin) {
        if (!MainConfig::plugin.isInitialized) {
            MainConfig.plugin = plugin
            configFile = File(plugin.dataFolder, "config.yml")
            if (!configFile.exists()) plugin.saveResource("config.yml", false)
            config = plugin.config
            PREFIX = plugin.config.getString("prefix") ?: (ConfigSanitizer.MainConfigSanitizer.Expected.PREFIX.default as ExpectedConfigField).value as String
        }
    }

    fun save() {
        plugin.saveConfig()
    }
    fun sanitize() {
        ConfigSanitizer.MainConfigSanitizer.sanitize()
    }

    fun reload() {
        plugin.reloadConfig()
    }

    fun set(path: String, value: Any, save: Boolean = true, reload: Boolean = false) {
        plugin.config.set(path, value)
        if (save) plugin.saveConfig()
        if (reload) plugin.reloadConfig()
    }

    fun get(path: String): Any? {
        return plugin.config.get(path)
    }

}
