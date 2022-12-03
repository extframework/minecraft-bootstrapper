package net.yakclient.minecraft.provider._default

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.yakclient.common.util.resource.SafeResource
import net.yakclient.common.util.toResource
import java.net.URI
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
public data class ClientManifest(
    val mainClass: String,
    val libraries: List<ClientLibrary>,
    val downloads: Map<ManifestDownloadType, McArtifact>,
    @JsonProperty("id")
    val version: String,
)

public enum class ManifestDownloadType {
    @JsonProperty("client")
    CLIENT,

    @JsonProperty("client_mappings")
    CLIENT_MAPPINGS,

    @JsonProperty("server")
    SERVER,

    @JsonProperty("server_mappings")
    SERVER_MAPPINGS
}

@JsonIgnoreProperties(ignoreUnknown = true)
public data class ClientLibrary(
    val name: String,
    val downloads: LibraryDownloads,
    @JsonProperty("extract")
    private val _extract: LibraryExtracts?,
    val natives: Map<String, String> = HashMap(),
    val rules: List<LibraryRule> = ArrayList()
) {
    val extract: LibraryExtracts = _extract ?: LibraryExtracts(listOf())
}

@JsonIgnoreProperties(ignoreUnknown = true)
public data class LibraryRule(
    val action: LibraryRuleAction,
    @JsonProperty("os")
    private val osProperties: Map<String, String> = HashMap()
) {
    val osName: String? = osProperties["name"]
}

public enum class LibraryRuleAction {
    @JsonProperty("allow")
    ALLOW,

    @JsonProperty("disallow")
    DISALLOW
}

@JsonIgnoreProperties(ignoreUnknown = true)
public data class LibraryExtracts(
    val exclude: List<String>
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class LibraryDownloads(
    val artifact: McArtifact,
    @JsonProperty("classifiers")
    val classifiers: Map<String, McArtifact> = HashMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class McArtifact(
    val url: URI,
    @JsonProperty("sha1")
    val checksum: String,
) {
    public fun toResource(): SafeResource = url.toResource(HexFormat.of().parseHex(checksum))
}