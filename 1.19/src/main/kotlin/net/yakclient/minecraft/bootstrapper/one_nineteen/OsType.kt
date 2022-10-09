package net.yakclient.minecraft.bootstrapper.one_nineteen

import java.util.*


public enum class OsType {
    OS_X,
    WINDOWS,
    UNIX;

    public companion object {
        public val type: OsType

        init {
            val os = System.getProperty("os.name").lowercase(Locale.ENGLISH)
            type =
                when {
                    os.contains("win") -> WINDOWS
                    os.contains("mac") -> OS_X
                    os.contains("solaris") || os.contains("sunos") -> UNIX
                    os.contains("linux") -> UNIX
                    os.contains("unix") -> UNIX
                    else -> throw IllegalStateException("Unsupported OS: $os")
                }

        }
    }
}