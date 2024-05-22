package net.yakclient.minecraft.bootstrapper

import net.yakclient.archives.ArchiveTree
import net.yakclient.archives.Archives
import net.yakclient.archives.transform.AwareClassWriter
import net.yakclient.boot.loader.MutableClassLoader
import net.yakclient.boot.loader.MutableClassProvider
import net.yakclient.boot.loader.MutableResourceProvider
import net.yakclient.boot.loader.MutableSourceProvider
import net.yakclient.boot.loader.SourceDefiner
import net.yakclient.common.util.toBytes
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.nio.ByteBuffer
import java.security.ProtectionDomain
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

internal class MinecraftClassLoader(
    mixinFor: (String) -> PriorityQueue<Pair<Int, MinecraftClassTransformer>>?,
    extraLibraries: List<ArchiveTree>,
    parent: ClassLoader,

    private val extraClassProvider: ExtraClassProvider,
) : MutableClassLoader(
    name = "Minecraft classloader",
    MutableSourceProvider(ArrayList()),
    MutableClassProvider(ArrayList()),
    MutableResourceProvider(ArrayList()),
    sd = SourceDefiner { name: String, bb: ByteBuffer, cl: ClassLoader, definer ->
        val newBb = mixinFor(name)?.let { transformers ->
            val transformedNode = (transformers).fold(ClassNode().also {
                ClassReader(bb.toBytes()).accept(
                    it,
                    0
                )
            }) { acc, it -> it.second.transform(acc) }

            val writer = AwareClassWriter(
                (transformers.flatMapTo(
                    HashSet(),
                ) { it.second.trees } + extraLibraries).toList(),
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