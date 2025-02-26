package com.elpideus.mcMMOAdditions

import com.elpideus.mcMMOAdditions.commands.ConfigCommand
import org.bukkit.plugin.java.JavaPlugin
import com.elpideus.mcMMOAdditions.config.MainConfig
import de.themoep.minedown.adventure.MineDown
import net.kyori.adventure.audience.Audience
import org.bukkit.entity.Player

class Main : JavaPlugin() {

    companion object {
        var VERSION = "2.0.0"
        fun Player.message(message: String, prefix: Boolean = true) {
            (this as Audience).sendMessage(MineDown.parse("${if (prefix) MainConfig.PREFIX else ""}${if (MainConfig.SPACE_AFTER_PREFIX) " " else ""}${message}"))
            println(MainConfig.SPACE_AFTER_PREFIX)
        }
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
