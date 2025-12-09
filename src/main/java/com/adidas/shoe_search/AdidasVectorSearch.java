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

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdidasVectorSearch {

	public static void main(String[] args) {
		String csvPath = "adidas.csv";

		System.out.println("--- 1. Initializing Local AI Model ---");
		EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

		InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

		System.out.println("--- 2. Loading CSV Data ---");
		List<TextSegment> segments = loadCsvAsSegments(csvPath);

		if (segments.isEmpty()) {
			System.err.println("CRITICAL ERROR: Could not find 'adidas.csv'.");
			return;
		}

		System.out.println("--- 3. Embedding " + segments.size() + " items... ---");
		List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
		embeddingStore.addAll(embeddings, segments);

		// --- NEW: INTERACTIVE CHAT LOOP ---
		java.util.Scanner scanner = new java.util.Scanner(System.in);
		System.out.println("\n👟 VIBE SEARCH READY! (Type 'exit' to quit)");

		while (true) {
			System.out.print("\n>> Ask a question: ");
			String queryText = scanner.nextLine();

			if (queryText.equalsIgnoreCase("exit")) {
				System.out.println("Bye! 👋");
				break;
			}

			// Embed the User's Question
			Embedding queryEmbedding = embeddingModel.embed(queryText).content();

			// Search
			EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
					EmbeddingSearchRequest.builder()
							.queryEmbedding(queryEmbedding)
							.maxResults(3) // Top 3 results
							.build()
			);

			System.out.println("\n=== Results for: \"" + queryText + "\" ===\n");
			for (EmbeddingMatch<TextSegment> match : result.matches()) {
				printProductDetails(match.embedded().text());
			}
		}
	}
	// --- CSV PARSING MAGIC ---
	// --- CSV PARSING MAGIC (Updated for 2025) ---
	private static List<TextSegment> loadCsvAsSegments(String csvPath) {
		List<TextSegment> segments = new ArrayList<>();

		try (Reader reader = new FileReader(csvPath)) {
			// 1. Configure the CSV Format using the new Builder
			CSVFormat format = CSVFormat.DEFAULT.builder()
					.setHeader()              // Automatically detect header
					.setSkipHeaderRecord(true) // Don't treat the first row as data
					.setIgnoreHeaderCase(true)
					.setTrim(true)
					.build();

			// 2. Parse
			try (CSVParser csvParser = new CSVParser(reader, format)) {
				for (CSVRecord row : csvParser) {
					// Formatting: "key: value | key: value"
					StringBuilder textBuilder = new StringBuilder();
					Map<String, String> rowMap = row.toMap();
					List<String> formattedParts = new ArrayList<>();

					for (Map.Entry<String, String> entry : rowMap.entrySet()) {
						formattedParts.add(entry.getKey() + ": " + entry.getValue());
					}
					String text = String.join(" | ", formattedParts);

					Metadata metadata = new Metadata();
					metadata.add("row_index", String.valueOf(row.getRecordNumber()));
					segments.add(TextSegment.from(text, metadata));
				}
			}

		} catch (IOException e) {
			System.err.println("Error reading CSV: " + e.getMessage());
		}
		return segments;
	}
	// --- prints---
	private static void printProductDetails(String pageContent) {
		String[] dataItems = pageContent.split(" \\| ");
		Map<String, String> keyValues = new HashMap<>();
		for (String item : dataItems) {
			if (item.contains(": ")) {
				String[] parts = item.split(": ", 2);
				keyValues.put(parts[0], parts[1]);
			}
		}
		System.out.println("Shoes " + keyValues.getOrDefault("name", "Unknown Shoe"));
		System.out.println("Money " + keyValues.getOrDefault("selling_price", "?") + " " + keyValues.getOrDefault("currency", ""));
		System.out.println("star " + keyValues.getOrDefault("average_rating", "N/A") + " stars");
		System.out.println("LINK " + keyValues.getOrDefault("url", "").substring(0, Math.min(keyValues.getOrDefault("url", "").length(), 40)) + "...");
		System.out.println("-".repeat(50));
	}
}