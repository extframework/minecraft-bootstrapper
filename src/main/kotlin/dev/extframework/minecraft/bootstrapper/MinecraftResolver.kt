package dev.extframework.minecraft.bootstrapper

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.boot.archive.ArchiveNodeResolver

public interface MinecraftResolver<T : ArtifactMetadata<MinecraftDescriptor, ArtifactMetadata.ParentInfo<MinecraftArtifactRequest, MinecraftRepositorySettings>>> :
    ArchiveNodeResolver<MinecraftDescriptor, MinecraftArtifactRequest, MinecraftNode, MinecraftRepositorySettings, T>