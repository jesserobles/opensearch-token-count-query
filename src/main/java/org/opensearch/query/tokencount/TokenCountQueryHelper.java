/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.query.tokencount;

import org.apache.lucene.document.IntPoint;
import org.apache.lucene.search.Query;

/**
 * Helper class for creating Lucene queries based on token count comparisons.
 */
public class TokenCountQueryHelper {

    /**
     * Creates a Lucene query for the given field, token count, and operator.
     *
     * @param fieldName The field to query
     * @param tokenCount The token count to compare against
     * @param operator The comparison operator
     * @return A Lucene query
     */
    public static Query createQuery(String fieldName, int tokenCount, TokenCountQueryBuilder.Operator operator) {
        switch (operator) {
            case EQ:
                return IntPoint.newExactQuery(fieldName, tokenCount);
            case GT:
                return IntPoint.newRangeQuery(fieldName, tokenCount + 1, Integer.MAX_VALUE);
            case LT:
                return IntPoint.newRangeQuery(fieldName, Integer.MIN_VALUE, tokenCount - 1);
            case GTE:
                return IntPoint.newRangeQuery(fieldName, tokenCount, Integer.MAX_VALUE);
            case LTE:
                return IntPoint.newRangeQuery(fieldName, Integer.MIN_VALUE, tokenCount);
            default:
                throw new IllegalArgumentException("Unknown operator: " + operator);
        }
    }
}
