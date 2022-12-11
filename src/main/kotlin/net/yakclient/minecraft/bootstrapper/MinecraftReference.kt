package net.yakclient.minecraft.bootstrapper

import net.yakclient.archive.mapper.MappedArchive
import net.yakclient.archives.ArchiveReference

public interface MinecraftReference {
    public val version: String
    public val archive: ArchiveReference
    public val mappings: MappedArchive
}
