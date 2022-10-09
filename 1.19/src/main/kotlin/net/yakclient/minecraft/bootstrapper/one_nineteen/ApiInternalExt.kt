package net.yakclient.minecraft.bootstrapper.one_nineteen

//public class ApiInternalExt {
//    override fun onLoad() {
//        val minecraft: ArchiveHandle = loadMinecraft()
//
//        // Finds the minecraft extension(yakclient) handle
//        val ext: ArchiveReference = Archives.find(YakClient.settings.mcExtLocation, Archives.Finders.JPM_FINDER)
//
//        // Loads settings
//        val settings = ExtensionLoader.loadSettings(ext)
//
//        // Loads the extension
//        ContainerLoader.load(
//            ExtensionInfo(
//                ext,
//                this,
//                settings,
//                ExtensionLoader.loadDependencies(settings).let {
//                    it.toMutableSet().also { m -> m.add(minecraft) }
//                }
//            ),
//            ExtensionLoader,
//            VolumeStore["minecraft-data"],
//            PrivilegeManager.allPrivileges(),
//            loader
//        ).process.start()
//    }

//}