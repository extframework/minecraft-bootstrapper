package net.yakclient.minecraft.bootstrapper

import net.yakclient.common.util.readInputStream

//public object MinecraftMixinAccess {
//    public fun read(name: String): ByteArray? {
//        MinecraftBootstrapper.checkInitialized()
//
//        return MinecraftBootstrapper.instance.minecraftRef.archive.reader[name]?.resource?.open()?.readInputStream()
//    }
//
//   public fun write(name: String, bytes: ByteArray) {
//        listeners.forEach { it(name, bytes) }
//    }
//
//    public companion object {
//        private val listeners: MutableList<(name: String, bytes: ByteArray) -> Unit> = ArrayList()
//
//        internal fun registerListener(listener: (name: String, bytes: ByteArray) -> Unit) {
//            listeners.add(listener)
//        }
//    }
//}