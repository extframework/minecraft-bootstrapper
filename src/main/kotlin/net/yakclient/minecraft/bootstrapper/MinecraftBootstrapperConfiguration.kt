package net.yakclient.minecraft.bootstrapper

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import net.yakclient.boot.component.ComponentConfiguration

public data class MinecraftBootstrapperConfiguration(
        val mcVersion: String,
        val repository: SimpleMavenRepositorySettings,
        val cache: String,
        val versionMappings: String,
        val mcArgs: List<String>
) : ComponentConfiguration