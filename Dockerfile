# Use OpenSearch 3.2.0 to match the plugin version
FROM opensearchproject/opensearch:3.2.0

# Copy the plugin distribution
COPY build/distributions/opensearch-token-count-query-0.1.0.zip /tmp/plugin.zip

# Install the plugin
RUN bin/opensearch-plugin install --batch file:///tmp/plugin.zip
