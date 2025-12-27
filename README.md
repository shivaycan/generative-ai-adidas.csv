# Java Local Vector Search (Adidas Dataset)

This project demonstrates how to implement **Semantic Search** in Java without relying on cloud APIs such as OpenAI, Pinecone, or hosted vector databases.

It uses **LangChain4j** to run a **local embedding model on CPU**, converting Adidas product data into vectors and enabling **similarity-based retrieval**.  
Users can search by *meaning* (e.g., "running shoes under 100") rather than exact keyword matches.

The system supports **intent extraction**, **structured filtering**, and **persistent vector storage** for fast startup after the initial indexing.

---

## 🔧 Technical Overview

- **Core Logic:** Replaces keyword / SQL search with **Vector Similarity Search**
- **Embedding Model:** `all-MiniLM-L6-v2` (local, CPU-based)
- **Vector Store:** `InMemoryEmbeddingStore` with JSON serialization
- **Similarity Metric:** Cosine similarity
- **Persistence:** Vector index is saved to disk and reused across runs
- **Data Source:** Apache Commons CSV
- **Interface:** Interactive CLI (console-based)

---

## 🏗️ Architecture

The application follows a **RAG-style (Retrieval-Augmented Retrieval)** pipeline:

1. **Ingestion**
    - Reads `adidas.csv`
    - Cleans HTML from descriptions
    - Builds semantic text per product
    - Attaches structured metadata

2. **Embedding**
    - Uses local MiniLM model
    - Generates 384-dimensional float vectors
    - Runs entirely on-device (no network calls)

3. **Indexing**
    - Stores embeddings and metadata in an in-memory KNN index
    - Serializes index to `adidas_vector_store.json`

4. **Retrieval**
    - Converts user query to embedding
    - Performs cosine similarity search
    - Applies relevance threshold
    - Applies structured post-filters (price, gender)

---

## 🧠 Query Processing Pipeline

User Query
↓
Intent Extraction (price, gender, semantic text)
↓
Query Embedding
↓
Vector Similarity Search (Top K)
↓
Relevance Threshold Filtering
↓
Price & Gender Post-Filtering
↓
Top Matching Products


---

## 🧩 Intent Extraction Features

### Price Detection
Recognizes patterns such as:
- `under 100`
- `below $80`
- `less than 120`
- `< 90`

Extracted price is applied as a **hard upper bound filter**.

---

### Gender Detection
Detects intent from keywords:
- **Men:** `men`, `man`
- **Women:** `women`, `ladies`

Filtering is applied **after vector retrieval** to preserve semantic recall.

---

### Semantic Query Cleanup
- Removes detected filters from query
- Normalizes whitespace
- Defaults to `"shoes"` if query becomes empty

---

## 💾 Vector Store Persistence

- On first run:
    - CSV is ingested
    - Embeddings are generated
    - Vector store is saved to disk
- On subsequent runs:
    - Vector store is loaded instantly
    - CSV ingestion is skipped entirely

### Manual Reindexing
The CLI supports a reindex command:



reindex


This deletes the stored index and forces fresh ingestion on the next run.

---

## ⚙️ Search Configuration

```java
MIN_RELEVANCE_SCORE = 0.60
MAX_RESULTS_TO_FETCH = 25


Retrieves up to 25 candidate vectors

Applies similarity threshold

Displays top 3 valid matches after filtering

💻 Technology Stack

Language: Java 17+

AI / ML: LangChain4j (Local Embeddings)

Vector Store: InMemoryEmbeddingStore

Data Parsing: Apache Commons CSV

Build Tool: Maven

Runtime: Local JVM (CPU only)

Note: This project is CLI-based and does not require Spring Boot or a web server.

🚀 How to Run
1. Clone the Repository
git clone https://github.com/your-username/java-local-vector-search.git

2. Place the Dataset

Ensure adidas.csv is present in the project root directory.

3. Build and Run
mvn clean compile
mvn exec:java -Dexec.mainClass="com.adidas.shoe_search.AdidasVectorSearch"

🖥️ CLI Usage

After startup, the application enters an interactive search loop.

Example Queries
Men's running shoes under 100
White sneakers for women
Casual shoes below 80

Supported Commands
Command	Description
exit	Terminates the application
reindex	Deletes vector store and forces re-ingestion
📊 Example Results
Query	Result Type	Technical Explanation
"Pink hoodies"	Relevant	Matches semantic similarity with color and category metadata
"Something for the rain"	Semantic	Retrieves items with concepts like waterproof or winter without exact keywords
"Men's shoes under 90"	Filtered Semantic	Combines vector similarity with price and gender constraints
🧠 Design Decisions

Fully local execution (privacy-safe, no API costs)

Persistent vector storage for fast cold starts

Intent extraction separated from semantic search

Post-filtering avoids embedding bias

Simple, inspectable CLI for debugging and learning

⚠️ Limitations

Single CSV data source

CLI-only interface

In-memory vector store (non-distributed)

No hybrid lexical + semantic ranking

🔮 Possible Extensions

Spring Boot REST API

Web UI (React / Next.js)

Hybrid BM25 + vector search

FAISS or Redis vector backend

Multi-brand ingestion

Recommendation and personalization layer

👤 Author

Shivay Garg
Computer Science (AI / ML)


---

If you want next, I can:
- Convert this into a **Spring Boot API**
- Refactor into **MCP client/server**
- Add **FAISS / Redis**
- Prepare an **interview explanation** for vector search & RAG

Just say the word.