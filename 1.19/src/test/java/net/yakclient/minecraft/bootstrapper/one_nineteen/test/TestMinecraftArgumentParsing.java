package net.yakclient.minecraft.bootstrapper.one_nineteen.test;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static net.yakclient.minecraft.bootstrapper.one_nineteen.test.ArgParser.translateCommandline;

public class TestMinecraftArgumentParsing {
    @Test
    public void testMcArgParse() {
        final var args = new String[]{"--hello", "asdf", "--last-thing", "asdfadsf", "--minecraft-args", "--version \"Idk ??\" --accessToken \"Valid\""};

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

        System.out.println(Arrays.toString(mcArgs));
    }
}
