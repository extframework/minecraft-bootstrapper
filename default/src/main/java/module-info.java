open module yakclient.minecraft.provider._default {
    requires kotlin.stdlib;
    requires com.fasterxml.jackson.databind;
    requires yakclient.common.util;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.kotlin;
    requires yakclient.archives;
    requires yakclient.minecraft.bootstrapper;
    requires yakclient.boot;
    requires durganmcbroom.artifact.resolver.simple.maven;
    requires kotlinx.coroutines.core.jvm;

    exports net.yakclient.minecraft.provider._default;
}