import com.netflix.nebula.archrules.gradle.report.SourceFileIndex;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Includes index construction; measures path matching without filesystem reads or report rendering. */
public class SourceLookupBenchmark {
    public static void main(String[] arguments) {
        var root = new File(arguments[0]);
        System.out.println("java=" + System.getProperty("java.runtime.version"));
        System.out.println("os=" + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("warmups=3, measured_pairs=10");
        System.out.println("sources,findings,scan_median_ms,index_median_ms,scan_min_ms,scan_max_ms,index_min_ms,index_max_ms");
        for (int[] shape : new int[][] {{100, 100}, {1_000, 10_000}}) {
            var sources = new ArrayList<File>();
            for (int i = 0; i < shape[0]; i++) sources.add(new File(root, "src/main/p" + i + "/Source" + i + ".java"));
            sources.add(new File(root, "src/test/Source0.java"));
            var names = new ArrayList<String>();
            for (int i = 0; i < shape[1]; i++) names.add(i % 20 == 19 ? "Missing.java" : "Source" + i % shape[0] + ".java");
            var scanTimes = new long[10];
            var indexTimes = new long[10];
            for (int i = -3; i < 10; i++) {
                Sample scanned;
                Sample indexed;
                if (i % 2 == 0) {
                    scanned = lookup(root, sources, names, false);
                    indexed = lookup(root, sources, names, true);
                } else {
                    indexed = lookup(root, sources, names, true);
                    scanned = lookup(root, sources, names, false);
                }
                if (!scanned.paths.equals(indexed.paths)) throw new AssertionError("Source selection changed");
                if (i >= 0) {
                    scanTimes[i] = scanned.nanos;
                    indexTimes[i] = indexed.nanos;
                }
            }
            Arrays.sort(scanTimes);
            Arrays.sort(indexTimes);
            System.out.printf(Locale.ROOT, "%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f%n", sources.size(), names.size(),
                    (scanTimes[4] + scanTimes[5]) / 2e6, (indexTimes[4] + indexTimes[5]) / 2e6,
                    scanTimes[0] / 1e6, scanTimes[9] / 1e6, indexTimes[0] / 1e6, indexTimes[9] / 1e6);
        }
    }

    private static Sample lookup(File root, List<File> sources, List<String> names, boolean indexed) {
        long start = System.nanoTime();
        var index = indexed ? new SourceFileIndex(root, sources) : null;
        var paths = new ArrayList<String>(names.size());
        for (var name : names) {
            if (indexed) {
                paths.add(index.pathFor(name));
            } else {
                // Reproduce scanning every source per finding and selecting the first match.
                var matches = sources.stream().filter(file -> file.getName().equals(name))
                        .map(file -> root.toPath().relativize(file.toPath()).toString()).toList();
                paths.add(matches.isEmpty() ? name : matches.get(0));
            }
        }
        return new Sample(System.nanoTime() - start, paths);
    }

    private record Sample(long nanos, List<String> paths) { }
}
