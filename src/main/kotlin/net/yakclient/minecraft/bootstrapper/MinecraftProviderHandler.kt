package net.yakclient.minecraft.bootstrapper

import arrow.core.Either
import arrow.core.identity
import com.durganmcbroom.artifact.resolver.*
import net.yakclient.archives.JpmArchives
import net.yakclient.boot.archive.JpmResolutionProvider
import net.yakclient.boot.archive.handleOrChildren
import net.yakclient.boot.dependency.DependencyData
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.boot.store.DataStore
import net.yakclient.boot.util.toSafeResource
import net.yakclient.common.util.resource.SafeResource
import java.nio.file.Path
import java.util.*

private const val PROPERTY_FILE_LOCATION = "META-INF/minecraft-provider.properties"

private const val MINECRAFT_PROVIDER_CN = "provider-name"

public class MinecraftProviderHandler<T : ArtifactRequest<*>, R : RepositorySettings>(
    private val archiveWriter: (req: T, resource: SafeResource) -> Path,
    private val store: DataStore<T, DependencyData<T>>,
    private val dependencyGraph: DependencyGraph<T, *, R>,
    private val requestBuilder: (version: String) -> T,
) {
    private val archiveProvider = JpmResolutionProvider


    public fun get(version: String, settings: R): MinecraftProvider<*> {
        val req: T = requestBuilder(version)

        val data = store[req] ?: run {
            @Suppress("UNCHECKED_CAST")
            val repositoryFactory =
                dependencyGraph.repositoryFactory as RepositoryFactory<R, T, ArtifactStub<T, *>, ArtifactReference<*, ArtifactStub<T, *>>, ArtifactRepository<T, ArtifactStub<T, *>, *>>

            val repository = repositoryFactory.createNew(settings)

            val ref = repository.get(req).fold(
                { throw IllegalArgumentException("Failed to load minecraft provider for version: '$version'. Error was: '${(it)}'") },
                ::identity
            )

            ref.children.forEach {
                for (s in it.candidates) {
                    val candidateSettings =
                        (repository.stubResolver.repositoryResolver as RepositoryStubResolver<RepositoryStub, R>).resolve(
                            s
                        ).orNull() ?: continue

                    if (dependencyGraph.cacherOf(candidateSettings).cache(it.request as T).isRight()) break
                }
            }

            val jarPath = archiveWriter(
                req,
                checkNotNull(ref.metadata.resource) { "Archive cannot be null for provider version: '$version'." }.toSafeResource()
            )

            val value = DependencyData(
                req,
                jarPath,
                @Suppress("UNCHECKED_CAST")
                ref.children.map { it.request as T }
            )
            store.put(req, value)

            value
        }

        val children = data.children
            .map(dependencyGraph::get)
            .map {
                it.orNull()
                    ?: throw IllegalStateException("Failed to load dependency of minecraft version: '${(it as Either.Left).value}. This means your dependency graph is invalidated, please delete the game cache and restart.")
            }

        val resource = checkNotNull(data.archive) { "Archive cannot be null for provider version: '$version'." }

        val archive = archiveProvider.resolve(
            resource,
            {
                IntegratedLoader(
                    sp = ArchiveSourceProvider(it),
                    parent = this::class.java.classLoader
                )
            },
            children.flatMapTo(HashSet()) { it.handleOrChildren() } + JpmArchives.moduleToArchive(this::class.java.module))
            .fold({ throw it }, ::identity).archive

        val properties =
            checkNotNull(archive.classloader.getResourceAsStream(PROPERTY_FILE_LOCATION)) { "Failed to find Minecraft Provider properties file in given archive." }.use {
                Properties().apply { load(it) }
            }

        val providerClassName = properties.getProperty(MINECRAFT_PROVIDER_CN)
            ?: throw IllegalStateException("Invalid minecraft-provider app class name.")

        val clazz = archive.classloader.loadClass(providerClassName)

        return clazz.getConstructor().newInstance() as? MinecraftProvider<*>
            ?: throw IllegalStateException("Loaded provider class, but type is not a MinecraftProvider!")
    }
}

public data class MinecraftProviderRequest(
    override val descriptor: MinecraftProviderDescriptor,
) : ArtifactRequest<MinecraftProviderDescriptor>

public data class MinecraftProviderDescriptor(
    val version: String,
) : ArtifactMetadata.Descriptor {
    override val name: String by ::version
}