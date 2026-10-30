import com.netflix.nebula.archrules.gradle.Rule;
import com.netflix.nebula.archrules.gradle.RuleResult;
import com.netflix.nebula.archrules.gradle.RuleResultStatus;
import com.netflix.nebula.archrules.gradle.ViolationsUtil;
import com.netflix.nebula.archrules.gradle.report.MarkdownReportPrinter;
import com.tngtech.archunit.lang.Priority;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Synthetic public fixture: measures result I/O and rendering, excluding bytecode analysis. */
public class ResultIoBenchmark {
    private static final int WARMUPS = 3;
    private static final int PAIRS = 10;

    public static void main(String[] arguments) throws Exception {
        var directory = Path.of(arguments[0]);
        Files.createDirectories(directory);
        System.out.println("java=" + System.getProperty("java.runtime.version"));
        System.out.println("os=" + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("warmups=" + WARMUPS + ", measured_pairs=" + PAIRS);
        System.out.println("records,phase,baseline_median_ms,buffered_median_ms,baseline_min_ms,baseline_max_ms,buffered_min_ms,buffered_max_ms");
        for (int count : new int[] {100, 10_000}) {
            var results = fixture(count);
            var original = new ArrayList<Measurement>();
            var buffered = new ArrayList<Measurement>();
            for (int iteration = -WARMUPS; iteration < PAIRS; iteration++) {
                Measurement before;
                Measurement after;
                if (iteration % 2 == 0) {
                    before = run(results, directory, false);
                    after = run(results, directory, true);
                } else {
                    after = run(results, directory, true);
                    before = run(results, directory, false);
                }
                requireSameFile(directory.resolve("baseline.data"), directory.resolve("buffered.data"));
                requireSameFile(directory.resolve("baseline.md"), directory.resolve("buffered.md"));
                if (iteration >= 0) {
                    original.add(before);
                    buffered.add(after);
                }
            }
            print(count, "write_results", original.stream().mapToLong(Measurement::write).toArray(), buffered.stream().mapToLong(Measurement::write).toArray());
            print(count, "read_results", original.stream().mapToLong(Measurement::read).toArray(), buffered.stream().mapToLong(Measurement::read).toArray());
            print(count, "render_markdown", original.stream().mapToLong(Measurement::markdown).toArray(), buffered.stream().mapToLong(Measurement::markdown).toArray());
            print(count, "result_io_and_markdown", original.stream().mapToLong(Measurement::total).toArray(), buffered.stream().mapToLong(Measurement::total).toArray());
            System.out.printf("validated records=%d, data_bytes=%d, markdown_bytes=%d%n", count,
                    Files.size(directory.resolve("buffered.data")), Files.size(directory.resolve("buffered.md")));
        }
    }

    private static List<RuleResult> fixture(int count) {
        var rules = new ArrayList<Rule>();
        for (int i = 0; i < 25; i++) {
            rules.add(new Rule("example.Rules" + i % 5, "rule" + i, "Public benchmark rule " + i, Priority.values()[i % 3]));
        }
        var results = new ArrayList<RuleResult>(count);
        for (int i = 0; i < count; i++) {
            var status = i % 10 == 0 ? RuleResultStatus.PASS : RuleResultStatus.FAIL;
            var message = status == RuleResultStatus.PASS ? "" :
                    "Method <example.Source" + i + ".run()> calls method <example.Target.accept()> in (Source" + i + ".java:" + (i + 1) + ")";
            results.add(new RuleResult(rules.get(i % rules.size()), message, status));
        }
        return results;
    }

    private static Measurement run(List<RuleResult> results, Path directory, boolean buffered) throws Exception {
        var dataFile = directory.resolve(buffered ? "buffered.data" : "baseline.data").toFile();
        var markdownFile = directory.resolve(buffered ? "buffered.md" : "baseline.md").toFile();
        long start = System.nanoTime();
        if (buffered) {
            ViolationsUtil.writeDetails(dataFile, results);
        } else {
            writeUnbuffered(dataFile, results);
        }
        long written = System.nanoTime();
        var restored = buffered ? ViolationsUtil.readDetails(dataFile) : readUnbuffered(dataFile);
        long read = System.nanoTime();
        if (!restored.equals(results)) throw new AssertionError("Findings or ordering changed");
        long markdownStart = System.nanoTime();
        try (var output = buffered ? new java.io.BufferedOutputStream(new FileOutputStream(markdownFile)) : new FileOutputStream(markdownFile)) {
            new MarkdownReportPrinter(output).print(restored, false, true, null);
        }
        long markdownEnd = System.nanoTime();
        return new Measurement(written - start, read - written, markdownEnd - markdownStart);
    }

    private static void writeUnbuffered(File file, List<RuleResult> results) throws IOException {
        try (var output = new ObjectOutputStream(new FileOutputStream(file))) {
            output.writeInt(results.size());
            for (var result : results) output.writeObject(result);
        }
    }

    private static List<RuleResult> readUnbuffered(File file) throws Exception {
        try (var input = new ObjectInputStream(new FileInputStream(file))) {
            int count = input.readInt();
            var results = new ArrayList<RuleResult>();
            for (int i = 0; i < count; i++) results.add((RuleResult) input.readObject());
            return results;
        }
    }

    private static void requireSameFile(Path first, Path second) throws IOException {
        if (Files.mismatch(first, second) != -1) throw new AssertionError("Output bytes changed: " + second);
    }

    private static void print(int count, String phase, long[] original, long[] buffered) {
        Arrays.sort(original);
        Arrays.sort(buffered);
        System.out.printf(Locale.ROOT, "%d,%s,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f%n", count, phase,
                median(original), median(buffered), original[0] / 1e6, original[original.length - 1] / 1e6,
                buffered[0] / 1e6, buffered[buffered.length - 1] / 1e6);
    }

    private static double median(long[] sorted) {
        return (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2e6;
    }

    private record Measurement(long write, long read, long markdown) {
        long total() { return write + read + markdown; }
    }
}
