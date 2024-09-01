package dev.extframework.minecraft.bootstrapper.test

import BootLoggerFactory
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.launch
import dev.extframework.boot.archive.*
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.audit.chain
import dev.extframework.boot.constraint.ConstraintArchiveAuditor
import dev.extframework.boot.dependency.BasicDependencyNode
import dev.extframework.boot.maven.MavenConstraintNegotiator
import dev.extframework.boot.maven.MavenDependencyResolver
import dev.extframework.boot.maven.MavenLikeResolver
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.monad.removeIf
import dev.extframework.common.util.readInputStream
import dev.extframework.minecraft.bootstrapper.MinecraftProviderRemoteLookup
import dev.extframework.minecraft.bootstrapper.loadMinecraft
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class TestMinecraftProviderLoading {
    fun loadMinecraft(version: String) {
        val dependencies = this::class.java.getResource("/dependencies.txt")?.openStream()?.use {
            val fileStr = String(it.readInputStream())
            fileStr.split("\n").toSet()
        }?.filterNot { it.isBlank() }?.mapTo(HashSet()) { SimpleMavenDescriptor.parseDescription(it)!! }
            ?: throw IllegalStateException("Cant load dependencies?")

        val cache = Path.of("test-run").toAbsolutePath()

        val archiveGraph = DefaultArchiveGraph(
            cache,
            dependencies.associateByTo(HashMap()) {
                BasicDependencyNode(it, null,
                    object : ArchiveAccessTree {
                        override val descriptor: ArtifactMetadata.Descriptor = it
                        override val targets: List<ArchiveTarget> = listOf()
                    }
                )
            } as MutableMap<ArtifactMetadata.Descriptor, ArchiveNode<*>>
        )

        val negotiator = MavenConstraintNegotiator()

        val alreadyLoaded = dependencies.map {
            negotiator.classify(it)
        }

        val maven = object : MavenDependencyResolver(
            parentClassLoader = TestMinecraftProviderLoading::class.java.classLoader,
        ) {
            override val auditors: Auditors
                get() = super.auditors.replace(
                    ConstraintArchiveAuditor(
                        listOf(MavenConstraintNegotiator()),
                    ).chain(object : ArchiveTreeAuditor {
                        override fun audit(event: ArchiveTreeAuditContext): Job<ArchiveTreeAuditContext> = job {
                            event.copy(tree = event.tree.removeIf {
                                alreadyLoaded.contains(negotiator.classify(it.value.descriptor as SimpleMavenDescriptor))
                            }!!)
                        }
                    })
                )
        }

        launch(BootLoggerFactory()) {
            val node = loadMinecraft(
                version,
                SimpleMavenRepositorySettings.local(),
                cache,
                archiveGraph,
                maven as MavenLikeResolver<ClassLoadedArchiveNode<SimpleMavenDescriptor>, *>,
            )().merge()

            println("Back here")
        }
    }

    @Test
    fun `Test 1_19_2 load`() {
        loadMinecraft("1.19.2")
    }

    @Test
    fun `Test 1_20_1 load`() {
        loadMinecraft("1.20.1")
    }

    @Test
    fun `Test 1_21 load`() {
        loadMinecraft("1.21")
    }

    @Test
    fun `Find correct mc version`() {
        val find = MinecraftProviderRemoteLookup(Files.createTempDirectory("test")).find("1.21")
        println(find)
        check(find.name == "dev.extframework.minecraft:minecraft-provider-def:2.0-SNAPSHOT")
    }
}