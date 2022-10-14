package net.yakclient.minecraft.bootstrapper

import net.yakclient.common.util.readInputStream
import net.yakclient.plugins.mixin.MixinAccess

public class MinecraftMixinAccess : MixinAccess {
    override fun read(name: String): ByteArray? {
        MinecraftBootstrapper.checkInitialized()

        return MinecraftBootstrapper.instance.minecraftRef.archive.reader[name]?.resource?.open()?.readInputStream()
    }

    override fun write(name: String, bytes: ByteArray) {
        listeners.forEach { it(name, bytes) }
    }

    public companion object {
        private val listeners: MutableList<(name: String, bytes: ByteArray) -> Unit> = ArrayList()

        internal fun registerListener(listener: (name: String, bytes: ByteArray) -> Unit) {
            listeners.add(listener)
        }
    }
}