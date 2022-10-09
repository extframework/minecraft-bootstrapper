open module yakclient.minecraft.bootstrapper.one_nineteen {
    requires kotlin.stdlib;
    requires yakclient.archive.mapper;
    requires yakclient.boot;
    requires yakclient.minecraft.bootstrapper;
    requires yakclient.common.util;
    requires kotlinx.coroutines.core.jvm;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.kotlin;
    requires durganmcbroom.artifact.resolver;
    requires durganmcbroom.artifact.resolver.simple.maven;
    requires yakclient.archives;
    requires arrow.core.jvm;

    exports net.yakclient.minecraft.bootstrapper.one_nineteen;
}