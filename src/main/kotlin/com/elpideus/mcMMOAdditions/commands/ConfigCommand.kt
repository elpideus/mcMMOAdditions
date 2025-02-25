package com.elpideus.mcMMOAdditions.commands

import com.elpideus.mcMMOAdditions.config.ConfigSanitizer
import com.elpideus.mcMMOAdditions.config.ExpectedConfigField
import com.elpideus.mcMMOAdditions.config.ExpectedConfigProperty
import com.elpideus.mcMMOAdditions.config.MainConfig
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.util.StringUtil
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

class ConfigCommand : CommandExecutor, TabCompleter {
    private val firstArgs = listOf("set", "get", "settings")
    private val setGetCommands = setOf("set", "get")
    private val sortedCategoryNames by lazy { ConfigCategoryManagement.sortedCategoryNames }
    private val categoryProperties by lazy { ConfigCategoryManagement.categoryNames }

    private fun ConfigurationSection.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        for (key in getKeys(false)) {
            val value = get(key)
            map[key] = when (value) {
                is ConfigurationSection -> value.toMap()
                is List<*> -> value.map { if (it is ConfigurationSection) it.toMap() else it }
                else -> value ?: ""
            }
        }
        return map
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("You need to specify some arguments.")
            return false
        }

        when (args[0].lowercase()) {
            "set" -> {
                if (args.size < 4) {
                    sender.sendMessage("Usage: /config set <section> <element> <path...> <value>")
                    return false
                }

                val section = args[1]
                val element = args[2]
                val remainingArgs = args.drop(3).toList()

                if (remainingArgs.isEmpty()) {
                    sender.sendMessage("Usage: /config set <section> <element> <path...> <value>")
                    return false
                }

                val (pathParts, valueParts) = splitPathAndValue(remainingArgs)
                val newValueString = valueParts.joinToString(" ").removeSurrounding("\"")
                val path = pathParts

                // Step 1: Retrieve ExpectedConfigProperty for the section and element
                val category = ConfigCategoryManagement.configCategories[section] ?: run {
                    sender.sendMessage("Invalid section: $section")
                    return false
                }
                val expectedClass = category::class.nestedClasses.firstOrNull { it.simpleName == "Expected" }?.objectInstance
                    ?: run {
                        sender.sendMessage("Section $section has no Expected properties.")
                        return false
                    }
                val expectedProp = expectedClass::class.memberProperties
                    .asSequence()
                    .filter { it.visibility == KVisibility.PUBLIC }
                    .mapNotNull { prop ->
                        prop.getter.call(expectedClass) as? ExpectedConfigProperty
                    }
                    .firstOrNull { it.name == element } ?: run {
                    sender.sendMessage("Invalid element: $element")
                    return false
                }

                // Step 2: Traverse the path to determine the expected type
                var currentExpectedType: Any? = expectedProp.expectedType
                for (part in pathParts) {
                    currentExpectedType = when (currentExpectedType) {
                        is Map<*, *> -> {
                            val map = currentExpectedType as? Map<String, *>
                            val field = map?.get(part) as? ExpectedConfigField
                            field?.value ?: run {
                                sender.sendMessage("Invalid path part: '$part'")
                                return false
                            }
                        }
                        else -> {
                            sender.sendMessage("Unexpected type at path part: '$part'")
                            return false
                        }
                    }
                }

                // Step 3: Parse the new value based on the expected type
                val parsedValue = when (currentExpectedType) {
                    is List<*> -> {
                        val elementType = (currentExpectedType as List<*>).firstOrNull() ?: Any::class.java
                        remainingArgs.mapNotNull { arg ->
                            when (elementType) {
                                String::class.java -> arg
                                Int::class.java -> arg.toIntOrNull()
                                Boolean::class.java -> arg.toBooleanStrictOrNull()
                                else -> {
                                    sender.sendMessage("Unsupported type in list: ${elementType.toString()}")
                                    null
                                }
                            }
                        }
                    }

                    String::class.java -> newValueString
                    Int::class.java -> newValueString.toIntOrNull() ?: run {
                        sender.sendMessage("Invalid integer: $newValueString")
                        return false
                    }

                    Boolean::class.java -> newValueString.toBooleanStrictOrNull() ?: run {
                        sender.sendMessage("Invalid boolean: $newValueString")
                        return false
                    }

                    else -> {
                        sender.sendMessage("Unsupported type: ${currentExpectedType?.toString() ?: "unknown"}")
                        return false
                    }
                }

                // Step 4: Update the configuration
                val rawCurrentValue = MainConfig.get(element)
                if (rawCurrentValue is Map<*, *> || rawCurrentValue is ConfigurationSection) {
                    // Handle nested maps or configuration sections
                    val currentMap = (rawCurrentValue as? Map<String, Any>)?.toMutableMap()
                        ?: (rawCurrentValue as ConfigurationSection).toMap().toMutableMap()
                    var nestedMap: MutableMap<String, Any> = currentMap
                    for (key in pathParts.dropLast(1)) {
                        nestedMap = nestedMap.getOrPut(key) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
                    }
                    nestedMap[pathParts.last()] = parsedValue
                    MainConfig.set(element, currentMap)
                } else {
                    if (pathParts.isNotEmpty()) {
                        sender.sendMessage("Cannot set path for non-map element.")
                        return false
                    }
                    MainConfig.set(element, parsedValue)
                }

                sender.sendMessage("Updated $element${if (pathParts.isNotEmpty()) " at ${pathParts.joinToString(".")}" else ""} to $parsedValue")
            }
        }
        return true
    }

    private fun splitPathAndValue(args: List<String>): Pair<List<String>, List<String>> {
        val path = mutableListOf<String>()
        val value = mutableListOf<String>()
        var currentArgument = ""
        var inQuotes = false

        args.forEachIndexed { index, arg ->
            if (index == args.size - 1 || !arg.endsWith("\"")) {
                if (inQuotes) {
                    // We are inside a quoted string, append this argument to the current building argument
                    currentArgument += " ${arg.removePrefix("\"").removeSuffix("\"")}"
                } else {
                    // This is a normal argument, add it to the path or value
                    if (currentArgument.isNotBlank()) {
                        if (path.isEmpty()) {
                            path.add(currentArgument.trim())
                        } else {
                            value.add(currentArgument.trim())
                        }
                        currentArgument = ""
                    }
                    if (index == args.size - 1) {
                        if (path.isEmpty()) {
                            path.add(arg)
                        } else {
                            value.add(arg)
                        }
                    } else {
                        path.add(arg)
                    }
                }
            } else {
                // We are at the end of a quoted string, add the completed argument to the value
                currentArgument += " ${arg.removePrefix("\"").removeSuffix("\"")}"
                value.add(currentArgument.trim())
                currentArgument = ""
                inQuotes = false
            }

            if (arg.startsWith("\"") && !inQuotes) {
                inQuotes = true
            }
        }
        return Pair(path, value)
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        if (args.isEmpty()) return null

        return when (args.size) {
            1 -> StringUtil.copyPartialMatches(args[0], firstArgs, ArrayList(3))
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
                        .mapNotNull { prop ->
                            prop.getter.call(expectedClass) as? ExpectedConfigProperty
                        }
                        .firstOrNull { it.name == element } ?: return null

                    var current: Any? = expectedProp.default
                    for (key in path.dropLast(1)) {
                        current = (current as? Map<*, *>)?.get(key)
                    }

                    val currentMap = current as? Map<*, *>
                    val keys = currentMap?.keys?.filterIsInstance<String>()?.toList() ?: emptyList()
                    StringUtil.copyPartialMatches(args.last(), keys, ArrayList(keys.size))
                } else {
                    null
                }
            }
        }
    }

    private object ConfigCategoryManagement {
        val configCategories: Map<String, Any> by lazy {
            ConfigSanitizer::class.nestedClasses
                .mapNotNull { it.objectInstance }
                .associateByTo(LinkedHashMap()) { nestedObject ->
                    nestedObject::class.memberProperties
                        .firstOrNull { it.name == "NAME" }
                        ?.getter?.call() as? String
                }
                .filterKeys { it != null }
                .mapValues { it.value } as Map<String, Any>
        }

        val sortedCategoryNames by lazy { configCategories.keys.sorted() }

        val categoryNames: Map<String, List<String>> by lazy {
            configCategories.mapNotNull { (name, obj) ->
                obj::class.nestedClasses
                    .firstOrNull { it.simpleName == "Expected" }
                    ?.objectInstance
                    ?.let { ec ->
                        ec::class.memberProperties
                            .asSequence()
                            .filter { it.visibility == KVisibility.PUBLIC }
                            .mapNotNull { prop ->
                                prop.getter.call(ec) as? ExpectedConfigProperty
                            }
                            .map { it.name }
                            .sorted()
                            .toList()
                            .takeIf { it.isNotEmpty() }
                            ?.let { name to it }
                    }
            }.toMap()
        }

    }
}