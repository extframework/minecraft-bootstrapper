package net.yakclient.minecraft.provider._default

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.store.DelegatingDataStore
import net.yakclient.minecraft.bootstrapper.MinecraftHandle
import net.yakclient.minecraft.bootstrapper.MinecraftProvider
import java.nio.file.Path

public class DefaultMinecraftProvider : MinecraftProvider<DefaultMinecraftReference> {
    override fun getReference(version: String, cachePath: Path): DefaultMinecraftReference {
        return loadMinecraftRef(version, cachePath, DelegatingDataStore(MCManifestDataAccess(cachePath)))
    }

    override fun get(ref: DefaultMinecraftReference): MinecraftHandle {
        val (handle, manifest) = loadMinecraft(ref)

        return DefaultMinecraftHandle(handle, manifest)
    }

    private class DefaultMinecraftHandle(
        override val archive: ArchiveHandle,
        private val manifest: ClientManifest
    ): MinecraftHandle {
        override fun start(args: Array<String>) {
            archive.classloader.loadClass(manifest.mainClass).getMethod("main", Array<String>::class.java).invoke(null, args)
        }
    }
}