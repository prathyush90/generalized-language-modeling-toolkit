package de.glmtk.counting;

import static de.glmtk.Config.CONFIG;
import static de.glmtk.common.Output.OUTPUT;
import static de.glmtk.util.PrintUtils.humanReadableByteCount;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.glmtk.Constants;
import de.glmtk.api.Status;
import de.glmtk.common.Output.Phase;
import de.glmtk.common.Output.Progress;
import de.glmtk.common.Pattern;
import de.glmtk.util.NioUtils;
import de.glmtk.util.StringUtils;
import de.glmtk.util.ThreadUtils;

public enum QueryCacherCreator {
    QUERY_CACHE_CREATOR;

    private static final Logger LOGGER = LogManager.getFormatterLogger(QueryCacherCreator.class);

    private class Thread implements Callable<Object> {
        private Pattern pattern;
        private Path patternFile;
        private Path targetPatternFile;
        private Queue<String> neededSequences;

        @Override
        public Object call() throws Exception {
            while (!patternQueue.isEmpty()) {
                pattern = patternQueue.poll(Constants.QUEUE_TIMEOUT,
                        TimeUnit.MILLISECONDS);
                if (pattern == null)
                    continue;

                LOGGER.debug("Caching pattern '%s'.", pattern);

                extractSequences();
                getPatternFiles();
                filterAndWriteSequenceCounts();

                status.addQueryCacheCounted(name, pattern);

                LOGGER.debug("Finished pattern '%s'.", pattern);

                synchronized (progress) {
                    progress.increase(1);
                }
            }

            LOGGER.debug("Thread finished.");
            return null;
        }

        private void extractSequences() throws IOException {
            Set<String> sequences = new HashSet<String>();

            int patternSize = pattern.size();
            try (BufferedReader reader = NioUtils.newBufferedReader(queryFile,
                    Constants.CHARSET, readerMemory)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] split = StringUtils.splitAtChar(line, ' ').toArray(
                            new String[0]);
                    String[] words = new String[split.length];
                    String[] poses = new String[split.length];
                    StringUtils.extractWordsAndPoses(split, queryFileTagged,
                            words, poses);

                    for (int p = 0; p <= split.length - patternSize; ++p) {
                        String sequence = pattern.apply(words, poses, p);
                        sequences.add(sequence);
                    }
                }
            }

            neededSequences = new LinkedList<String>();
            neededSequences.addAll(new TreeSet<String>(sequences));
        }

        private void getPatternFiles() {
            if (pattern.isAbsolute()) {
                patternFile = absoluteDir.resolve(pattern.toString());
                targetPatternFile = targetAbsoluteDir.resolve(pattern.toString());
            } else {
                patternFile = continuationDir.resolve(pattern.toString());
                targetPatternFile = targetContinuationDir.resolve(pattern.toString());
            }
        }

        private void filterAndWriteSequenceCounts() throws IOException {
            try (BufferedReader reader = NioUtils.newBufferedReader(
                    patternFile, Constants.CHARSET, readerMemory);
                    BufferedWriter writer = NioUtils.newBufferedWriter(
                            targetPatternFile, Constants.CHARSET, writerMemory)) {
                String nextSequence = neededSequences.poll();
                if (nextSequence == null)
                    return;

                String line;
                while ((line = reader.readLine()) != null) {
                    int p = line.indexOf('\t');
                    String sequence = p == -1 ? line : line.substring(0, p);

                    int cmp;
                    while (nextSequence != null
                            && (cmp = sequence.compareTo(nextSequence)) >= 0) {
                        if (cmp == 0) {
                            writer.write(line);
                            writer.write('\n');
                        }

                        nextSequence = neededSequences.poll();
                    }
                }
            }
        }
    }

    private Progress progress;
    private Status status;
    private String name;
    private Path queryFile;
    private boolean queryFileTagged;
    private Path absoluteDir;
    private Path continuationDir;
    private Path targetAbsoluteDir;
    private Path targetContinuationDir;
    private BlockingQueue<Pattern> patternQueue;
    private int readerMemory;
    private int writerMemory;

    public void createQueryCache(Status status,
                                 Set<Pattern> patterns,
                                 String name,
                                 Path queryFile,
                                 boolean queryFileTagged,
                                 Path absoluteDir,
                                 Path continuationDir,
                                 Path targetAbsoluteDir,
                                 Path targetContinuationDir) throws Exception {
        OUTPUT.setPhase(Phase.SCANNING_COUNTS);

        LOGGER.debug("patterns = '%s'", patterns);
        if (patterns.isEmpty())
            return;

        Files.createDirectories(targetAbsoluteDir);
        Files.createDirectories(targetContinuationDir);

        this.status = status;
        this.name = name;
        this.queryFile = queryFile;
        this.queryFileTagged = queryFileTagged;
        this.absoluteDir = absoluteDir;
        this.continuationDir = continuationDir;
        this.targetAbsoluteDir = targetAbsoluteDir;
        this.targetContinuationDir = targetContinuationDir;
        patternQueue = new LinkedBlockingQueue<Pattern>();
        patternQueue.addAll(patterns);
        calculateMemory();

        List<Callable<Object>> threads = new LinkedList<Callable<Object>>();
        for (int i = 0; i != CONFIG.getNumberOfCores(); ++i)
            threads.add(new Thread());

        progress = new Progress(patternQueue.size());
        ThreadUtils.executeThreads(CONFIG.getNumberOfCores(), threads);
    }

    private void calculateMemory() {
        double AVAILABLE_MEM_RATIO = 0.5;

        Runtime r = Runtime.getRuntime();
        r.gc();

        long totalFreeMem = r.maxMemory() - r.totalMemory() + r.freeMemory();
        long availableMem = (long) (AVAILABLE_MEM_RATIO * totalFreeMem);
        long memPerThread = availableMem / CONFIG.getNumberOfCores();

        readerMemory = Constants.BUFFER_SIZE;
        writerMemory = Constants.BUFFER_SIZE;

        LOGGER.debug("totalFreeMem = %s", humanReadableByteCount(totalFreeMem));
        LOGGER.debug("availableMem = %s", humanReadableByteCount(availableMem));
        LOGGER.debug("memPerThread = %s", humanReadableByteCount(memPerThread));
        LOGGER.debug("readerMemory = %s", humanReadableByteCount(readerMemory));
        LOGGER.debug("writerMemory = %s", humanReadableByteCount(writerMemory));
    }
}