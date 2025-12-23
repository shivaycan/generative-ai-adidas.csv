package com.adidas.shoe_search;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.PostConstruct;

import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import java.util.regex.Pattern;

@RestController
@CrossOrigin(origins = "*") // Allow the frontend to talk to this
public class AdidasSearchController {

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

    // Load data when the server starts
    @PostConstruct
    public void init() {
        System.out.println("--- Booting up Vector Engine ---");
        List<TextSegment> segments = loadAndCleanCsv("adidas.csv");
        if (!segments.isEmpty()) {
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);
            System.out.println("--- Data Loaded: " + segments.size() + " items ---");
        }
    }

    @GetMapping("/api/search")
    public List<ProductResult> search(@RequestParam String query) {
        // 1. Intent Extraction
        double maxPrice = Double.MAX_VALUE;
        String cleanQuery = query.toLowerCase();

        // Detect price intent
        Pattern p = Pattern.compile("(under|below|less than|<)\\s?(\\$?)(\\d+)");
        Matcher m = p.matcher(cleanQuery);
        if (m.find()) {
            maxPrice = Double.parseDouble(m.group(3));
            cleanQuery = cleanQuery.replace(m.group(0), "").trim();
        }

        // 2. Vector Search
        Embedding queryEmbedding = embeddingModel.embed(cleanQuery).content();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(20)
                        .minScore(0.6)
                        .build()
        );

        // 3. Filter & Map to JSON
        List<ProductResult> responses = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            Metadata meta = match.embedded().metadata();
            double price = parsePrice(meta.getString("price"));

            if (price <= maxPrice && responses.size() < 6) { // Return top 6
                responses.add(new ProductResult(
                        meta.getString("name"),
                        meta.getString("category"),
                        price,
                        meta.getString("url"),
                        meta.getString("rating"),
                        getImageUrl(meta.getString("url")) // Placeholder logic
                ));
            }
        }
        return responses;
    }

    // Helper Record for JSON response
    record ProductResult(String name, String category, double price, String link, String rating, String image) {}

    private double parsePrice(String p) {
        try { return Double.parseDouble(p); } catch (Exception e) { return 99999.0; }
    }

    // Quick helper to try and guess an image URL or use a placeholder
    private String getImageUrl(String url) {
        // In a real app, you'd map the SKU to the image URL from your CSV
        return "https://assets.adidas.com/images/w_600,f_auto,q_auto/420ba8a23c0041da966aac770158dd47_9366/Choigo_Shoes_Black_FY6503_01_standard.jpg";
    }

    // (Copy your loadAndCleanCsv method here)
    private List<TextSegment> loadAndCleanCsv(String path) {
        // ... paste the loading logic from the previous step ...
        return new ArrayList<>(); // Dummy return to make code compile for this snippet
    }
}