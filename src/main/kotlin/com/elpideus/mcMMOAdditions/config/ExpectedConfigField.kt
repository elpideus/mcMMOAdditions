package com.elpideus.mcMMOAdditions.config

data class ExpectedConfigField (
    val value: Any,
    val required: Boolean = false,
    val outOfTheBoxDefault: Boolean = false
)