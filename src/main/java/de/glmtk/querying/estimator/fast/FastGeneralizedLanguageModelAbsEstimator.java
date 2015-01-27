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

import static de.glmtk.common.NGram.WSKP_NGRAM;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import de.glmtk.cache.Cache;
import de.glmtk.common.BackoffMode;
import de.glmtk.common.NGram;
import de.glmtk.common.Pattern;
import de.glmtk.counts.Counts;
import de.glmtk.counts.Discount;
import de.glmtk.counts.NGramTimes;
import de.glmtk.querying.estimator.AbstractEstimator;

public class FastGeneralizedLanguageModelAbsEstimator extends AbstractEstimator {
    protected BackoffMode backoffMode;
    private Map<Pattern, Discount> discounts;

    public FastGeneralizedLanguageModelAbsEstimator() {
        setBackoffMode(BackoffMode.SKP);
    }

    public void setBackoffMode(BackoffMode backoffMode) {
        this.backoffMode = backoffMode;
    }

    @Override
    public void setCache(Cache cache) {
        super.setCache(cache);
        discounts = new HashMap<>();
    }

    @Override
    protected double calcProbability(NGram sequence,
                                     NGram history,
                                     int recDepth) {
        double denominator = cache.getAbsolute(getFullHistory(sequence, history));
        if (denominator == 0.0)
            return (double) cache.getAbsolute(sequence) / cache.getNumWords();

        double numerator = cache.getAbsolute(getFullSequence(sequence, history));
        if (history.isEmptyOrOnlySkips())
            return numerator / denominator;

        Discount d = getDiscounts(history.getPattern(), recDepth);
        double discount = d.getForCount(cache.getAbsolute(history));

        Counts c = cache.getContinuation(history.concat(WSKP_NGRAM));
        double gamma = (d.getOne() * c.getOneCount() + d.getTwo()
                * c.getTwoCount() + d.getThree() * c.getThreePlusCount())
                / denominator;

        double alpha = Math.max(numerator - discount, 0.0) / denominator;
        double beta = 0;
        Set<NGram> differentiatedHistories = history.getDifferentiatedNGrams(backoffMode);
        for (NGram differentiatedHistory : differentiatedHistories)
            beta += probability(sequence, differentiatedHistory, recDepth);
        beta /= differentiatedHistories.size();

        return alpha + gamma * beta;
    }

    protected Discount getDiscounts(Pattern pattern,
                                    @SuppressWarnings("unused") int recDepth) {
        Discount result = discounts.get(pattern);
        if (result != null)
            return result;

        NGramTimes n = cache.getNGramTimes(pattern);
        double y = (double) n.getOneCount()
                / (n.getOneCount() + n.getTwoCount());
        result = new Discount(1.0f - 2.0f * y * n.getTwoCount()
                / n.getOneCount(), 2.0f - 3.0f * y * n.getThreeCount()
                / n.getTwoCount(), 3.0f - 4.0f * y * n.getFourCount()
                / n.getThreeCount());

        discounts.put(pattern, result);
        return result;
    }

    //    @Override
    //    public CacheBuilder getRequiredCache(int modelSize) {
    //        try {
    //            CacheBuilder requiredCache = super.getRequiredCache(modelSize);
    //            Field countsField = CacheBuilder.class.getDeclaredField("counts");
    //            countsField.setAccessible(true);
    //            @SuppressWarnings("unchecked")
    //            Set<Pattern> countsPatterns = (Set<Pattern>) countsField.get(requiredCache);
    //            //            countsPatterns.removeAll(Patterns.getMany("1xx", "11xx", "11xxx"));
    //            return requiredCache;
    //        } catch (Throwable t) {
    //            throw new RuntimeException(t);
    //        }
    //    }
}
