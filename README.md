# OpenSearch Token Count Query Plugin

An OpenSearch plugin that implements a `token_count` query for server-side text analysis.

## Quick Example

### Why Server-Side Tokenization Matters

When analyzers modify text (stop words, synonyms), client-side token counting breaks. In order to use `token_count`, currently the client needs to perform anaysis to get the token counts. This can be either splitting the text (e.g., `text.split()`) or using the `_analyze` API to determine the token counts. The `_analyze` is preferable but may be confusing to process because OpenSearch uses **position-based counting** during indexing, not simple token counts. It also requires an extra API call. Let's take a look at some examples, starting with synonyms:

### Try It Yourself

**In OpenSearch Dashboards Dev Tools:**

```json
# Create index with synonym analyzer
PUT /synonym_test
{
  "settings": {
    "analysis": {
      "filter": {
        "synonym_filter": {
          "type": "synonym",
          "synonyms": ["laptop, notebook"]
        }
      },
      "analyzer": {
        "synonym_analyzer": {
          "tokenizer": "standard",
          "filter": ["lowercase", "synonym_filter"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "title": {
        "type": "text",
        "analyzer": "synonym_analyzer",
        "fields": {
          "num_words": {
            "type": "token_count",
            "analyzer": "synonym_analyzer"
          }
        }
      }
    }
  }
}

# Index a document
POST /synonym_test/_doc/1
{
  "title": "laptop computer"
}

# Check what _analyze returns (3 tokens)
POST /synonym_test/_analyze
{
  "analyzer": "synonym_analyzer",
  "text": "laptop computer"
}
```

**The _analyze API response:**

```json
{
  "tokens": [
    {
      "token": "laptop",
      "start_offset": 0,
      "end_offset": 6,
      "type": "<ALPHANUM>",
      "position": 0
    },
    {
      "token": "notebook",
      "start_offset": 0,
      "end_offset": 6,
      "type": "SYNONYM",
      "position": 0
    },
    {
      "token": "computer",
      "start_offset": 7,
      "end_offset": 15,
      "type": "<ALPHANUM>",
      "position": 1
    }
  ]
}
```

The API returns 3 tokens, but notice both `laptop` and `notebook` have `position: 0` (they're synonyms). OpenSearch uses position-based counting during indexing, so this counts as **2 tokens**, not 3.

**Naive approach using the 3-token count from _analyze (FAILS):**

```json
GET /synonym_test/_search
{
  "query": {
    "bool": {
      "must": [
        { "match_phrase": { "title": "laptop computer" } },
        { "term": { "title.num_words": 3 } }
      ]
    }
  }
}
```

**Response: 0 results** (the field has 2 tokens, not 3)

**Using this plugin (SUCCEEDS):**

```json
GET /synonym_test/_search
{
  "query": {
    "bool": {
      "must": [
        { "match_phrase": { "title": "laptop computer" } },
        {
          "token_count": {
            "field": "title.num_words",
            "text": "laptop computer",
            "operator": "eq"
          }
        }
      ]
    }
  }
}
```

**Response: 1 result** - The plugin correctly handles position-based counting!

---

**Stop word example:**

```json
# Create index with stop word analyzer
PUT /stopword_test
{
  "settings": {
    "analysis": {
      "analyzer": {
        "english_stop": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "english_stop"]
        }
      },
      "filter": {
        "english_stop": {
          "type": "stop",
          "stopwords": "_english_"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "title": {
        "type": "text",
        "analyzer": "english_stop",
        "fields": {
          "num_words": {
            "type": "token_count",
            "analyzer": "english_stop"
          }
        }
      }
    }
  }
}

# Index a document
POST /stopword_test/_doc/1
{
  "title": "The quick brown fox" # The is removed during anaylysis since it's a stopword
}

# Check what _analyze returns
POST /stopword_test/_analyze
{
  "analyzer": "english_stop",
  "text": "The quick brown fox"
}

# Returns 3 tokens: ["quick", "brown", "fox"]

# Naive approach: count tokens returned by `_analyze` API = 3 tokens (FAILS)
GET /stopword_test/_search
{
  "query": {
    "bool": {
      "must": [
        { "match_phrase": { "title": "the quick brown fox" } },
        { "term": { "title.num_words": 3 } }
      ]
    }
  }
}

# Response: 0 results (field has 4 token positions)

# Using the token_count query (SUCCEEDS)
GET /stopword_test/_search
{
  "query": {
    "bool": {
      "must": [
        { "match_phrase": { "title": "the quick brown fox" } },
        {
          "token_count": {
            "field": "title.num_words",
            "text": "the quick brown fox",
            "operator": "eq"
          }
        }
      ]
    }
  }
}

# Response: 1 result - The plugin analyzes "the quick brown fox" server-side,
# correctly counting token positions after stop word removal

Here is an example where naive string splitting on the client side can lead to incorrect results
```

---

**Naive string splitting example:**

```json
# Create index with standard analyzer
PUT /punctuation_test
{
  "mappings": {
    "properties": {
      "title": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "num_words": {
            "type": "token_count",
            "analyzer": "standard"
          }
        }
      }
    }
  }
}

# Index a document
POST /punctuation_test/_doc/1
{
  "title": "Wi-Fi router"  # Hyphen is removed during analysis
}

# If you split client-side: "Wi-Fi router".split() = ["Wi-Fi", "router"] = 2 tokens
# But standard analyzer produces: ["wi", "fi", "router"] = 3 tokens

# Naive approach: count by splitting = 2 tokens (FAILS)
GET /punctuation_test/_search
{
  "query": {
    "bool": {
      "must": [
        { "match_phrase": { "title": "Wi-Fi router" } },
        { "term": { "title.num_words": 2 } }
      ]
    }
  }
}

# Response: 0 results (field has 3 tokens: "wi", "fi", "router")

# Using the token_count query (SUCCEEDS)
GET /punctuation_test/_search
{
  "query": {
    "bool": {
      "must": [
        { "match_phrase": { "title": "Wi-Fi router" } },
        {
          "token_count": {
            "field": "title.num_words",
            "text": "Wi-Fi router",
            "operator": "eq"
          }
        }
      ]
    }
  }
}

# Response: 1 result - Plugin correctly analyzes "Wi-Fi router" as 3 tokens
```

**Key Point:** The `token_count` field type uses position-based counting, not simple token counts. This plugin replicates the same logic, so you get accurate matches without client-side token counting.


### Works with Complex Analysis

The plugin handles all analyzer features automatically:

**With stemming:**
- ✅ `"Running Shoes"` matches query `"running shoes"` (both stem to `["run", "shoe"]`)
- ✅ `"Running Shoe"` also matches (stems to same tokens)

**With punctuation:**
- ✅ `"Running-Shoes"` matches query `"running shoes"` (hyphen is removed → 2 tokens)

**Still prevents partial matches:**
- ❌ `"Best Running Shoes"` doesn't match (3 tokens, not 2)
- ❌ `"Running Shoes for Marathon"` doesn't match (4 tokens, not 2)

The key advantage: you get **stemming, case-insensitivity, stop word removal, punctuation handling, and other text analysis features** while still ensuring the field contains only the phrase you're searching for. The plugin uses server-side tokenization, so you don't need to replicate analyzer logic on the client.

## The Problem

When searching for phrases in OpenSearch, `match_phrase` queries will match documents where the phrase appears anywhere in the field, even if the field contains additional text:

```json
{
  "query": {
    "match_phrase": {
      "product_name": "wireless mouse"
    }
  }
}
```

This query matches **both** of these documents:
- ✅ `"Wireless Mouse"` ← **Want this**
- ✅ `"Wireless Mouse with RGB Lighting"` ← **Don't want this**

While BM25 scoring prioritizes shorter matches, sometimes you need to **only** match documents where the field contains just the phrase and nothing more.

## The Solution

This plugin allows you to combine phrase matching with token count verification. By ensuring the field has the same number of tokens as your search phrase (after analysis), you get exact phrase-only matches while still benefiting from text analysis features like stemming, synonyms, and stop words.

### Why Not Use a Keyword Field?

You could use a `keyword` field for exact matching, but that sacrifices all text analysis:

**Keyword field approach:**
- ❌ No stemming: `"review"` won't match `"reviews"`
- ❌ No case-insensitivity: `"Steam"` won't match `"steam"`
- ❌ No synonyms or custom analysis
- ❌ No slop/fuzzy matching

**Token count query approach:**
- ✅ Stemming works: `"reviews"` matches `"review"`
- ✅ Case-insensitive: `"Steam"` matches `"steam"`
- ✅ Full analyzer support: synonyms, stop words, etc.
- ✅ Can combine with slop for flexible phrase matching
- ✅ Still ensures exact phrase length

### Why Not Use the _analyze API + Term/Range Queries?

You could use the `_analyze` API to count tokens, then use a `term` query:

```bash
# Step 1: Analyze the text
POST /my-index/_analyze
{
  "analyzer": "my_analyzer",
  "text": "laptop computer"
}

# Step 2: Count tokens from response
# Step 3: Use the count in a term query
```

**Problems with this approach:**

1. **Synonym expansion breaks token counting:**
   - If your analyzer has `"laptop" => "laptop, notebook"`, the `_analyze` API might return `["laptop", "notebook", "computer"]` (3 tokens)
   - But during indexing, OpenSearch uses **position-based counting**, so synonyms at the same position count as 1
   - Your manual count of 3 won't match the field's stored count of 2
   - The plugin uses the same `PositionIncrementAttribute` logic as indexing

2. **Multi-step process is error-prone:**
   - ❌ Extra network round-trip
   - ❌ Need to parse JSON response and count tokens
   - ❌ Need to handle different synonym filter configurations
   - ❌ Must track position increments, not just token count
   - ❌ Code breaks if analyzer changes

3. **Position increment complexity:**
   ```json
   {
     "tokens": [
       {"token": "laptop", "position": 0},
       {"token": "notebook", "position": 0},  // Same position as "laptop"
       {"token": "computer", "position": 1}
     ]
   }
   ```
   Counting tokens gives 3, but correct position-based count is 2.

**Token count query approach:**
- ✅ Single query, no extra API calls
- ✅ Automatically handles position increments correctly
- ✅ Works with all synonym configurations
- ✅ Uses same logic as `TokenCountFieldMapper`
- ✅ Analyzer changes automatically propagate

This plugin complements OpenSearch's built-in [`token_count` field type](https://docs.opensearch.org/latest/field-types/supported-field-types/token-count/) by providing server-side token counting that matches your query analyzer, eliminating client-side token count calculation.

## Features

- **Exact phrase-only matching**: Combine with `match_phrase` to match only fields that contain exactly the search phrase
- **Analyzer-aware**: Uses the same analyzer as your query, automatically handling stemming, synonyms, stop words, etc.
- **Server-side token counting**: No need to calculate token counts on the client
- **Flexible operators**: `eq`, `gt`, `lt`, `gte`, `lte` for various matching scenarios
- **Works with stemming and slop**: Still get exact-phrase matching even with text analysis features
- **Position-based counting**: Uses the same token counting logic as OpenSearch's built-in `token_count` field mapper

## Usage

### Exact Phrase-Only Matching

Combine `match_phrase` with `token_count` to match only documents where the field contains exactly the search phrase:

```json
{
  "query": {
    "bool": {
      "must": [
        {
          "match_phrase": {
            "product_name": "wireless mouse"
          }
        },
        {
          "token_count": {
            "field": "product_name.num_words",
            "text": "wireless mouse",
            "operator": "eq"
          }
        }
      ]
    }
  }
}
```

**Results:**
- ✅ Matches: `"Wireless Mouse"` (2 tokens)
- ❌ Doesn't match: `"Wireless Mouse with RGB Lighting"` (5 tokens)
- ✅ Matches: `"Wireless Mice"` (if using stemming analyzer - "mice" stems to "mouse")

### Working with Stemming

The plugin respects your analyzer settings, so stemming works automatically:

```json
{
  "query": {
    "bool": {
      "must": [
        {
          "match_phrase": {
            "category": "running shoe"
          }
        },
        {
          "token_count": {
            "field": "category.num_words",
            "text": "running shoe",
            "operator": "eq"
          }
        }
      ]
    }
  }
}
```

With an English analyzer, this matches both:
- `"Running Shoe"`
- `"Running Shoes"` (stems to same tokens)

But doesn't match:
- `"Best Running Shoes for Marathon Training"`

### Using with Slop

You can use slop in your `match_phrase` query while still ensuring exact field length:

```json
{
  "query": {
    "bool": {
      "must": [
        {
          "match_phrase": {
            "skill": {
              "query": "learning machine",
              "slop": 1
            }
          }
        },
        {
          "token_count": {
            "field": "skill.num_words",
            "text": "learning machine",
            "operator": "eq"
          }
        }
      ]
    }
  }
}
```

Matches: `"Machine Learning"` (tokens can be reordered with slop, but field still has exactly 2 tokens)

### Other Use Cases

**Finding Documents by Length:**

Use operators to find documents by token count ranges:

```json
{
  "query": {
    "token_count": {
      "field": "description.num_words",
      "text": "short product description here",
      "operator": "lte"
    }
  }
}
```

Finds all documents with descriptions of 5 words or fewer.

**Reverse Percolation Pattern:**

Find stored queries/documents that match the length of incoming text:

```json
{
  "query": {
    "token_count": {
      "field": "query_template.num_words",
      "text": "user input text here",
      "operator": "eq"
    }
  }
}
```

This can be useful for matching user input against stored templates or patterns based on length.

**Dynamic Length Filtering with Custom Analyzers:**

Filter documents based on analyzed length without pre-calculating on the client:

```json
{
  "query": {
    "bool": {
      "filter": [
        {
          "token_count": {
            "field": "tweet.num_words",
            "text": "This is a sample tweet with hashtags #opensearch #search",
            "analyzer": "hashtag_analyzer",
            "operator": "lte"
          }
        }
      ]
    }
  }
}
```

Finds tweets with the same or fewer tokens than the input when analyzed with a custom hashtag analyzer. Without this plugin, you'd need to:
1. Replicate the `hashtag_analyzer` logic client-side
2. Count tokens yourself
3. Use a range query
4. Keep client and server analyzers in sync

**Synonym-Aware Length Matching:**

When using synonym filters, match based on post-synonym token count:

```json
{
  "query": {
    "token_count": {
      "field": "product_name.num_words",
      "text": "laptop computer",
      "operator": "eq"
    }
  }
}
```

If your analyzer expands "laptop" to "laptop, notebook", the server-side counting handles this automatically. Client-side counting would require duplicating your synonym configuration.

### Available Operators

- `eq` - Equals (default) - exact match
- `gt` - Greater than - field has more tokens
- `lt` - Less than - field has fewer tokens
- `gte` - Greater than or equal to
- `lte` - Less than or equal to

## Query Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `field` | Yes | The field name to query against (typically a `token_count` field) |
| `text` | Yes | The text to analyze and count tokens |
| `operator` | No | Comparison operator (default: `eq`) |
| `analyzer` | No | Override the analyzer to use for counting tokens |

## Complete Example

### Exact Phrase Matching for Product Titles

This example demonstrates using the plugin for exact phrase-only matching in a product search scenario.

**Create Index:**

```bash
curl -X PUT "localhost:9200/products" -H 'Content-Type: application/json' -d'
{
  "settings": {
    "analysis": {
      "analyzer": {
        "title_analyzer": {
          "type": "standard"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "title": {
        "type": "text",
        "analyzer": "title_analyzer",
        "fields": {
          "num_words": {
            "type": "token_count",
            "analyzer": "title_analyzer"
          }
        }
      }
    }
  }
}'
```

**Index Sample Products:**

```bash
curl -X POST "localhost:9200/products/_doc/1" -H 'Content-Type: application/json' -d'
{
  "title": "Wireless Keyboard"
}'

curl -X POST "localhost:9200/products/_doc/2" -H 'Content-Type: application/json' -d'
{
  "title": "Wireless Mouse"
}'

curl -X POST "localhost:9200/products/_doc/3" -H 'Content-Type: application/json' -d'
{
  "title": "Wireless Mouse with RGB Lighting"
}'

curl -X POST "localhost:9200/products/_doc/4" -H 'Content-Type: application/json' -d'
{
  "title": "Mechanical Keyboard"
}'
```

**Search for Exact Phrase Only:**

Find products titled exactly "Wireless Mouse" (and nothing more):

```bash
curl -X GET "localhost:9200/products/_search?pretty" -H 'Content-Type: application/json' -d'
{
  "query": {
    "bool": {
      "must": [
        {
          "match_phrase": {
            "title": "wireless mouse"
          }
        },
        {
          "token_count": {
            "field": "title.num_words",
            "text": "wireless mouse",
            "operator": "eq"
          }
        }
      ]
    }
  }
}'
```

**Results:**
- ✅ Matches document 2: `"Wireless Mouse"`
- ❌ Doesn't match document 3: `"Wireless Mouse with RGB Lighting"` (has extra words)

**Comparison: Without token_count:**

```bash
curl -X GET "localhost:9200/products/_search?pretty" -H 'Content-Type: application/json' -d'
{
  "query": {
    "match_phrase": {
      "title": "wireless mouse"
    }
  }
}'
```

This matches **both** documents 2 and 3, which may not be what you want.

### Example 2: Form Field Validation and Matching

Match user input length against stored constraints or similar entries. This is useful for finding forms, templates, or entries with similar structure.

**Use Case: Find Similar Form Responses**

When analyzing survey responses, find all responses with similar length to identify patterns or group similar answer styles.

**Create Index:**

```bash
curl -X PUT "localhost:9200/survey_responses" -H 'Content-Type: application/json' -d'
{
  "mappings": {
    "properties": {
      "question": {
        "type": "text"
      },
      "answer": {
        "type": "text"
      },
      "answer_length": {
        "type": "integer"
      }
    }
  }
}'
```

**Index Sample Responses:**

```bash
curl -X POST "localhost:9200/survey_responses/_doc/1" -H 'Content-Type: application/json' -d'
{
  "question": "Why do you like our product?",
  "answer": "Fast and reliable",
  "answer_length": 3
}'

curl -X POST "localhost:9200/survey_responses/_doc/2" -H 'Content-Type: application/json' -d'
{
  "question": "Why do you like our product?",
  "answer": "Great customer service and easy to use interface",
  "answer_length": 8
}'

curl -X POST "localhost:9200/survey_responses/_doc/3" -H 'Content-Type: application/json' -d'
{
  "question": "Why do you like our product?",
  "answer": "Simple and effective",
  "answer_length": 3
}'
```

**Find Responses with Similar Length to New Input:**

When a new response comes in, find all similar-length responses (useful for grouping, analysis, or detecting spam patterns):

```bash
curl -X GET "localhost:9200/survey_responses/_search?pretty" -H 'Content-Type: application/json' -d'
{
  "query": {
    "token_count": {
      "field": "answer_length",
      "text": "good and cheap",
      "operator": "eq"
    }
  }
}'
```

Returns documents 1 and 3 (both have 3-word answers).

**Additional Use Cases for Integer Fields:**

1. **Content Length Policies**: Find all blog posts/comments that match length requirements
   ```json
   {
     "token_count": {
       "field": "word_count",
       "text": "user submitted comment text here...",
       "operator": "lte"
     }
   }
   ```

2. **Template Matching**: Match incoming data against stored templates by structure
   ```json
   {
     "token_count": {
       "field": "template_field_count",
       "text": "field1 field2 field3",
       "operator": "eq"
     }
   }
   ```

3. **Spam Detection**: Find messages with similar length patterns to known spam
   ```json
   {
     "token_count": {
       "field": "message_tokens",
       "text": "suspicious message content",
       "operator": "eq"
     }
   }
   ```

4. **Data Quality Checks**: Verify imported data matches expected field lengths
   ```json
   {
     "token_count": {
       "field": "expected_tokens",
       "text": "actual data value",
       "operator": "eq"
     }
   }
   ```

## Installation

### Building from Source

```bash
./gradlew clean build
```

The plugin zip will be created at: `build/distributions/opensearch-token-count-query-0.1.0.zip`

### Installing the Plugin

```bash
bin/opensearch-plugin install \
  file:///path/to/opensearch-token-count-query-0.1.0.zip
```

Then restart your OpenSearch cluster.

### Verify Installation

```bash
curl -X GET "localhost:9200/_cat/plugins?v"
```

You should see `opensearch-token-count-query` with version `0.1.0` in the output.

## Version

Current version: `0.1.0` (initial development)

Compatible with: OpenSearch 3.2.0

## Development

### Running Tests

Run all tests (unit, integration, and YAML REST tests):

```bash
./gradlew check
```

Run only unit tests:

```bash
./gradlew test
```

Run only integration tests:

```bash
./gradlew integTest
```

### Local Development with Docker

A `docker-compose.yml` and `Dockerfile` are provided for local testing. The Dockerfile builds an OpenSearch image with the plugin pre-installed.

```bash
# Build the plugin
./gradlew clean build

# Start OpenSearch (builds image and installs plugin automatically)
docker-compose up -d

# Verify plugin is installed
curl -X GET "localhost:9200/_cat/plugins?v"

# Access OpenSearch Dashboards
open http://localhost:5601
```

## Implementation Details

### Token Counting Algorithm

The plugin uses position-based token counting, matching the behavior of OpenSearch's built-in `TokenCountFieldMapper`. This means it uses `PositionIncrementAttribute` from Lucene's `TokenStream` API, which properly handles:

- Synonyms
- Stop words
- Position increments
- Multi-term tokens

### Query Translation

The plugin translates token count queries into Lucene `IntPoint` range queries:

- `eq`: `IntPoint.newExactQuery()`
- `gt`: `IntPoint.newRangeQuery(count + 1, MAX_VALUE)`
- `lt`: `IntPoint.newRangeQuery(MIN_VALUE, count - 1)`
- `gte`: `IntPoint.newRangeQuery(count, MAX_VALUE)`
- `lte`: `IntPoint.newRangeQuery(MIN_VALUE, count)`

## License

This code is licensed under the Apache 2.0 License. See [LICENSE.txt](LICENSE.txt).

## Copyright

Copyright OpenSearch Contributors. See [NOTICE](NOTICE.txt) for details.
