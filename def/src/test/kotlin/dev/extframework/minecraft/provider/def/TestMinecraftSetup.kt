package dev.extframework.minecraft.provider.def

import BootLoggerFactory
import com.durganmcbroom.jobs.launch
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.minecraft.bootstrapper.MinecraftArtifactRequest
import dev.extframework.minecraft.bootstrapper.MinecraftDescriptor
import dev.extframework.minecraft.bootstrapper.MinecraftRepositorySettings
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Path
import kotlin.test.Test

class TestMinecraftSetup {
    @Test
    fun `Test load mc ref`() {
        val cachePath = Path.of("test-run").toAbsolutePath()
        println(cachePath)

        val provider = DefaultMinecraftProvider()
        val archiveGraph = ArchiveGraph.from(cachePath)
        launch(BootLoggerFactory()) {
            val resolver = provider.get(
                cachePath = cachePath,
                archiveGraph,
            )

            val descriptor = MinecraftDescriptor("1.21")
            archiveGraph.cache(
                MinecraftArtifactRequest(
                    descriptor
                ),
                MinecraftRepositorySettings,
                resolver
            )().merge()

            val node = archiveGraph.get(
                descriptor,
                resolver
            )().merge()

            assertTrue(
                node.resources.findResources("net/minecraft/client/main/Main.class").toList().isNotEmpty()
            )
        }
    }
}