package com.elpideus.mcMMOAdditions

import com.elpideus.mcMMOAdditions.commands.ConfigCommand
import org.bukkit.plugin.java.JavaPlugin
import com.elpideus.mcMMOAdditions.config.MainConfig

class Main : JavaPlugin() {

    companion object {
        var VERSION = "2.0.0"
    }

    override fun onEnable() {
        VERSION = this.description.version
        MainConfig.initialize(this)
        MainConfig.sanitize()
        Logging.print("version &2$VERSION")

        // Register the command executor and tab completer
        val configCommand = ConfigCommand()
        this.getCommand("mmo")?.setExecutor(configCommand)
        this.getCommand("mmo")?.tabCompleter = configCommand
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
