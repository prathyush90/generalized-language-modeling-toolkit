package de.typology.smoothing;

import java.util.ArrayList;
import java.util.List;

import de.typology.patterns.PatternElem;
import de.typology.utils.StringUtils;

public class MaximumLikelihoodEstimator extends FractionEstimator {

    public MaximumLikelihoodEstimator(
            Corpus corpus) {
        super(corpus);
    }

    @Override
    protected double getNumerator(
            List<String> reqSequence,
            List<String> condSequence,
            int recDepth) {
        List<String> sequence = getSequence(reqSequence, condSequence);
        double sequenceCount = corpus.getAbsolute(sequence);
        debugSequence(sequence, sequenceCount, recDepth);

        return sequenceCount;
    }

    @Override
    protected double getDenominator(
            List<String> reqSequence,
            List<String> condSequence,
            int recDepth) {
        List<String> history = getHistory(reqSequence, condSequence);
        double historyCount;
        if (history.isEmpty()) {
            historyCount = corpus.getNumWords();
        } else {
            historyCount = corpus.getAbsolute(history);
        }
        debugHistory(history, historyCount, recDepth);

        return historyCount;
    }

    /**
     * {@code sequence = condSequence + reqSequence}
     */
    protected List<String> getSequence(
            List<String> reqSequence,
            List<String> condSequence) {
        int n = reqSequence.size() + condSequence.size() - 1;

        List<String> sequence = new ArrayList<String>(n);
        sequence.addAll(condSequence);
        sequence.addAll(reqSequence);

        return sequence;
    }

    /**
     * {@code history = condSequence + skp (reqSequence.size)}
     */
    protected List<String> getHistory(
            List<String> reqSequence,
            List<String> condSequence) {
        int n = reqSequence.size() + condSequence.size() - 1;

        List<String> history = new ArrayList<String>(n);
        history.addAll(condSequence);
        for (int i = 0; i != reqSequence.size(); ++i) {
            history.add(PatternElem.SKIPPED_WORD);
        }

        return history;
    }

    protected void debugSequence(
            List<String> sequence,
            double sequenceCount,
            int recDepth) {
        logger.debug(StringUtils.repeat("  ", recDepth) + "sequence = " + sequence
                + "(count = " + sequenceCount + ")");
    }

    protected void debugHistory(
            List<String> history,
            double historyCount,
            int recDepth) {
        logger.debug(StringUtils.repeat("  ", recDepth) + "history = " + history
                + "(count = " + historyCount + ")");
    }

}
