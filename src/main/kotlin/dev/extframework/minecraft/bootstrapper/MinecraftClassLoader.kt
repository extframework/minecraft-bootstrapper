package dev.extframework.minecraft.bootstrapper

import dev.extframework.boot.loader.MutableClassLoader
import dev.extframework.boot.loader.MutableSourceProvider
import java.nio.ByteBuffer

internal class MinecraftClassLoader(
    parent: ClassLoader,
    defineClasses: Boolean
) : MutableClassLoader(
    name = "Minecraft classloader",
    sources = object : MutableSourceProvider(mutableListOf()) {
        override fun findSource(name: String): ByteBuffer? {
            return if (defineClasses)  super.findSource(name)
            else null
        }
    },
    parent = parent
)