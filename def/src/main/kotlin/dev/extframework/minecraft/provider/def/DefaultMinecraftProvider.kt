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

public class DefaultMinecraftProvider : MinecraftProvider {
    override fun get(
        version: String,
        cachePath: Path,
        archiveGraph: ArchiveGraph,
        classloader: MutableClassLoader
    ): Job<MinecraftHandle> = job(JobName("Resolve minecraft reference ('${version}') into handle")) {
        val ref = loadMinecraftRef(
            version, cachePath, DelegatingDataStore(LaunchMetadataDataAccess(cachePath))
        )().merge()

        val (handle, libraries) = loadMinecraft(ref, classloader, archiveGraph)().merge()

        DefaultMinecraftHandle(
            handle, libraries, ref.runtimeInfo,
        )
    }
}

private class DefaultMinecraftHandle(
    override val archive: ArchiveHandle,
    override val libraries: List<ArchiveHandle>,
    override val info: MinecraftHandle.GameRuntimeInfo,
) : MinecraftHandle
