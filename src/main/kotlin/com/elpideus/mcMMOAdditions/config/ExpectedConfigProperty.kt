package com.elpideus.mcMMOAdditions.config

interface ExpectedConfigProperty {

    val name: String
    val default: Any
    val expectedType: Any

}