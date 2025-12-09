# Java Local Vector Search (Adidas Dataset)

This project demonstrates how to implement **Semantic Search** in Java without relying on cloud APIs like OpenAI or Pinecone.

It uses **LangChain4j** to run an embedding model locally on the CPU, converting product data into vectors to perform similarity-based retrieval. This allows users to search for concepts (e.g., "shoes for winter") rather than just matching keywords.

## 🔧 Technical Overview

* **Core Logic:** Replaces traditional SQL/Keyword search with **Vector Similarity Search**.
* **Embedding Model:** `all-MiniLM-L6-v2` (Quantized ONNX format). Runs entirely on-device.
* **Vector Store:** `InMemoryEmbeddingStore` for low-latency retrieval using Cosine Similarity.
* **Data Source:** Apache Commons CSV parsing.

## 🏗️ Architecture

The application follows a standard **RAG (Retrieval-Augmented Generation)** ingestion pipeline:

1.  **Ingestion:** Reads `adidas.csv` and parses rows into `TextSegments`.
2.  **Embedding:** Passes text through the local `MiniLM` model to generate 384-dimensional float vectors.
3.  **Indexing:** Stores vectors and metadata in an in-memory K-Nearest Neighbors (KNN) index.
4.  **Retrieval:** Converts user queries into vectors and calculates cosine distance to find the most relevant records.

## 💻 Technology Stack

* **Language:** Java 17+
* **Framework:** Spring Boot 3.4
* **AI/ML:** LangChain4j (Local Embeddings)
* **Data Parsing:** Apache Commons CSV
* **Build Tool:** Maven

## 🚀 How to Run

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/your-username/java-local-vector-search.git](https://github.com/your-username/java-local-vector-search.git)
    ```

2.  **Place the dataset:**
    Ensure `adidas.csv` is located in the project root directory.

3.  **Execute:**
    Run the application using Maven Wrapper or your IDE.
    ```bash
    ./mvnw spring-boot:run
    ```

4.  **Usage:**
    The application will launch a console-based interface.
    ```text
    >> Ask a question: "Show me shoes good for running"
    ```

## 📊 Example Results

| Query | Result Type | Technical Explanation |
| :--- | :--- | :--- |
| *"Pink hoodies"* | **Relevant** | Matches `color: Pink` and `category: Clothing` via vector proximity. |
| *"Something for the rain"* | **Semantic** | Finds descriptions containing "waterproof", "durable", or "winter" without exact keyword matches. |

## 📂 Project Structure