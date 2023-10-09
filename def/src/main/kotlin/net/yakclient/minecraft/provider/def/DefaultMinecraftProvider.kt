package net.yakclient.minecraft.provider.def

import com.durganmcbroom.jobs.JobResult
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.ClassIdentifier
import net.yakclient.archive.mapper.MappingType
import net.yakclient.archive.mapper.MethodIdentifier
import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.store.DelegatingDataStore
import net.yakclient.launchermeta.handler.LaunchMetadata
import net.yakclient.minecraft.bootstrapper.MinecraftHandle
import net.yakclient.minecraft.bootstrapper.MinecraftProvider
import java.nio.file.Path

public class DefaultMinecraftProvider : MinecraftProvider<DefaultMinecraftReference> {
    override suspend fun getReference(version: String, cachePath: Path): JobResult< DefaultMinecraftReference, Throwable> {
        return loadMinecraftRef(version, cachePath, DelegatingDataStore(LaunchMetadataDataAccess(cachePath)))
    }

    override fun get(ref: DefaultMinecraftReference, parent: ClassLoader): MinecraftHandle {
        val (handle, libraries, manifest) = loadMinecraft(ref, parent)

        return DefaultMinecraftHandle(handle, libraries, manifest, ref.mappings)
    }

    private class DefaultMinecraftHandle(
        override val archive: ArchiveHandle,
        override val libraries: List<ArchiveHandle>,
        private val manifest: LaunchMetadata,
        private val mappings: ArchiveMapping,
    ) : MinecraftHandle {
        override fun start(args: Array<String>) {
            archive.classloader.loadClass(manifest.mainClass).getMethod("main", Array<String>::class.java)
                .invoke(null, args)
        }

        override fun shutdown() {
            val mapping = mappings.classes[ClassIdentifier(
                "net/minecraft/client/Minecraft", MappingType.REAL
            )]
            checkNotNull(mapping) { "Could not find mapping for 'net.minecraft.client.Minecraft'" }

            val minecraftClass = archive.classloader.loadClass(mapping.fakeIdentifier.name.replace('/', '.'))
            val minecraft = minecraftClass.getMethod(
                checkNotNull(
                    mapping.methods[MethodIdentifier(
                        "getInstance",
                        listOf(),
                        MappingType.REAL
                    )]?.fakeIdentifier?.name
                )
                { "Couldnt map method 'net.minecraft.client.Minecraft getInstance()', is this minecraft loader out of date?" }
            ).invoke(null)
            checkNotNull(minecraft) { "Couldnt get minecraft instance, has it fully started?" }

            minecraftClass.getMethod(
                checkNotNull(
                    mapping.methods[MethodIdentifier(
                        "stop",
                        listOf(),
                        MappingType.REAL
                    )]?.fakeIdentifier?.name
                )
                { "Couldnt map method 'net.minecraft.client.Minecraft stop()', is this minecraft loader out of date?" }
            ).invoke(minecraft)
        }
    }
}