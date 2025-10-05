/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.query.tokencount;

import org.apache.lucene.search.Query;
import org.opensearch.core.common.ParsingException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.AbstractQueryBuilder;
import org.opensearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * A query that matches documents based on the token count of analyzed text.
 * This performs server-side analysis of the provided text and compares the token count
 * against a token_count field using the specified operator.
 */
public class TokenCountQueryBuilder extends AbstractQueryBuilder<TokenCountQueryBuilder> {

    public static final String NAME = "token_count";

    private final String fieldName;
    private final String text;
    private Operator operator = Operator.EQ;
    private String analyzer;

    /**
     * Comparison operators for token count matching
     */
    public enum Operator {
        EQ("eq"),
        GT("gt"),
        LT("lt"),
        GTE("gte"),
        LTE("lte");

        private final String name;

        Operator(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static Operator fromString(String op) {
            for (Operator operator : values()) {
                if (operator.name.equalsIgnoreCase(op)) {
                    return operator;
                }
            }
            throw new IllegalArgumentException("Unknown operator: " + op);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Constructs a new token count query.
     *
     * @param fieldName The token_count field to query against
     * @param text The text to analyze for token counting
     */
    public TokenCountQueryBuilder(String fieldName, String text) {
        if (fieldName == null || fieldName.isEmpty()) {
            throw new IllegalArgumentException("field cannot be null or empty");
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text cannot be null or empty");
        }
        this.fieldName = fieldName;
        this.text = text;
    }

    /**
     * Read from a stream.
     */
    public TokenCountQueryBuilder(StreamInput in) throws IOException {
        super(in);
        this.fieldName = in.readString();
        this.text = in.readString();
        this.operator = Operator.valueOf(in.readString());
        this.analyzer = in.readOptionalString();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(fieldName);
        out.writeString(text);
        out.writeString(operator.name());
        out.writeOptionalString(analyzer);
    }

    /**
     * @return The field name
     */
    public String fieldName() {
        return fieldName;
    }

    /**
     * @return The text to analyze
     */
    public String text() {
        return text;
    }

    /**
     * @return The comparison operator
     */
    public Operator operator() {
        return operator;
    }

    /**
     * Sets the comparison operator.
     */
    public TokenCountQueryBuilder operator(Operator operator) {
        this.operator = operator;
        return this;
    }

    /**
     * @return The analyzer name, or null if using the field's analyzer
     */
    public String analyzer() {
        return analyzer;
    }

    /**
     * Sets the analyzer to use for token counting.
     * If not set, the analyzer configured for the parent field will be used.
     */
    public TokenCountQueryBuilder analyzer(String analyzer) {
        this.analyzer = analyzer;
        return this;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.field("field", fieldName);
        builder.field("text", text);
        builder.field("operator", operator.getName());
        if (analyzer != null) {
            builder.field("analyzer", analyzer);
        }
        printBoostAndQueryName(builder);
        builder.endObject();
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        // Get the appropriate analyzer
        String analyzerName = this.analyzer;
        if (analyzerName == null) {
            // Extract the parent field name from the token_count field
            // e.g., "title.token_count" -> "title"
            String parentField = fieldName;
            int dotIndex = fieldName.lastIndexOf('.');
            if (dotIndex > 0) {
                parentField = fieldName.substring(0, dotIndex);
            }

            // Get the analyzer from the parent field's mapping
            if (context.getMapperService().fieldType(parentField) != null) {
                analyzerName = context.getMapperService().fieldType(parentField).getTextSearchInfo().getSearchAnalyzer().name();
            }
        }

        // Fall back to standard analyzer if none specified
        if (analyzerName == null) {
            analyzerName = "standard";
        }

        // Analyze the text to get token count
        org.apache.lucene.analysis.Analyzer luceneAnalyzer = context.getIndexAnalyzers().get(analyzerName);
        if (luceneAnalyzer == null) {
            // Fall back to the default analyzer
            luceneAnalyzer = context.getIndexAnalyzers().getDefaultIndexAnalyzer();
        }

        int tokenCount = TokenCountAnalyzer.countTokens(luceneAnalyzer, text);

        // Create the appropriate numeric range query based on the operator
        return TokenCountQueryHelper.createQuery(fieldName, tokenCount, operator);
    }

    @Override
    protected boolean doEquals(TokenCountQueryBuilder other) {
        return Objects.equals(fieldName, other.fieldName)
            && Objects.equals(text, other.text)
            && Objects.equals(operator, other.operator)
            && Objects.equals(analyzer, other.analyzer);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(fieldName, text, operator, analyzer);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    /**
     * Parse a token_count query from XContent.
     */
    public static TokenCountQueryBuilder fromXContent(XContentParser parser) throws IOException {
        String fieldName = null;
        String text = null;
        Operator operator = Operator.EQ;
        String analyzer = null;
        String queryName = null;
        float boost = DEFAULT_BOOST;

        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                if ("field".equals(currentFieldName)) {
                    fieldName = parser.text();
                } else if ("text".equals(currentFieldName)) {
                    text = parser.text();
                } else if ("operator".equals(currentFieldName)) {
                    operator = Operator.fromString(parser.text());
                } else if ("analyzer".equals(currentFieldName)) {
                    analyzer = parser.text();
                } else if (AbstractQueryBuilder.BOOST_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                    boost = parser.floatValue();
                } else if (AbstractQueryBuilder.NAME_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                    queryName = parser.text();
                } else {
                    throw new ParsingException(
                        parser.getTokenLocation(),
                        "[" + NAME + "] query does not support [" + currentFieldName + "]"
                    );
                }
            } else {
                throw new ParsingException(
                    parser.getTokenLocation(),
                    "[" + NAME + "] unknown token [" + token + "] after [" + currentFieldName + "]"
                );
            }
        }

        if (fieldName == null) {
            throw new ParsingException(parser.getTokenLocation(), "[" + NAME + "] requires 'field' parameter");
        }
        if (text == null) {
            throw new ParsingException(parser.getTokenLocation(), "[" + NAME + "] requires 'text' parameter");
        }

        TokenCountQueryBuilder queryBuilder = new TokenCountQueryBuilder(fieldName, text);
        queryBuilder.operator(operator);
        if (analyzer != null) {
            queryBuilder.analyzer(analyzer);
        }
        queryBuilder.boost(boost);
        queryBuilder.queryName(queryName);
        return queryBuilder;
    }
}
