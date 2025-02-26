package com.elpideus.mcMMOAdditions

import com.elpideus.mcMMOAdditions.config.MainConfig
import com.elpideus.mcMMOAdditions.logging.Color
import de.themoep.minedown.adventure.MineDown
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player


/**
 * Utility object for logging messages with Minecraft-styled color codes.
 * This object provides methods for printing messages with different log levels, such as info, warning, and error, with
 * support for Minecraft's color formatting.
 *
 * The messages can be customized with various colors, styles, and formatting supported by Minecraft's color codes.
 * This object also translates Minecraft color codes into ANSI escape codes to ensure terminal compatibility.
 */
object Logging {
    /**
     * A map that associates Minecraft color codes with their respective ANSI escape codes.
     * Used to translate Minecraft color codes into terminal-compatible colors.
     */
    private val mcToANSIMap: Map<String, String> = mapOf(
        "&0" to "${Color.BLACK}",
        "&1" to "${Color.DARK_BLUE}",
        "&2" to "${Color.GREEN}",
        "&3" to "${Color.DARK_AQUA}",
        "&4" to "${Color.DARK_RED}",
        "&5" to "${Color.DARK_PURPLE}",
        "&6" to "${Color.GOLD}",
        "&7" to "${Color.GRAY}",
        "&8" to "${Color.DARK_GRAY}",
        "&9" to "${Color.BLUE}",
        "&a" to "${Color.GREEN}",
        "&b" to "${Color.AQUA}",
        "&c" to "${Color.RED}",
        "&d" to "${Color.LIGHT_PURPLE}",
        "&e" to "${Color.YELLOW}",
        "&f" to "${Color.WHITE}",
        "&k" to "${Color.MAGIC}",
        "&l" to "${Color.BOLD}",
        "&m" to "${Color.STRIKETHROUGH}",
        "&n" to "${Color.UNDERLINE}",
        "&o" to "${Color.ITALIC}",
        "&r" to "${Color.RESET}"
    )

    /**
     * Parses the input string and replaces Minecraft color codes with their corresponding ANSI escape codes.
     *
     * This function scans the input string for Minecraft color codes (e.g., `&1`, `&a`, etc.) and replaces them with the
     * appropriate ANSI escape codes, allowing the text to be styled with colors and effects in terminal environments.
     *
     * @param content The input string possibly containing Minecraft color codes.
     * @return The input string with eventual Minecraft color codes replaced by ANSI escape codes.
     */
    private fun parse(content: String, usePrefix: Boolean = true, autoResetColor: Boolean = true, autoSpace: Boolean  = true): String {
        var editedContent = if (usePrefix) MainConfig.PREFIX + if (autoSpace) " " else "" + content else content
        if (autoResetColor) editedContent += MineDown.parse(content + Color.RESET)
        val result = StringBuilder(editedContent.length)

        var index = 0
        while (index < editedContent.length) {
            // Check if the current character is '&' and there's enough room for a color code
            if (editedContent[index] == '&' && index + 1 < editedContent.length) {
                val colorCode = editedContent.substring(index, index + 2).lowercase()

                // If the color code exists in the map, append the corresponding ANSI code
                if (mcToANSIMap.containsKey(colorCode)) {
                    result.append(mcToANSIMap[colorCode])
                    index += 2  // Move past the color code
                } else {
                    // If not a color code, just append the current character
                    result.append(editedContent[index])
                    index++
                }
            } else {
                // If not a color code, just append the current character
                result.append(editedContent[index])
                index++
            }
        }

        return result.toString()
    }



    /**
     * Prints the content to the console with Minecraft-styled color formatting.
     *
     * This method prints the provided content to the console, applying the appropriate Minecraft color formatting
     * based on the Minecraft color codes embedded in the string.
     *
     * @param content The string to print, potentially containing Minecraft color codes.
     */
    fun print(content: String) {
        println(parse(content))
    }

    /**
     * Prints an error message to the console in red with Minecraft-styled color formatting.
     *
     * This method is a convenience function for logging error messages. It prints the given content in red, using
     * Minecraft color formatting, and resets the color formatting afterward.
     *
     * @param content The error message to print.
     */
    fun error(content: String) {
        print("${Color.RED}${content}${Color.RESET}")
    }

    /**
     * Prints a warning message to the console in yellow with Minecraft-styled color formatting.
     *
     * This method is a convenience function for logging warning messages. It prints the given content in yellow, using
     * Minecraft color formatting, and resets the color formatting afterward.
     *
     * @param content The warning message to print.
     */
    fun warn(content: String) {
        print("${Color.YELLOW}${content.replace("&r", Color.YELLOW.toString())}${Color.RESET}")
    }

    /**
     * Prints a warning message to the console in yellow with Minecraft-styled color formatting.
     *
     * This method is an alias for `warn()`, making it easier to use both naming conventions. It prints the given content
     * in yellow, using Minecraft color formatting, and resets the color formatting afterward.
     *
     * @param content The warning message to print.
     * @see Logging.warn
     */
    fun warning(content: String) {
        warn(content);
    }

    /**
     * Prints an informational message to the console in blue with Minecraft-styled color formatting.
     *
     * This method is a convenience function for logging informational messages. It prints the given content in blue, using
     * Minecraft color formatting, and resets the color formatting afterward.
     *
     * @param content The informational message to print.
     */
    fun info(content: String) {
        print("${Color.BLUE}${content}${Color.RESET}")
    }
}