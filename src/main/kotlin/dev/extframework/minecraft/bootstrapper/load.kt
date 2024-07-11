package dev.extframework.minecraft.bootstrapper

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.ResourceAlgorithm.SHA1
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.maven.MavenLikeResolver
import java.nio.file.Path

public fun load(
    version: String,

    repository: SimpleMavenRepositorySettings,
    cache: Path,

    archiveGraph: ArchiveGraph,
    maven: MavenLikeResolver<*, *>,

    loadClasses: Boolean = false
) : Job<MinecraftHandle> = job {
    val provider = MinecraftProviderFinder(cache)

    val descriptor = provider.find(version)

    job(JobName("Load minecraft handler")) {
        val minecraftHandler = MinecraftHandler(
            version,
            cache,
            archiveGraph.loadProvider(descriptor, maven, repository)().merge(),
            archiveGraph,
            loadClasses
        )

        minecraftHandler.loadMinecraft(ClassLoader.getSystemClassLoader())().merge()
    }().merge()
}