/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.query.tokencount;

import org.opensearch.core.common.io.stream.NamedWriteableRegistry;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.plugins.Plugin;
import org.opensearch.plugins.SearchPlugin;

import java.util.Collections;
import java.util.List;

/**
 * Plugin that registers the token_count query for server-side text analysis and token counting.
 */
public class TokenCountQueryPlugin extends Plugin implements SearchPlugin {

    @Override
    public List<QuerySpec<?>> getQueries() {
        return Collections.singletonList(
            new QuerySpec<>(
                TokenCountQueryBuilder.NAME,
                TokenCountQueryBuilder::new,
                TokenCountQueryBuilder::fromXContent
            )
        );
    }
}
