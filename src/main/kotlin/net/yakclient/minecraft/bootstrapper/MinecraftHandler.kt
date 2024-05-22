package net.yakclient.minecraft.bootstrapper

import com.durganmcbroom.jobs.*
import net.yakclient.archives.ArchiveTree
import net.yakclient.archives.Archives
import net.yakclient.archives.transform.AwareClassWriter
import org.objectweb.asm.ClassReader
import java.nio.file.Path
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.loader.*
import net.yakclient.common.util.toBytes
import org.objectweb.asm.tree.ClassNode
import java.nio.ByteBuffer
import java.security.ProtectionDomain
import java.util.PriorityQueue

public interface MinecraftClassTransformer {
    public val trees: List<ArchiveTree>

    public fun transform(node: ClassNode): ClassNode
}

public interface ExtraClassProvider {
    public fun getByteArray(name: String): ByteArray?
}


public class MinecraftHandler<T : MinecraftReference>(
    public val version: String,
    private val cache: Path,
    public val provider: MinecraftProvider<T>,
    private val archiveGraph: ArchiveGraph
) {
    public lateinit var minecraftReference: T
        private set
    public lateinit var handle: MinecraftHandle
        private set

    public var isLoaded: Boolean = false
        private set
    public var hasStarted: Boolean = false
        private set

    internal val mixins: MutableMap<String, PriorityQueue<Pair<Int, MinecraftClassTransformer>>> = HashMap()

    internal fun loadReference(): Job<Unit> =
        job(JobName("Setup minecraft reference")) {
            val r = provider.getReference(version, cache).invoke().merge()

            minecraftReference = r
        }

    public fun loadMinecraft(parent: ClassLoader, extraClassProvider: ExtraClassProvider): Job<Unit> = job {
        check(!isLoaded) { "Minecraft is already loaded" }
        handle = provider.get(
            minecraftReference,
            archiveGraph,
            MinecraftClassLoader(mixins::get, minecraftReference.libraries + minecraftReference.archive, parent, extraClassProvider)
        )().merge()
    }

    public fun startMinecraft(args: Array<String>) {
        check(!hasStarted) { "Minecraft has already started" }
        hasStarted = true


        handle.start(args)
    }

    internal fun shutdownMinecraft() {
        check(hasStarted) { "Minecraft is not running!" }
        handle.shutdown()
        hasStarted = false
    }

    public fun registerMixin(to: String, transformer: MinecraftClassTransformer) {
        registerMixin(to, 0, transformer)
    }

    public fun registerMixin(to: String, priority: Int, transformer: MinecraftClassTransformer) {
        (mixins[to] ?: PriorityQueue<Pair<Int, MinecraftClassTransformer>> { first, second ->
            second.first - first.first
        }.also { mixins[to] = it }).add(
            priority to transformer
        )
    }
}