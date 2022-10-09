package net.yakclient.minecraft.bootstrapper.one_nineteen

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.store.DataAccess
import net.yakclient.common.util.make
import java.nio.file.Path
import net.yakclient.common.util.resolve
import java.nio.file.Files

public class MCManifestDataAccess(
    private val path: Path
) : DataAccess<String, ClientManifest> {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override fun read(key: String): ClientManifest? {
        val path = path resolve key resolve "$key-manifest.json"

        if (!Files.exists(path)) return null

        return mapper.readValue<ClientManifest>(path.toFile())
    }

    override fun write(key: String, value: ClientManifest) {
        val path = path resolve key resolve "$key-manifest.json"

        if (!Files.exists(path)) path.make()

        Files.write(path, mapper.writeValueAsBytes(value))
    }
}