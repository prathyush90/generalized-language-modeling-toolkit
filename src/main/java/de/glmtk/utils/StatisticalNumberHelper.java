package de.glmtk.utils;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper class to determine average byte consumption of types of objects.
 */
public class StatisticalNumberHelper {

    private static final Logger LOGGER = LogManager
            .getLogger(StatisticalNumberHelper.class);

    private static class AverageItem {

        public long max = Long.MIN_VALUE;

        public long min = Long.MAX_VALUE;

        public long number = 0;

        public long count = 0;

    }

    private static Map<String, Long> counters = new HashMap<String, Long>();

    private static Map<String, AverageItem> averages =
            new HashMap<String, AverageItem>();

    public static void count(String name) {
        count(name, 1);
    }

    public static void count(String name, long number) {
        Long counter = counters.get(name);
        if (counter == null) {
            counter = 0L;
        }
        counter += number;
        counters.put(name, counter);
    }

    public static void average(String name, long number) {
        AverageItem average = averages.get(name);
        if (average == null) {
            average = new AverageItem();
            averages.put(name, average);
        }
        if (number > average.max) {
            average.max = number;
        }
        if (number < average.min) {
            average.min = number;
        }
        average.number += number;
        ++average.count;
    }

    public static void print() {
        for (Map.Entry<String, Long> entry : counters.entrySet()) {
            String name = entry.getKey();
            Long counter = entry.getValue();
            LOGGER.debug(name + "Counter = " + counter);
        }
        for (Map.Entry<String, AverageItem> entry : averages.entrySet()) {
            String name = entry.getKey();
            AverageItem average = entry.getValue();
            LOGGER.debug(name + "Average = " + average.number / average.count
                    + " (min=" + average.min + " max=" + average.max + ")");
        }
    }

    public static void reset() {
        averages = new HashMap<String, AverageItem>();
    }

}
