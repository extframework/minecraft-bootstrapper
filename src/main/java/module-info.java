module yakclient.minecraft.bootstrapper {
    requires kotlin.stdlib;
    requires yakclient.boot;
    requires yakclient.archives;
    requires kotlinx.cli.jvm;
    requires durganmcbroom.artifact.resolver.simple.maven;
    requires durganmcbroom.artifact.resolver;
    requires arrow.core.jvm;
    requires yakclient.common.util;
    requires yakclient.mixin.plugin;
    requires net.bytebuddy.agent;
    requires java.instrument;

    exports net.yakclient.minecraft.bootstrapper;
}