module yakclient.minecraft.bootstrapper {
    requires kotlin.stdlib;
    requires yakclient.boot;
    requires yakclient.archives;
    requires kotlinx.cli.jvm;
    requires durganmcbroom.artifact.resolver.simple.maven;
    requires durganmcbroom.artifact.resolver;
    requires arrow.core.jvm;
    requires yakclient.common.util;

    exports net.yakclient.minecraft.bootstrapper;
}