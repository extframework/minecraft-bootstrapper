package net.yakclient.minecraft.bootstrapper.one_nineteen.test

import net.yakclient.boot.store.DelegatingDataStore
import net.yakclient.common.util.resolve
import net.yakclient.minecraft.bootstrapper.one_nineteen.MCManifestDataAccess
import net.yakclient.minecraft.bootstrapper.one_nineteen.loadMinecraft
import java.nio.file.Path
import java.util.Arrays
import kotlin.test.Test

class TestLoadingMinecraft {
    @Test
    fun `Test load`() {
        val path = Path.of(System.getProperty("user.dir")) resolve "mc"

        val (mc, manifest) = loadMinecraft("1.19", path, DelegatingDataStore(MCManifestDataAccess(path)))

        println(mc)

        val cls = mc.classloader.loadClass(manifest.mainClass)

        cls.getMethod("main", Array<String>::class.java)
            .invoke(null, arrayOf("--accessToken", "", "--version", "1.18.2"))
    }

    @Test
    fun `Test arg parse`() {
        println(
            Arrays.toString(ArgParser.translateCommandline(
                "--hello \"asdf\" --test-other-thing \"adsfasdf\""
            ))
        )
    }

    @Test
    fun `Test mc arg parse`() {

    }
}