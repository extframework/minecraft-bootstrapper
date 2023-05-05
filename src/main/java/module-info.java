module yakclient.minecraft.bootstrapper {
    requires kotlin.stdlib;
    requires yakclient.archives;
    requires kotlinx.cli.jvm;
    requires durganmcbroom.artifact.resolver.simple.maven;
    requires durganmcbroom.artifact.resolver;
    requires arrow.core.jvm;
    requires yakclient.common.util;
    requires net.bytebuddy.agent;
    requires java.instrument;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.kotlin;
    requires yakclient.archives.mixin;
    requires java.logging;
    requires yakclient.archive.mapper;

    exports net.yakclient.minecraft.bootstrapper;
}