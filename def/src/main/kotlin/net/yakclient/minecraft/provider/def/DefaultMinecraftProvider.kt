package net.yakclient.minecraft.provider.def

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.store.DelegatingDataStore
import net.yakclient.launchermeta.handler.LaunchMetadata
import net.yakclient.minecraft.bootstrapper.MinecraftHandle
import net.yakclient.minecraft.bootstrapper.MinecraftProvider
import java.nio.file.Path

public class DefaultMinecraftProvider : MinecraftProvider<DefaultMinecraftReference> {
    override fun getReference(version: String, cachePath: Path): DefaultMinecraftReference {
        return loadMinecraftRef(version, cachePath, DelegatingDataStore(LaunchMetadataDataAccess(cachePath)))
    }

    override fun get(ref: DefaultMinecraftReference): MinecraftHandle {
        val (handle, manifest) = loadMinecraft(ref)

        return DefaultMinecraftHandle(handle, manifest)
    }

    private class DefaultMinecraftHandle(
        override val archive: ArchiveHandle,
        private val manifest: LaunchMetadata
    ): MinecraftHandle {
        override fun start(args: Array<String>) {
            archive.classloader.loadClass(manifest.mainClass).getMethod("main", Array<String>::class.java).invoke(null, args)
        }

        override fun shutdown() {
            TODO()
        }
    }
}