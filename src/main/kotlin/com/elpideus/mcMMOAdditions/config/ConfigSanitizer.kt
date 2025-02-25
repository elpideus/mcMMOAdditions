package com.elpideus.mcMMOAdditions.config
import com.elpideus.mcMMOAdditions.Logging
import org.bukkit.configuration.MemorySection


object ConfigSanitizer {

    object MainConfigSanitizer {

        const val NAME: String = "main"

        object Expected {

            val PREFIX = object : ExpectedConfigProperty {
                override val name = "prefix"
                override val default = ExpectedConfigField(
                    value = "&8&l[&6&lmcMMO Additions&8&l]&r ", // Actual value stored here
                    required = true,
                    outOfTheBoxDefault = true
                )
                override val expectedType = String::class.java
            }

            val CONSOLE = object: ExpectedConfigProperty {
                override val name: String = "console"
                override val default = mapOf(
                    "add-prefix-space-automatically" to true
                )
                override val expectedType = mapOf(
                    "add-prefix-space-automatically" to ExpectedConfigField(Boolean::class.java)
                )
            }

            val CONFIG2 = object : ExpectedConfigProperty {
                override val name = "config2"
                override val default = mapOf(
                    "some-key" to "some-value",
                    "lets-try-int" to 2,
                    "a-list" to listOf("banana", "pineapple", "pen", "apple"),
                    "another-object" to mapOf(
                        "some-element" to mapOf(
                            "sub-element" to "Hello"
                        ),
                        "some-other-list" to listOf("urmom", "you")
                    )
                )
                override val expectedType = mapOf(
                    "some-key" to ExpectedConfigField(String::class.java, required = true, outOfTheBoxDefault = true),
                    "lets-try-int" to ExpectedConfigField(Int::class.java),
                    "a-list" to ExpectedConfigField(listOf(String::class.java)),
                    "another-object" to ExpectedConfigField(
                        mapOf(
                            "some-element" to ExpectedConfigField(
                                mapOf(
                                    "sub-element" to ExpectedConfigField(String::class.java)
                                )
                            ),
                            "some-other-list" to ExpectedConfigField(listOf(String::class.java))
                        ),
                    )
                )
            }

        }

        fun sanitize() {
            val prepareToSanitize = listOf(Expected.PREFIX, Expected.CONFIG2, Expected.CONSOLE)

            val sanitizedExpected = linkedMapOf<String, Any?>()
            prepareToSanitize.forEach { expectedProp ->
                val currentValue = MainConfig.config.get(expectedProp.name)
                val sanitizedValue = sanitizeValue(currentValue, expectedProp.default, expectedProp.expectedType, expectedProp.name)
                sanitizedExpected[expectedProp.name] = sanitizedValue
            }

            val remainingKeys = linkedMapOf<String, Any?>()
            MainConfig.config.getKeys(false).forEach { key ->
                if (!sanitizedExpected.containsKey(key)) remainingKeys[key] = MainConfig.config.get(key)
            }

            val newConfigMap = linkedMapOf<String, Any?>().apply {
                putAll(sanitizedExpected)
                putAll(remainingKeys)
            }

            MainConfig.config.getKeys(false).forEach { key -> MainConfig.config.set(key, null) }
            newConfigMap.forEach { (key, value) -> MainConfig.config.set(key, value) }

            MainConfig.save()
            MainConfig.reload()
        }

        private fun sanitizeValue(currentValue: Any?, default: Any, expectedType: Any, key: Any): Any? {

            fun log (content: String) {
                Logging.info("&8(&2Sanitizing&8)&r $content&r")
            }

            return when (expectedType) {
                is Map<*, *> -> {
                    val expectedFields = expectedType as Map<String, ExpectedConfigField>
                    val currentMap = when (currentValue) {
                        is Map<*, *> -> currentValue.mapKeys { it.key.toString() }.toMutableMap()
                        is MemorySection -> currentValue.getValues(false).mapKeys { it.key.toString() }.toMutableMap()
                        else -> mutableMapOf()
                    }

                    val sanitizedMap = mutableMapOf<String, Any>()

                    expectedFields.forEach { (locKey, expectedField) ->
                        val currentSubValue = currentMap[locKey]
                        // Extract value from ExpectedConfigField if default is one
                        val defaultSubValue = when (val defaultVal = (default as Map<*, *>)[locKey]) {
                            is ExpectedConfigField -> defaultVal.value
                            else -> defaultVal
                        }

                        if (currentSubValue != null) {
                            val sanitizedSubValue = sanitizeValue(
                                currentSubValue,
                                defaultSubValue!!,
                                expectedField.value,
                                locKey
                            )
                            if (sanitizedSubValue != null) {
                                sanitizedMap[locKey] = sanitizedSubValue
                            }
                        } else if (expectedField.outOfTheBoxDefault) {
                            sanitizedMap[locKey] = defaultSubValue!!
                            log("&2+&r Added {&d$key/$locKey&r: &b${defaultSubValue}&r} to config.yml")
                        }
                    }

                    currentMap.forEach { (key, value) ->
                        if (key !in expectedFields) {
                            sanitizedMap[key] = value as Any
                            Logging.info("&c-&r Removed {&d$key&r: &b$value&r} from config.yml")
                        }
                    }

                    sanitizedMap
                }

                is List<*> -> {
                    val allowedTypes = (expectedType as List<*>).filterIsInstance<Class<*>>()
                    val currentList = currentValue as? List<*> ?: return if (currentValue == null) null else default
                    val sanitizedList = currentList.filter { element -> allowedTypes.any { it.isInstance(element) } }
                    if (sanitizedList.isEmpty()) null else sanitizedList
                }

                is ExpectedConfigField -> {
                    if (currentValue == null) {
                        if (expectedType.outOfTheBoxDefault) default else null
                    } else {
                        sanitizeValue(currentValue, default, expectedType.value, key)
                        Logging.info("Fixed {&d$key&r: &b$currentValue&r} to {&d$key&r: &b${expectedType.value}&r} in config.yml")
                    }
                }

                is Class<*> -> {
                    // Handle cases where the default might be an ExpectedConfigField
                    val effectiveDefault = when (default) {
                        is ExpectedConfigField -> default.value // Extract the value
                        else -> default
                    }

                    if (currentValue != null) {
                        // Special case for Int, since Kotlin Int maps to Java Integer
                        val expectedJavaType = if (expectedType == Int::class.java) Int::class.javaObjectType else expectedType

                        if (expectedJavaType.isInstance(currentValue)) {
                            currentValue
                        } else {
                            log("Changed {&d$key&r: &b$currentValue&r} to {&d$key&r: &b$effectiveDefault&r} in config.yml")
                            effectiveDefault
                        }
                    } else {
                        effectiveDefault
                    }
                }


                else -> {
                    // Handle other cases by unwrapping ExpectedConfigField if present
                    when (default) {
                        is ExpectedConfigField -> default.value
                        else -> {
                            log("Changed {&d$key&r: &b$currentValue&r} to {&d$key&r: &b$default&r} in config.yml")
                            default
                        }
                    }
                }
            }
        }
    }
}
