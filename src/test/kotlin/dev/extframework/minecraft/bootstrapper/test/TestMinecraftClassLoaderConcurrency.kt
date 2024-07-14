package dev.extframework.minecraft.bootstrapper.test

import dev.extframework.boot.loader.*
import dev.extframework.minecraft.bootstrapper.MinecraftClassLoader
import org.junit.jupiter.api.RepeatedTest
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors

fun newClassBytes(name: String): ByteBuffer {
    val node = ClassNode()
    node.name = name.replace('.', '/')
    node.superName = "java/lang/Object"
    node.access = Opcodes.ACC_PUBLIC
    node.version = 61

    val writer = ClassWriter(0)
    node.accept(writer)
    return ByteBuffer.wrap(writer.toByteArray())
}

class TestMinecraftClassLoaderConcurrency {
    fun concurrentlyLoadClasses(
        loaderProvider: (
            SourceProvider
        ) -> ClassLoader
    ) {
        val sources = object : SourceProvider {
            override val packages: Set<String> = setOf("com.example")

            override fun findSource(name: String): ByteBuffer? {
                println("Getting class source : '$name' in thread : '${Thread.currentThread().name}'")

                return if (!name.startsWith("java")) newClassBytes(name) else null
            }
        }

        // URLs pointing to classpath
        val classLoader = loaderProvider(sources)

        // Create a pool of threads
        val executorService = Executors.newFixedThreadPool(10)

        // Define tasks to load classes
        val classNames = (1..100).map { "com.example.Class$it" }

        // Define tasks to load classes
        val tasks = classNames.map { className ->
            Callable { classLoader.loadClass(className) }
        }

        // Submit tasks
        val futures = tasks.map { executorService.submit(it) }

        // Wait for all tasks to complete and assert no exceptions occurred
        try {
            for (future in futures) {
                future.get()
            }
            assert(true) // All classes loaded without exception
        } catch (e: Exception) {
            throw e
        } finally {
            executorService.shutdown()
        }
    }

    @RepeatedTest(10)
    fun `Test minecraft loader`() {
        concurrentlyLoadClasses { p ->
            MinecraftClassLoader(
                ClassLoader.getPlatformClassLoader(),
                false
            ).also {
                it.addSources(p)
            }
        }
    }
}