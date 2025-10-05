/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.query.tokencount;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;

/**
 * Utility class for analyzing text and counting tokens.
 * Uses position increments for accurate counting, matching the behavior of TokenCountFieldMapper.
 */
public class TokenCountAnalyzer {

    /**
     * Analyzes the given text with the specified analyzer and counts the tokens.
     * This uses position increments to accurately count tokens, which properly handles
     * cases like synonyms and other multi-term tokens.
     *
     * @param analyzer The analyzer to use
     * @param text The text to analyze
     * @return The number of token positions produced by the analyzer
     * @throws IOException If an I/O error occurs during analysis
     */
    public static int countTokens(Analyzer analyzer, String text) throws IOException {
        if (analyzer == null) {
            throw new IllegalArgumentException("Analyzer cannot be null");
        }
        if (text == null || text.isEmpty()) {
            return 0;
        }

        // Implementation based on TokenCountFieldMapper.countPositions()
        // from org.opensearch.index.mapper.TokenCountFieldMapper
        try (TokenStream tokenStream = analyzer.tokenStream("field", text)) {
            int count = 0;
            PositionIncrementAttribute position = tokenStream.addAttribute(PositionIncrementAttribute.class);
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                count += position.getPositionIncrement();
            }
            tokenStream.end();
            count += position.getPositionIncrement();
            return count;
        }
    }
}
