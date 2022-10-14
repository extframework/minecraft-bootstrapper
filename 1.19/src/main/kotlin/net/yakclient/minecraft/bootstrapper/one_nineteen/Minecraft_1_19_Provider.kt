package net.yakclient.minecraft.bootstrapper.one_nineteen

import net.yakclient.boot.store.DelegatingDataStore
import net.yakclient.minecraft.bootstrapper.MinecraftAppInstance
import net.yakclient.minecraft.bootstrapper.MinecraftProvider
import java.nio.file.Path

@Suppress("ClassName")
public class Minecraft_1_19_Provider : MinecraftProvider<Minecraft_1_19_Reference> {
    override fun getReference(version: String, dumpPath: Path): Minecraft_1_19_Reference {
        return loadMinecraftRef(version, dumpPath, DelegatingDataStore(MCManifestDataAccess(dumpPath)))
    }

    override fun get(ref: Minecraft_1_19_Reference): MinecraftAppInstance {
        val (handle, manifest) = loadMinecraft(ref)

        return Minecraft_1_19_App(handle, manifest)
    }
}