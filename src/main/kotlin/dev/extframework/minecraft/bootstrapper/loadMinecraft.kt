package dev.extframework.minecraft.bootstrapper

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.maven.MavenLikeResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

@JvmOverloads
public fun loadMinecraft(
    version: String,

    repository: SimpleMavenRepositorySettings,
    cache: Path,

    archiveGraph: ArchiveGraph,
    maven: MavenLikeResolver<ClassLoadedArchiveNode<SimpleMavenDescriptor>, *>,

    providerFinder: MinecraftProviderFinder = MinecraftProviderRemoteLookup(cache),
) : Job<MinecraftNode> = job {
    val descriptor = providerFinder.find(version)

    job(JobName("Load minecraft handler")) {
        val minecraftHandler = MinecraftHandler(
            version,
            cache,
            archiveGraph.loadProvider(descriptor, maven, repository)().merge(),
            archiveGraph,
        )

        // This has to be Dispatchers.IO or a class circularity error will get thrown.
        runBlocking(Dispatchers.IO) {
            minecraftHandler.loadMinecraft()().merge()
        }
    }().merge()
}