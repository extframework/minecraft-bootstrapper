package net.yakclient.minecraft.bootstrapper

import net.yakclient.archives.ArchiveHandle
import java.nio.file.Path

public interface MinecraftProvider<T: MinecraftReference> {
    public fun getReference(version: String, cachePath: Path) : T

    public fun get(ref: T) : MinecraftHandle
}