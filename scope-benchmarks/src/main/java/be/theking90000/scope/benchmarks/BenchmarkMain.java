package be.theking90000.scope.benchmarks;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.results.format.ResultFormatType;

public final class BenchmarkMain {
    private BenchmarkMain() {
    }

    public static void main(String[] args) throws Exception {
        ChainedOptionsBuilder builder = new OptionsBuilder()
            .include(SyntheticScopeBenchmarks.class.getSimpleName())
            .warmupIterations(3)
            .measurementIterations(5)
            .forks(1)
            .shouldFailOnError(true);

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--result-format" -> builder.resultFormat(ResultFormatType.valueOf(requireValue(args, ++i)));
                case "--result" -> builder.result(requireValue(args, ++i));
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        Options options = builder.build();

        new Runner(options).run();
    }

    private static String requireValue(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + args[index - 1]);
        }
        return args[index];
    }
}
