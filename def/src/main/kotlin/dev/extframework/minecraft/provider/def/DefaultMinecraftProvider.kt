package dev.extframework.minecraft.provider.def

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.ClassIdentifier
import dev.extframework.archive.mapper.MethodIdentifier
import dev.extframework.archive.mapper.parsers.proguard.ProGuardMappingParser
import dev.extframework.archives.ArchiveHandle
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.loader.MutableClassLoader
import dev.extframework.boot.store.DelegatingDataStore
import dev.extframework.launchermeta.handler.LaunchMetadata
import dev.extframework.minecraft.bootstrapper.MinecraftHandle
import dev.extframework.minecraft.bootstrapper.MinecraftProvider
import java.nio.file.Path
import kotlin.io.path.inputStream

public class DefaultMinecraftProvider : MinecraftProvider<DefaultMinecraftReference> {
    override fun getReference(
        version: String,
        cachePath: Path
    ): Job<DefaultMinecraftReference> {
            return loadMinecraftRef(version, cachePath, DelegatingDataStore(LaunchMetadataDataAccess(cachePath)))
    }

    override fun get(
        ref: DefaultMinecraftReference,
        archiveGraph: ArchiveGraph,
        classloader: MutableClassLoader
    ): Job<MinecraftHandle> =
        job(JobName("Resolve minecraft reference ('${ref.version}') into handle")) {
            val (handle, libraries, manifest) = loadMinecraft(ref, classloader, archiveGraph)().merge()

            DefaultMinecraftHandle(
                handle, libraries, manifest,
                ProGuardMappingParser("obf", "deobf").parse(
                    ref.mappings.inputStream()
                )
            )
        }
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
            "net/minecraft/client/Minecraft", "deobf"
        )]
        checkNotNull(mapping) { "Could not find mapping for 'net.minecraft.client.Minecraft'" }

        val minecraftClass = archive.classloader.loadClass(mapping.getIdentifier("obf")!!.name.replace('/', '.'))
        val minecraft = minecraftClass.getMethod(
            checkNotNull(
                mapping.methods[MethodIdentifier(
                    "getInstance",
                    listOf(),
                    "deobf"
                )]?.getIdentifier("obf")?.name
            )
            { "Couldnt map method 'net.minecraft.client.Minecraft getInstance()', is this minecraft loader out of date?" }
        ).invoke(null)
        checkNotNull(minecraft) { "Couldnt get minecraft instance, has it fully started?" }

        minecraftClass.getMethod(
            checkNotNull(
                mapping.methods[MethodIdentifier(
                    "stop",
                    listOf(),
                    "deobf"
                )]?.getIdentifier("obf")?.name
            )
            { "Couldnt map method 'net.minecraft.client.Minecraft stop()', is this minecraft loader out of date?" }
        ).invoke(minecraft)
    }
}
