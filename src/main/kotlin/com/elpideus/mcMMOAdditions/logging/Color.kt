package com.elpideus.mcMMOAdditions.logging

/**
 * Enum representing various color codes for terminal text formatting.
 *
 * Each enum constant corresponds to a color or text effect supported by ANSI escape codes, which can be used to style
 * text in terminal environments. These colors are typically used for console output formatting, and the `toString()`
 * method provides the appropriate ANSI escape code to apply the color or effect.
 */
enum class Color(private val colorCode: String) {

    /**
     * Resets any previous text formatting.
     */
    RESET("\u001B[0m"),

    /**
     * Applies bold formatting to text.
     */
    BOLD("\u001B[1m"),

    /**
     * Applies underline formatting to text.
     */
    UNDERLINE("\u001B[4m"),

    /**
     * Applies strikethrough formatting to text.
     */
    STRIKETHROUGH("\u001B[9m"),

    /**
     * Applies italic formatting to text.
     */
    ITALIC("\u001B[3m"),

    /**
     * Sets the text color to black.
     */
    BLACK("\u001B[30m"),

    /**
     * Sets the text color to dark blue.
     */
    DARK_BLUE("\u001B[34m"),

    /**
     * Sets the text color to green.
     */
    GREEN("\u001B[32m"),

    /**
     * Sets the text color to dark aqua.
     */
    DARK_AQUA("\u001B[36m"),

    /**
     * Sets the text color to dark red.
     */
    DARK_RED("\u001B[31m"),

    /**
     * Sets the text color to dark purple.
     */
    DARK_PURPLE("\u001B[35m"),

    /**
     * Sets the text color to gold.
     */
    GOLD("\u001B[33m"),

    /**
     * Sets the text color to gray.
     */
    GRAY("\u001B[37m"),

    /**
     * Sets the text color to dark gray.
     */
    DARK_GRAY("\u001B[90m"),

    /**
     * Sets the text color to blue.
     */
    BLUE("\u001B[94m"),

    /**
     * Sets the text color to aqua.
     */
    AQUA("\u001B[96m"),

    /**
     * Sets the text color to red.
     */
    RED("\u001B[91m"),

    /**
     * Sets the text color to light purple.
     */
    LIGHT_PURPLE("\u001B[35m"),

    /**
     * Sets the text color to yellow.
     */
    YELLOW("\u001B[93m"),

    /**
     * Sets the text color to white.
     */
    WHITE("\u001B[97m"),

    /**
     * Applies a "magic" effect (typically used for scrambling text).
     */
    MAGIC("\u001B[8m");

    /**
     * Returns the corresponding ANSI escape code for the color or effect.
     *
     * This method is overridden to return the string representation of the ANSI escape code for each color or effect.
     *
     * @return The ANSI escape code as a string.
     */
    override fun toString(): String = colorCode
}
