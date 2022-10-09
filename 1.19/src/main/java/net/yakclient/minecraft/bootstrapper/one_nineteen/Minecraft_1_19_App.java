package net.yakclient.minecraft.bootstrapper.one_nineteen;


import net.yakclient.archives.ArchiveHandle;
import net.yakclient.minecraft.bootstrapper.MinecraftAppInstance;

import java.lang.reflect.InvocationTargetException;

//private class App(
//    val handle: ArchiveHandle,
//    val manifest: ClientManifest,
//) : MinecraftAppInstance {
//    override fun start(args: Array<String>) {
//        val cls = handle.classloader.loadClass(manifest.mainClass)
//
//        cls.getMethod("main", Array<String>::class.java).invoke(null, arrayOf<Any>(args))
//    }
//}
public class Minecraft_1_19_App implements MinecraftAppInstance {
    private final ArchiveHandle handle;
    private final ClientManifest manifest;

    public Minecraft_1_19_App(ArchiveHandle handle, ClientManifest manifest) {
        this.handle = handle;
        this.manifest = manifest;
    }

    @Override
    public void start(String[] args) {
        try {
            final var cls = handle.getClassloader().loadClass(manifest.getMainClass());

            cls.getMethod("main", String[].class).invoke(null, new Object[]{args});
        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
