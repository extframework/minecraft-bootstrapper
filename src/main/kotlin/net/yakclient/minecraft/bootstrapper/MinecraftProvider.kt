package net.yakclient.minecraft.bootstrapper

import java.nio.file.Path

public interface MinecraftProvider<T: MinecraftReference> {
    public fun getReference(version: String, dumpPath: Path) : T

    public fun get(ref: T) : MinecraftAppInstance
}