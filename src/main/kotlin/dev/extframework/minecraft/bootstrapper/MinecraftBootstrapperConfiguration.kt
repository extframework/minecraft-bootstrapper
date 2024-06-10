package dev.extframework.minecraft.bootstrapper

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import dev.extframework.boot.component.ComponentConfiguration

public data class MinecraftBootstrapperConfiguration(
        val mcVersion: String,
        val repository: SimpleMavenRepositorySettings,
        val cache: String,
        val versionMappings: String,
//        val mcArgs: List<String>,
//        val applyBasicArgs: Boolean = true
) : ComponentConfiguration