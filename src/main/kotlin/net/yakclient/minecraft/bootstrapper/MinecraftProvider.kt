package net.yakclient.minecraft.bootstrapper

import java.nio.file.Path

public interface MinecraftProvider {
    public fun get(version: String, dumpPath: Path) : MinecraftAppInstance
}