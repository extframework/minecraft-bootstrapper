package net.yakclient.minecraft.bootstrapper.one_nineteen;


import net.yakclient.archives.ArchiveHandle;
import net.yakclient.minecraft.bootstrapper.MinecraftAppInstance;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class Minecraft_1_19_App implements MinecraftAppInstance {
    private final ArchiveHandle handle;
    private final ClientManifest manifest;

    public Minecraft_1_19_App(ArchiveHandle handle, ClientManifest manifest) {
        this.handle = handle;
        this.manifest = manifest;
    }

    @Override
    public void start(String[] args) {
        try {
            final var i = IntStream.range(0, args.length)
                    .filter(it -> Objects.equals(args[it], "--minecraft-args"))
                    .findFirst();

            final Supplier<String[]> translateArgs = () -> {
                final int asInt = i.getAsInt();

                if (asInt + 1 > args.length)
                    throw new IllegalArgumentException("Bad args, flag --minecraft-args provided but no args were given. Complete argument list was '" + Arrays.toString(args) + "'");

                return translateCommandline(args[asInt + 1]);
            };

            final var mcArgs = i.isPresent() ? translateArgs.get() : new String[]{};

            final var cls = handle.getClassloader().loadClass(manifest.getMainClass());

            cls.getMethod("main", String[].class).invoke(null, new Object[]{mcArgs});
        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * [code borrowed from ant.jar]
     * Crack a command line.
     *
     * @param toProcess the command line to process.
     * @return the command line broken into strings.
     * An empty or null toProcess parameter results in a zero sized array.
     */
    private static String[] translateCommandline(String toProcess) {
        if (toProcess == null || toProcess.length() == 0) {
            //no command? no string
            return new String[0];
        }
        // parse with a simple finite state machine

        final int normal = 0;
        final int inQuote = 1;
        final int inDoubleQuote = 2;
        int state = normal;
        final StringTokenizer tok = new StringTokenizer(toProcess, "\"\' ", true);
        final ArrayList<String> result = new ArrayList<String>();
        final StringBuilder current = new StringBuilder();
        boolean lastTokenHasBeenQuoted = false;

        while (tok.hasMoreTokens()) {
            String nextTok = tok.nextToken();
            switch (state) {
                case inQuote:
                    if ("\'".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                case inDoubleQuote:
                    if ("\"".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                default:
                    if ("\'".equals(nextTok)) {
                        state = inQuote;
                    } else if ("\"".equals(nextTok)) {
                        state = inDoubleQuote;
                    } else if (" ".equals(nextTok)) {
                        if (lastTokenHasBeenQuoted || current.length() != 0) {
                            result.add(current.toString());
                            current.setLength(0);
                        }
                    } else {
                        current.append(nextTok);
                    }
                    lastTokenHasBeenQuoted = false;
                    break;
            }
        }
        if (lastTokenHasBeenQuoted || current.length() != 0) {
            result.add(current.toString());
        }
        if (state == inQuote || state == inDoubleQuote) {
            throw new RuntimeException("unbalanced quotes in " + toProcess);
        }
        return result.toArray(new String[result.size()]);
    }
}
