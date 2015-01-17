/*
 * Generalized Language Modeling Toolkit (GLMTK)
 *
 * Copyright (C) 2015 Lukas Schmelzeisen
 *
 * GLMTK is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * GLMTK is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * GLMTK. If not, see <http://www.gnu.org/licenses/>.
 *
 * See the AUTHORS file for contributors.
 */

package de.glmtk.querying.estimator.fast;

import java.util.HashMap;
import java.util.Map;

import de.glmtk.common.BackoffMode;
import de.glmtk.common.CountCache;
import de.glmtk.common.NGram;
import de.glmtk.common.Pattern;
import de.glmtk.counts.Counts;
import de.glmtk.counts.Discount;
import de.glmtk.counts.NGramTimes;
import de.glmtk.querying.estimator.Estimator;

public class FastModifiedKneserNeyAbsEstimator extends Estimator {
    protected BackoffMode backoffMode;
    private Map<Pattern, Discount> discounts;

    public FastModifiedKneserNeyAbsEstimator() {
        setBackoffMode(BackoffMode.DEL);
    }

    public void setBackoffMode(BackoffMode backoffMode) {
        if (backoffMode != BackoffMode.DEL && backoffMode != BackoffMode.SKP)
            throw new IllegalArgumentException(
                    "Illegal BackoffMode for this class.");
        this.backoffMode = backoffMode;
    }

    @Override
    public void setCountCache(CountCache countCache) {
        super.setCountCache(countCache);
        discounts = new HashMap<>();
    }

    @Override
    protected double calcProbability(NGram sequence,
                                     NGram history,
                                     int recDepth) {
        double denominator = countCache.getAbsolute(history.concat(NGram.SKP_NGRAM));

        if (history.isEmptyOrOnlySkips()) {
            if (denominator == 0.0)
                return (double) countCache.getAbsolute(sequence.get(0))
                        / countCache.getNumWords();

            double numerator = countCache.getAbsolute(history.concat(sequence));
            return numerator / denominator;
        }

        double discount;
        double gamma = 0.0;
        {
            Discount d = getDiscounts(history.getPattern(), recDepth);
            discount = d.getForCount(countCache.getAbsolute(history));

            if (denominator != 0) {
                Counts c = countCache.getContinuation(history.concat(NGram.WSKP_NGRAM));
                gamma = (d.getOne() * c.getOneCount() + d.getTwo()
                        * c.getTwoCount() + d.getThree()
                        * c.getThreePlusCount())
                        / denominator;
            }
        }

        double alpha;
        if (denominator == 0.0)
            alpha = (double) countCache.getAbsolute(sequence.get(0))
            / countCache.getNumWords();
        else {
            double numerator = countCache.getAbsolute(history.concat(sequence));
            numerator = Math.max(numerator - discount, 0.0);

            alpha = numerator / denominator;
        }

        NGram backoffHistory = history.backoffUntilSeen(backoffMode, countCache);
        double beta = probability(sequence, backoffHistory, recDepth);

        return alpha + gamma * beta;
    }

    protected Discount getDiscounts(Pattern pattern,
                                    @SuppressWarnings("unused") int recDepth) {
        Discount result = discounts.get(pattern);
        if (result != null)
            return result;

        NGramTimes n = countCache.getNGramTimes(pattern);
        double y = (double) n.getOneCount()
                / (n.getOneCount() + n.getTwoCount());
        result = new Discount(1.0f - 2.0f * y * n.getTwoCount()
                / n.getOneCount(), 2.0f - 3.0f * y * n.getThreeCount()
                / n.getTwoCount(), 3.0f - 4.0f * y * n.getFourCount()
                / n.getThreeCount());

        discounts.put(pattern, result);
        return result;
    }

}
