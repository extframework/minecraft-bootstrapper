package net.yakclient.minecraft.bootstrapper.one_nineteen

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.boot.store.DelegatingDataStore
import net.yakclient.minecraft.bootstrapper.MinecraftAppInstance
import net.yakclient.minecraft.bootstrapper.MinecraftProvider
import java.nio.file.Path

@Suppress("ClassName")
public class Minecraft_1_19_Provider : MinecraftProvider {
    override fun get(version: String, dumpPath: Path): MinecraftAppInstance {
        val (handle, manifest) = loadMinecraft(version, dumpPath, DelegatingDataStore(MCManifestDataAccess(dumpPath)))

        return Minecraft_1_19_App(handle, manifest)
    }

}