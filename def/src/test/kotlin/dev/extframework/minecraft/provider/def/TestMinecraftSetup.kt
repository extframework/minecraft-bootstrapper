package dev.extframework.minecraft.provider.def

import BootLoggerFactory
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.launch
import com.durganmcbroom.jobs.logging.info
import com.durganmcbroom.resources.ResourceAlgorithm
import com.durganmcbroom.resources.VerifiedResource
import com.durganmcbroom.resources.toResource
import dev.extframework.boot.store.DelegatingDataStore
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.launchermeta.handler.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.net.URL
import java.nio.file.Files
import java.util.HexFormat
import kotlin.math.floor
import kotlin.test.Test

class TestMinecraftSetup {
    @Test
    fun `Test load mc ref`() {
        val cachePath = Files.createTempDirectory("mc-ref")
        launch(BootLoggerFactory()) {
            loadMinecraftRef(
                "1.21",
                cachePath,
                DelegatingDataStore(LaunchMetadataDataAccess(cachePath))
            )().merge()
        }
    }

    @Test
    fun `Download problematic one`() {
        val asset = Asset(
            "aa1d3aace1c481ac32d5827fba287294b6bc99fb",
            17607318,
        )

        val unverifiedResource =
            URL("$MINECRAFT_RESOURCES_URL/${asset.checksum.take(2)}/${asset.checksum}")
                .toResource()

        VerifiedResource(
            unverifiedResource,
            ResourceAlgorithm.SHA1,
            HexFormat.of().parseHex(asset.checksum),
        ) copyTo Files.createTempFile("mc-asset", ".ogg")
    }
}