package com.elpideus.mcMMOAdditions.commands

import com.elpideus.mcMMOAdditions.Main.Companion.message
import com.elpideus.mcMMOAdditions.config.ConfigSanitizer
import com.elpideus.mcMMOAdditions.config.ExpectedConfigField
import com.elpideus.mcMMOAdditions.config.ExpectedConfigProperty
import com.elpideus.mcMMOAdditions.config.MainConfig
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.util.StringUtil
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

/**
 * Implements a command that allows players to view and modify configuration settings in-game.
 *
 * This command supports operations like getting and setting configuration values,
 * with tab completion for easier navigation through configuration categories and properties.
 */
class ConfigCommand : CommandExecutor, TabCompleter {
    /** List of available primary subcommands */
    private val firstArgs = arrayOf("set", "get")

    /** Set of commands that operate on configuration values */
    private val setGetCommands = hashSetOf("set", "get")

    /** Sorted list of configuration category names, lazily initialized */
    private val sortedCategoryNames by lazy { ConfigCategoryManagement.sortedCategoryNames }

    /** Map of category names to their properties, lazily initialized */
    private val categoryProperties by lazy { ConfigCategoryManagement.categoryNames }

    /** Cache for ConfigurationSection to Map conversions to improve performance */
    private val mapCache = mutableMapOf<ConfigurationSection, Map<String, Any>>()

    /** The usage string for the config command */
    public val usage = "/config set <section> <category> <path...> <value>"

    /**
     * Converts a ConfigurationSection to a Map representation.
     *
     * Uses a cache to avoid redundant conversions of the same section.
     *
     * @return A Map representation of this ConfigurationSection
     */
    private fun ConfigurationSection.toMap(): Map<String, Any> {
        mapCache[this]?.let { return it }
        val map = HashMap<String, Any>(getKeys(false).size)
        for (key in getKeys(false)) {
            val value = get(key)
            when (value) {
                is ConfigurationSection -> map[key] = value.toMap()
                is List<*> -> map[key] = value.mapTo(ArrayList(value.size)) { if (it is ConfigurationSection) it.toMap() else it ?: "" }
                else -> map[key] = value ?: ""
            }
        }
        mapCache[this] = map
        return map
    }

    /**
     * Handles the execution of the config command.
     *
     * Supports operations like getting and setting configuration values.
     *
     * @param player The command sender
     * @param command The command being executed
     * @param label The command label used
     * @param args The command arguments
     * @return true if the command was handled, false otherwise
     */
    override fun onCommand(player: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (player !is Player) return true
        if (args.isEmpty()) {
            player.message("&eNo instruction specified.")
            return true
        }

        if (args[0].lowercase() == "set") {

            if (args.size < 3) {
                if (args.size < 2) {
                    player.message("&eNo category specified")
                    return true
                }
                player.message("&eNo setting specified")
                return true
            }

            val section = args[1]
            val element = args[2]
            val remainingArgs = args.drop(3)

            if (remainingArgs.isEmpty()) {
                player.message("&eSetting or value not specified")
                return true
            }

            val (pathParts, valueParts) = splitPathAndValue(remainingArgs)
            val newValueString = valueParts.joinToString(" ").let {
                if (it.startsWith("\"") && it.endsWith("\"")) it.substring(1, it.length - 1) else it }

            val category = ConfigCategoryManagement.configCategories[section] ?: run {
                player.message("&eSetting &b##$section## &eis invalid")
                return true
            }

            val expectedClass = category::class.nestedClasses.firstOrNull { it.simpleName == "Expected" }?.objectInstance ?: run {
                player.message("&eSetting &b##$section## &ehas no properties to set.")
                return true
            }

            val expectedProp = expectedClass::class.memberProperties.asSequence().filter { it.visibility == KVisibility.PUBLIC }
                .mapNotNull { it.getter.call(expectedClass) as? ExpectedConfigProperty }.firstOrNull { it.name == element } ?: run {
                player.message("&eInvalid category: &b##$element##")
                return true
            }

            var currentExpectedType: Any? = expectedProp.expectedType
            for (part in pathParts) {
                when (currentExpectedType) {
                    is Map<*, *> -> {
                        if (currentExpectedType.keys.all { it is String }) {
                            @Suppress("UNCHECKED_CAST")
                            val typedMap = currentExpectedType as Map<String, *>
                            val field = typedMap[part] as? ExpectedConfigField ?: run {
                                player.message("&eInvalid path part: '&b##$part##&e'")
                                return true
                            }
                            currentExpectedType = field.value
                        } else {
                            player.message("&eInvalid map keys at path part: '&b##$part##&e'")
                            return true
                        }
                    }
                    else -> {
                        player.message("&eUnexpected type at path part: '&b##$part##&e'")
                        return true
                    }
                }
            }

            val parsedValue = when (currentExpectedType) {
                is List<*> -> {
                    val firstType = currentExpectedType.firstOrNull()
                    ArrayList<Any>(valueParts.size).apply {
                        for (part in valueParts) {
                            add(when (firstType) {
                                is String, String::class.java -> part
                                is Int, Int::class.java -> part.toIntOrNull() ?: 0
                                is Boolean, Boolean::class.java -> part.toBooleanStrictOrNull() ?: false
                                else -> part
                            })
                        }
                    }
                }
                String::class.java -> newValueString
                Int::class.java -> newValueString.toIntOrNull() ?: run {
                    player.message("&eInvalid integer: &b##$newValueString##")
                    return true
                }
                Double::class.java -> newValueString.toDoubleOrNull() ?: run {
                    player.message("&eInvalid double: &b##$newValueString##")
                    return true
                }
                Boolean::class.java -> newValueString.toBooleanStrictOrNull() ?: run {
                    player.message("&eInvalid boolean: &b##$newValueString##")
                    return true
                }
                else -> {
                    player.message("&eUnsupported type: &b##${currentExpectedType?.toString() ?: "unknown"}##")
                    return true
                }
            }

            val rawCurrentValue = MainConfig.get(element)
            if (rawCurrentValue is Map<*, *> || rawCurrentValue is ConfigurationSection) {
                val currentMap = when(rawCurrentValue) {
                    is Map<*, *> -> {
                        if (rawCurrentValue.keys.all { it is String }) {
                            @Suppress("UNCHECKED_CAST")
                            HashMap<String, Any>((rawCurrentValue as Map<String, Any>))
                        } else {
                            player.message("&eInvalid map keys in configuration")
                            return false
                        }
                    }
                    is ConfigurationSection -> HashMap<String, Any>((rawCurrentValue).toMap())
                    else -> HashMap<String, Any>()
                }

                var nestedMap: MutableMap<String, Any> = currentMap
                for (key in pathParts.dropLast(1)) {
                    val next = nestedMap[key]
                    if (next is MutableMap<*, *>) {
                        if (next.keys.all { it is String }) {
                            @Suppress("UNCHECKED_CAST")
                            nestedMap = next as MutableMap<String, Any>
                        } else {
                            player.message("&eInvalid nested map keys")
                            return false
                        }
                    } else {
                        val newMap = HashMap<String, Any>()
                        nestedMap[key] = newMap
                        nestedMap = newMap
                    }
                }
                nestedMap[pathParts.last()] = parsedValue
                MainConfig.set(element, currentMap)
            } else {
                if (pathParts.isNotEmpty()) {
                    player.message("&eCannot set path for non-map element.")
                    return false
                }
                MainConfig.set(element, parsedValue)
            }

            player.message("&e Updated &b##$element${if (pathParts.isNotEmpty()) "## &eat &d##${pathParts.joinToString(".")}" else ""}## &eto &2##$parsedValue##")
        }

        else if (args[0].lowercase() == "get") {
            if (args.size < 3) {
                if (args.size < 2) {
                    player.message("&eNo category specified")
                    return true
                }
                player.message("&eNo setting specified")
                return true
            }

            val section = args[1]
            val element = args[2]
            val pathParts = args.drop(3)

            val category = ConfigCategoryManagement.configCategories[section] ?: run {
                player.message("&eSetting &b##$section## &eis invalid")
                return true
            }

            // Verify the category is valid
            val expectedClass = category::class.nestedClasses.firstOrNull { it.simpleName == "Expected" }?.objectInstance ?: run {
                player.message("&eSetting &b##$section## &ehas no properties to get.")
                return true
            }

            // Verify the property exists
            val expectedProp = expectedClass::class.memberProperties.asSequence()
                .filter { it.visibility == KVisibility.PUBLIC }
                .mapNotNull { it.getter.call(expectedClass) as? ExpectedConfigProperty }
                .firstOrNull { it.name == element } ?: run {
                player.message("&eInvalid category: &b##$element##")
                return true
            }

            // Get the current value from config
            val rawValue = MainConfig.get(element) ?: run {
                player.message("&eConfiguration value for &b##$element## &enot found")
                return true
            }

            // If no path parts, display the entire value
            if (pathParts.isEmpty()) {
                displayValue(player, element, rawValue)
                return true
            }

            // Navigate through path parts
            var currentValue: Any? = rawValue
            var currentPath = element

            for (part in pathParts) {
                currentPath += ".$part"
                when (currentValue) {
                    is Map<*, *> -> {
                        if (currentValue.keys.all { it is String }) {
                            @Suppress("UNCHECKED_CAST")
                            val typedMap = currentValue as Map<String, *>
                            currentValue = typedMap[part] ?: run {
                                player.message("&eInvalid path part: '&b##$part##&e'")
                                return true
                            }
                        } else {
                            player.message("&eInvalid map keys at path part: '&b##$part##&e'")
                            return true
                        }
                    }
                    is ConfigurationSection -> {
                        currentValue = if (currentValue.contains(part)) {
                            currentValue.get(part)
                        } else {
                            player.message("&eInvalid path part: '&b##$part##&e'")
                            return true
                        }
                    }
                    is List<*> -> {
                        val index = part.toIntOrNull() ?: run {
                            player.message("&eInvalid list index: '&b##$part##&e'")
                            return true
                        }

                        if (index < 0 || index >= currentValue.size) {
                            player.message("&eList index out of bounds: '&b##$part##&e'")
                            return true
                        }

                        currentValue = currentValue[index]
                        currentPath = "$currentPath[$index]"
                    }
                    else -> {
                        player.message("&eCannot navigate further from type: ${currentValue?.javaClass?.simpleName ?: "null"}")
                        return true
                    }
                }
            }

            displayValue(player, currentPath, currentValue)
            return true
        }

        return true
    }

    /**
     * Displays a configuration value to the player in a formatted way.
     *
     * @param player The player to send the message to
     * @param path The configuration path being displayed
     * @param value The value to display
     */
    private fun displayValue(player: Player, path: String, value: Any?) {
        when (value) {
            null -> player.message("&eValue at &b##$path## &eis null")
            is Map<*, *> -> {
                player.message("&eValue at &b##$path## &eis a Map with ${value.size} entries:")
                value.entries.take(10).forEach { (k, v) ->
                    val displayValue = when (v) {
                        is Map<*, *> -> "Map(${v.size} entries)"
                        is List<*> -> "List(${v.size} items)"
                        else -> v.toString().take(50)
                    }
                    player.message("&e  - &b##$k## &e= &2##$displayValue##")
                }
                if (value.size > 10) {
                    player.message("&e  ... and ${value.size - 10} more entries")
                }
            }
            is List<*> -> {
                player.message("&eValue at &b##$path## &eis a List with ${value.size} items:")
                value.take(10).forEachIndexed { index, item ->
                    val displayValue = when (item) {
                        is Map<*, *> -> "Map(${item.size} entries)"
                        is List<*> -> "List(${item.size} items)"
                        else -> item.toString().take(50)
                    }
                    player.message("&e  $index: &2##$displayValue##")
                }
                if (value.size > 10) {
                    player.message("&e  ... and ${value.size - 10} more items")
                }
            }
            is ConfigurationSection -> {
                val map = value.toMap()
                displayValue(player, path, map)
            }
            else -> player.message("&eValue at &b##$path## &eis: &2##$value##")
        }
    }

    /**
     * Splits command arguments into path parts and value parts.
     *
     * Handles quoted values to allow spaces within a single argument.
     *
     * @param args The list of arguments to split
     * @return A pair containing the path parts and value parts
     */
    private fun splitPathAndValue(args: List<String>): Pair<List<String>, List<String>> {
        if (args.size == 2 && !args[0].contains("\"") && !args[1].contains("\"")) {
            return Pair(listOf(args[0]), listOf(args[1]))
        }

        val path = ArrayList<String>(1)
        val values = ArrayList<String>(args.size - 1)
        var sb = StringBuilder(32)
        var inQuotes = false
        var isCollectingPath = true

        for (arg in args) {
            if (!inQuotes && arg.startsWith("\"")) {
                inQuotes = true
                isCollectingPath = false
                sb.append(arg.substring(1))
            } else if (inQuotes && arg.endsWith("\"")) {
                inQuotes = false
                sb.append(' ').append(arg.substring(0, arg.length - 1))
                values.add(sb.toString())
                sb = StringBuilder(32)
            } else if (inQuotes) {
                sb.append(' ').append(arg)
            } else {
                if (isCollectingPath) {
                    path.add(arg)
                    if (path.size == 1) isCollectingPath = false
                } else {
                    values.add(arg)
                }
            }
        }

        if (!inQuotes && sb.isNotEmpty()) {
            values.add(sb.toString())
        }

        return Pair(path, values)
    }

    /**
     * Provides tab completion for the config command.
     *
     * Suggests available subcommands, categories, properties, and nested paths
     * based on the current input.
     *
     * @param sender The command sender
     * @param command The command being executed
     * @param alias The alias used
     * @param args The current arguments
     * @return A list of possible completions, or null if none are available
     */
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        if (args.isEmpty()) return null

        return when (args.size) {
            1 -> StringUtil.copyPartialMatches(args[0], firstArgs.toList(), ArrayList(3))
            2 -> if (args[0].lowercase() in setGetCommands) StringUtil.copyPartialMatches(args[1], sortedCategoryNames, ArrayList(sortedCategoryNames.size)) else null
            3 -> categoryProperties[args[1]]?.let { StringUtil.copyPartialMatches(args[2], it, ArrayList(it.size)) }
            else -> {
                if (args[0].lowercase() in setGetCommands) {
                    val section = args[1]
                    val element = args[2]
                    val path = args.drop(3)

                    val categoryObject = ConfigCategoryManagement.configCategories[section] ?: return null
                    val expectedClass = categoryObject::class.nestedClasses
                        .firstOrNull { it.simpleName == "Expected" }
                        ?.objectInstance ?: return null

                    val expectedProp = expectedClass::class.memberProperties
                        .asSequence()
                        .filter { it.visibility == KVisibility.PUBLIC }
                        .mapNotNull { it.getter.call(expectedClass) as? ExpectedConfigProperty }
                        .firstOrNull { it.name == element } ?: return null

                    var current: Any? = expectedProp.default
                    for (key in path.dropLast(1)) {
                        if (current is Map<*, *> && current.keys.all { it is String }) {
                            @Suppress("UNCHECKED_CAST")
                            current = (current as Map<String, *>)[key]
                        } else {
                            return null
                        }
                    }

                    if (current is Map<*, *> && current.keys.all { it is String }) {
                        @Suppress("UNCHECKED_CAST")
                        val keys = (current as Map<String, *>).keys.toList()
                        return StringUtil.copyPartialMatches(args.last(), keys, ArrayList(keys.size))
                    }
                }
                null
            }
        }
    }

    /**
     * Helper object that manages configuration categories and their properties.
     *
     * Provides access to configuration structure for command handling and tab completion.
     */
    private object ConfigCategoryManagement {
        /** Map of category names to their corresponding objects */
        val configCategories: Map<String, Any> by lazy {
            val result = HashMap<String, Any>()
            for (clazz in ConfigSanitizer::class.nestedClasses) {
                val instance = clazz.objectInstance ?: continue
                val name = instance::class.memberProperties
                    .firstOrNull { it.name == "NAME" }
                    ?.getter?.call() as? String ?: continue
                result[name] = instance
            }
            result
        }

        /** Sorted list of category names for consistent display */
        val sortedCategoryNames by lazy { configCategories.keys.sorted() }

        /** Map of category names to their available property names */
        val categoryNames: Map<String, List<String>> by lazy {
            val result = HashMap<String, List<String>>()
            for ((name, obj) in configCategories) {
                val expectedClass = obj::class.nestedClasses
                    .firstOrNull { it.simpleName == "Expected" }
                    ?.objectInstance ?: continue

                val props = ArrayList<String>()
                for (prop in expectedClass::class.memberProperties) {
                    if (prop.visibility != KVisibility.PUBLIC) continue
                    val configProp = prop.getter.call(expectedClass) as? ExpectedConfigProperty ?: continue
                    props.add(configProp.name)
                }

                if (props.isNotEmpty()) {
                    props.sort()
                    result[name] = props
                }
            }
            result
        }
    }
}