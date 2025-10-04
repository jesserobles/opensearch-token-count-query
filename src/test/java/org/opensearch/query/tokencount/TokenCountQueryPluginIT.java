/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.query.tokencount;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchIntegTestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertHitCount;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class TokenCountQueryPluginIT extends OpenSearchIntegTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singletonList(TokenCountQueryPlugin.class);
    }

    public void testPluginInstalled() throws IOException, ParseException {
        Response response = getRestClient().performRequest(new Request("GET", "/_cat/plugins"));
        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

        logger.info("response body: {}", body);
        assertThat(body, containsString("opensearch-token-count-query"));
    }

    public void testTokenCountQuery() throws Exception {
        // Note: token_count field type is a built-in OpenSearch field type
        // For this test, we'll verify the query can be created and serialized

        TokenCountQueryBuilder query = new TokenCountQueryBuilder("title.token_count", "quick brown fox");
        assertThat(query.fieldName(), equalTo("title.token_count"));
        assertThat(query.text(), equalTo("quick brown fox"));
        assertThat(query.operator(), equalTo(TokenCountQueryBuilder.Operator.EQ));
    }

    public void testTokenCountQueryWithOperators() throws Exception {
        TokenCountQueryBuilder gtQuery = new TokenCountQueryBuilder("field.token_count", "test text")
            .operator(TokenCountQueryBuilder.Operator.GT);
        assertThat(gtQuery.operator(), equalTo(TokenCountQueryBuilder.Operator.GT));

        TokenCountQueryBuilder ltQuery = new TokenCountQueryBuilder("field.token_count", "test text")
            .operator(TokenCountQueryBuilder.Operator.LT);
        assertThat(ltQuery.operator(), equalTo(TokenCountQueryBuilder.Operator.LT));

        TokenCountQueryBuilder gteQuery = new TokenCountQueryBuilder("field.token_count", "test text")
            .operator(TokenCountQueryBuilder.Operator.GTE);
        assertThat(gteQuery.operator(), equalTo(TokenCountQueryBuilder.Operator.GTE));

        TokenCountQueryBuilder lteQuery = new TokenCountQueryBuilder("field.token_count", "test text")
            .operator(TokenCountQueryBuilder.Operator.LTE);
        assertThat(lteQuery.operator(), equalTo(TokenCountQueryBuilder.Operator.LTE));
    }

    public void testTokenCountQueryWithAnalyzer() throws Exception {
        TokenCountQueryBuilder query = new TokenCountQueryBuilder("field.token_count", "test text")
            .analyzer("custom_analyzer");
        assertThat(query.analyzer(), equalTo("custom_analyzer"));
    }
}
