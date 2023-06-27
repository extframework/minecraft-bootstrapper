package net.yakclient.minecraft.provider.def

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.ClassIdentifier
import net.yakclient.archive.mapper.MappingType
import net.yakclient.archive.mapper.MethodIdentifier
import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.store.DelegatingDataStore
import net.yakclient.launchermeta.handler.LaunchMetadata
import net.yakclient.minecraft.bootstrapper.MinecraftHandle
import net.yakclient.minecraft.bootstrapper.MinecraftProvider
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import java.security.Permission

public class DefaultMinecraftProvider : MinecraftProvider<DefaultMinecraftReference> {
    override fun getReference(version: String, cachePath: Path): DefaultMinecraftReference {
        return loadMinecraftRef(version, cachePath, DelegatingDataStore(LaunchMetadataDataAccess(cachePath)))
    }


    override fun get(ref: DefaultMinecraftReference): MinecraftHandle {
        val (handle, manifest) = loadMinecraft(ref)

        return DefaultMinecraftHandle(handle, manifest, ref.mappings)
    }

    private class DefaultMinecraftHandle(
            override val archive: ArchiveHandle,
            private val manifest: LaunchMetadata,
            private val mappings: ArchiveMapping
    ) : MinecraftHandle {
        private class ExitTrappedException : SecurityException()

        override fun start(args: Array<String>) {
            // https://stackoverflow.com/questions/5401281/preventing-system-exit-from-api
            val securityManager: SecurityManager = object : SecurityManager() {
                override fun checkExit(status: Int) {
                    throw ExitTrappedException()
                }

                override fun checkPermission(perm: Permission?) {
                    // Dont wanna check permissions
                }
            }
            System.setSecurityManager(securityManager)

            val out = System.out
            try {
                archive.classloader.loadClass(manifest.mainClass).getMethod("main", Array<String>::class.java).invoke(null, args)
            } catch (e: Throwable) {
                System.setOut(out)
                if (!(e is InvocationTargetException && ExitTrappedException::class.isInstance(e.targetException))) throw e
            }
        }

        override fun shutdown() {
            val mapping = mappings.classes[ClassIdentifier(
                    "net/minecraft/client/Minecraft", MappingType.REAL
            )]
            checkNotNull(mapping) { "Could not find mapping for 'net.minecraft.client.Minecraft'" }

            val minecraftClass = archive.classloader.loadClass(mapping.fakeIdentifier.name.replace('/', '.'))
            val minecraft = minecraftClass.getMethod(
                    checkNotNull(mapping.methods[MethodIdentifier("getInstance", listOf(), MappingType.REAL)]?.fakeIdentifier?.name)
                    { "Couldnt map method 'net.minecraft.client.Minecraft getInstance()', is this minecraft loader out of date?" }
            ).invoke(null)
            checkNotNull(minecraft) { "Couldnt get minecraft instance, has it fully started?" }


            minecraftClass.getMethod(
                    checkNotNull(mapping.methods[MethodIdentifier("stop", listOf(), MappingType.REAL)]?.fakeIdentifier?.name)
                    { "Couldnt map method 'net.minecraft.client.Minecraft stop()', is this minecraft loader out of date?" }
            ).invoke(minecraft)
        }
    }
}