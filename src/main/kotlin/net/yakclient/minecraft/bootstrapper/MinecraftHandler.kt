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

    private val mixins: MutableMap<String, PriorityQueue<Pair<Int, MinecraftClassTransformer>>> = HashMap()

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
            MinecraftClassLoader(this@MinecraftHandler, parent, extraClassProvider)
        )().merge()
    }

    private class MinecraftClassLoader(
        private val handler: MinecraftHandler<*>,
        parent: ClassLoader,
        private val extraClassProvider: ExtraClassProvider
    ) : MutableClassLoader(
        name = "Minecraft classloader",
        MutableSourceProvider(ArrayList()),
        MutableClassProvider(ArrayList()),
        MutableResourceProvider(ArrayList()),
        sd = SourceDefiner { name: String, bb: ByteBuffer, cl: ClassLoader, definer ->
            val newBb = handler.mixins[name]?.let { transformers ->
                val transformedNode = (transformers).fold(ClassNode().also {
                    ClassReader(bb.toBytes()).accept(
                        it,
                        0
                    )
                }) { acc, it -> it.second.transform(acc) }

                val writer = AwareClassWriter(
                    (transformers.flatMapTo(
                        HashSet(),
                    ) { it.second.trees } + handler.minecraftReference.libraries + handler.minecraftReference.archive).toList(),
                    Archives.WRITER_FLAGS
                )
                transformedNode.accept(writer)
                ByteBuffer.wrap(writer.toByteArray())
            } ?: bb

            definer.invoke(name, newBb, ProtectionDomain(null, null))
        },
        parent = parent,
    ) {
        override fun tryDefine(name: String): Class<*>? {
            return extraClassProvider.getByteArray(name)?.let {
                sourceDefiner.define(name, ByteBuffer.wrap(it), this, ::defineClass)
            } ?: super.tryDefine(name)
        }

        companion object {
            init {
                registerAsParallelCapable()
            }
        }
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

//    public fun writeAll() {
//        val mixins: Set<Map.Entry<String, MutableList<MixinMetadata<*>>>> =
//            mixins.filter { updatedMixins.contains(it.key) }.entries
//
//        val toWrite = mixins.map { (to, all: MutableList<MixinMetadata<*>>) ->
//            all.map { (data, injection) ->
//                (injection as MixinInjection<MixinInjection.InjectionData>).apply(data)
//            }.reduce { acc: TransformerConfig, t: TransformerConfig.Mutable ->
//                acc + t
//            } to to
//        }
//
//        toWrite.forEach { (config, to) ->
//            val entry = minecraftReference
//                .archive
//                .reader["${to.replace('.', '/')}.class"]
//            val bytes = entry
//                ?.resource
//                ?.open()
//                ?.readInputStream()
//                ?: throw IllegalArgumentException("Failed to inject into class '$to' because it does not exist!")
//
//            if (!isLoaded) {
//                minecraftReference.archive.writer.put(
//                    entry.transform(
//                        config, minecraftReference.libraries
//                    )
//                )
//            } else {
//                instrumentation.redefineClasses(
//                    ClassDefinition(
//                        handle.archive.classloader.loadClass(to),
//                        Archives.resolve(
//                            ClassReader(bytes),
//                            config,
//                            AwareClassWriter(
//                                minecraftReference.libraries + minecraftReference.archive,
//                                Archives.WRITER_FLAGS
//                            )
//                        )
//                    )
//                )
//            }
//        }
//    }
}