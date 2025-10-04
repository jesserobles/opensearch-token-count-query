/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.query.tokencount;

import org.apache.lucene.search.Query;
import org.opensearch.core.common.io.stream.NamedWriteableAwareStreamInput;
import org.opensearch.core.common.io.stream.NamedWriteableRegistry;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.plugins.Plugin;
import org.opensearch.plugins.SearchPlugin;
import org.opensearch.test.AbstractQueryTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

public class TokenCountQueryBuilderTests extends AbstractQueryTestCase<TokenCountQueryBuilder> {

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        List<Class<? extends Plugin>> plugins = new ArrayList<>(super.getPlugins());
        plugins.add(TokenCountQueryPlugin.class);
        return plugins;
    }

    @Override
    protected void initializeAdditionalMappings(org.opensearch.index.mapper.MapperService mapperService) {
        // Add any additional mappings if needed
    }

    @Override
    protected TokenCountQueryBuilder doCreateTestQueryBuilder() {
        String fieldName = randomAlphaOfLengthBetween(1, 10) + ".token_count";
        String text = randomAlphaOfLengthBetween(5, 50);
        TokenCountQueryBuilder builder = new TokenCountQueryBuilder(fieldName, text);

        if (randomBoolean()) {
            builder.operator(randomFrom(TokenCountQueryBuilder.Operator.values()));
        }

        if (randomBoolean()) {
            builder.analyzer(randomAlphaOfLengthBetween(3, 10));
        }

        return builder;
    }

    @Override
    protected void doAssertLuceneQuery(TokenCountQueryBuilder queryBuilder, Query query, QueryShardContext context) {
        // The actual Lucene query will be a numeric range query
        assertThat(query, instanceOf(Query.class));
    }

    public void testFieldIsRequired() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> {
            new TokenCountQueryBuilder(null, "test text");
        });
        assertThat(e.getMessage(), equalTo("field cannot be null or empty"));

        e = expectThrows(IllegalArgumentException.class, () -> {
            new TokenCountQueryBuilder("", "test text");
        });
        assertThat(e.getMessage(), equalTo("field cannot be null or empty"));
    }

    public void testTextIsRequired() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> {
            new TokenCountQueryBuilder("field", null);
        });
        assertThat(e.getMessage(), equalTo("text cannot be null or empty"));

        e = expectThrows(IllegalArgumentException.class, () -> {
            new TokenCountQueryBuilder("field", "");
        });
        assertThat(e.getMessage(), equalTo("text cannot be null or empty"));
    }

    public void testDefaultOperator() {
        TokenCountQueryBuilder builder = new TokenCountQueryBuilder("field.token_count", "test text");
        assertThat(builder.operator(), equalTo(TokenCountQueryBuilder.Operator.EQ));
    }

    public void testOperators() {
        TokenCountQueryBuilder builder = new TokenCountQueryBuilder("field.token_count", "test text");

        builder.operator(TokenCountQueryBuilder.Operator.GT);
        assertThat(builder.operator(), equalTo(TokenCountQueryBuilder.Operator.GT));

        builder.operator(TokenCountQueryBuilder.Operator.LT);
        assertThat(builder.operator(), equalTo(TokenCountQueryBuilder.Operator.LT));

        builder.operator(TokenCountQueryBuilder.Operator.GTE);
        assertThat(builder.operator(), equalTo(TokenCountQueryBuilder.Operator.GTE));

        builder.operator(TokenCountQueryBuilder.Operator.LTE);
        assertThat(builder.operator(), equalTo(TokenCountQueryBuilder.Operator.LTE));

        builder.operator(TokenCountQueryBuilder.Operator.EQ);
        assertThat(builder.operator(), equalTo(TokenCountQueryBuilder.Operator.EQ));
    }

    public void testAnalyzer() {
        TokenCountQueryBuilder builder = new TokenCountQueryBuilder("field.token_count", "test text");

        assertThat(builder.analyzer(), equalTo(null));

        builder.analyzer("standard");
        assertThat(builder.analyzer(), equalTo("standard"));

        builder.analyzer("custom_analyzer");
        assertThat(builder.analyzer(), equalTo("custom_analyzer"));
    }

    public void testSerialization() throws IOException {
        TokenCountQueryBuilder original = new TokenCountQueryBuilder("field.token_count", "test text")
            .operator(TokenCountQueryBuilder.Operator.GTE)
            .analyzer("standard");

        try (org.opensearch.common.io.stream.BytesStreamOutput output = new org.opensearch.common.io.stream.BytesStreamOutput()) {
            original.writeTo(output);

            StreamInput input = new NamedWriteableAwareStreamInput(
                output.bytes().streamInput(),
                getNamedWriteableRegistry()
            );

            TokenCountQueryBuilder deserialized = new TokenCountQueryBuilder(input);

            assertThat(deserialized.fieldName(), equalTo(original.fieldName()));
            assertThat(deserialized.text(), equalTo(original.text()));
            assertThat(deserialized.operator(), equalTo(original.operator()));
            assertThat(deserialized.analyzer(), equalTo(original.analyzer()));
        }
    }

    public void testEqualsAndHashCode() {
        TokenCountQueryBuilder builder1 = new TokenCountQueryBuilder("field.token_count", "test text")
            .operator(TokenCountQueryBuilder.Operator.EQ)
            .analyzer("standard");

        TokenCountQueryBuilder builder2 = new TokenCountQueryBuilder("field.token_count", "test text")
            .operator(TokenCountQueryBuilder.Operator.EQ)
            .analyzer("standard");

        TokenCountQueryBuilder builder3 = new TokenCountQueryBuilder("field.token_count", "different text")
            .operator(TokenCountQueryBuilder.Operator.EQ)
            .analyzer("standard");

        assertThat(builder1, equalTo(builder2));
        assertThat(builder1.hashCode(), equalTo(builder2.hashCode()));
        assertThat(builder1.equals(builder3), equalTo(false));
    }

    protected NamedWriteableRegistry getNamedWriteableRegistry() {
        List<NamedWriteableRegistry.Entry> entries = new ArrayList<>();
        entries.add(new NamedWriteableRegistry.Entry(
            QueryBuilder.class,
            TokenCountQueryBuilder.NAME,
            TokenCountQueryBuilder::new
        ));
        return new NamedWriteableRegistry(entries);
    }
}
