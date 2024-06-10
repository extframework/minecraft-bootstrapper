package dev.extframework.minecraft.provider.def

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.store.DataAccess
import dev.extframework.common.util.make
import java.nio.file.Path
import dev.extframework.common.util.resolve
import dev.extframework.launchermeta.handler.LaunchMetadata
import java.nio.file.Files

public class LaunchMetadataDataAccess(
    private val path: Path
) : DataAccess<String, LaunchMetadata> {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override fun read(key: String): LaunchMetadata? {
        val path = path resolve key resolve "$key-manifest.json"

        if (!Files.exists(path)) return null

        return mapper.readValue<LaunchMetadata>(path.toFile())
    }

    override fun write(key: String, value: LaunchMetadata) {
        val path = path resolve key resolve "$key-manifest.json"

        if (!Files.exists(path)) path.make()

        Files.write(path, mapper.writeValueAsBytes(value))
    }
}