package net.yakclient.minecraft.bootstrapper

import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import net.yakclient.boot.BootInstance
import net.yakclient.boot.component.ComponentFactory
import net.yakclient.boot.component.context.ContextNodeTypes
import net.yakclient.boot.component.context.ContextNodeTypes.String
import net.yakclient.boot.component.context.ContextNodeValue

public class MinecraftBootstrapperFactory(boot: BootInstance) : ComponentFactory<MinecraftBootstrapperConfiguration, MinecraftBootstrapper>(boot) {
    override fun parseConfiguration(value: ContextNodeValue): MinecraftBootstrapperConfiguration {
        fun String?.coerceNotNull(lazy: () -> String) = checkNotNull(this, lazy)

        val tree = value.coerceTree()
        return MinecraftBootstrapperConfiguration(
                tree["mcVersion"]?.coerceType(String).coerceNotNull { "Invalid Minecraft Bootstrapper configuration, property 'mcVersion' is required." },
                run {
                    val repo = tree["repository"]?.coerceType(String).coerceNotNull { "Invalid Minecraft Bootstrapper configuration, property 'repository' is required." }
                    val type = tree["repositoryType"]?.coerceType(String).coerceNotNull { "Invalid Minecraft Bootstrapper configuration, property 'repositoryType' is required." }

                    when (type.lowercase()) {
                        "default" -> SimpleMavenRepositorySettings.default(repo, preferredHash = HashType.SHA1)
                        "local" -> SimpleMavenRepositorySettings.local(repo, preferredHash = HashType.SHA1)
                        else -> throw IllegalArgumentException("Unknown repository type: '$type'. Options are 'default' or 'local' (case-insensitive).")
                    }
                },
                tree["cache"]?.coerceType(String).coerceNotNull { "Invalid Minecraft Bootstrapper configuration, property 'cache' is required." },
                tree["versionMappings"]?.coerceType(String).coerceNotNull { "Invalid Minecraft Bootstrapper configuration, property 'versionMappings' is required." },
                tree["mcArgs"]?.coerceType(ContextNodeTypes.Array)?.list()?.mapNotNull { it.tryType(String) }
                        ?: ArrayList()
        )
    }

    override fun new(configuration: MinecraftBootstrapperConfiguration): MinecraftBootstrapper {
        return MinecraftBootstrapper(boot, configuration)
    }
}