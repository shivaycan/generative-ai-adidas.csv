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
// [NEW] Added imports for file handling
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdidasVectorSearch {

	// Tuning Parameters
	private static final double MIN_RELEVANCE_SCORE = 0.60;
	private static final int MAX_RESULTS_TO_FETCH = 25;

	private static final String CSV_PATH = "adidas.csv";

	// [NEW] Added a constant for the file where embeddings will be saved
	private static final String STORE_FILE = "adidas_vector_store.json";

	public static void main(String[] args) {
		System.out.println("--- 1. Initializing AI Model ---");
		EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
		InMemoryEmbeddingStore<TextSegment> embeddingStore;

		// [NEW] Create a Path object for our storage file
		Path storePath = Paths.get(STORE_FILE);

		// [CHANGED] Replaced unconditional CSV loading with this Logic Block
		// Check if the file already exists on the disk
		if (Files.exists(storePath)) {
			System.out.println("💾 Found existing vector store. Loading from disk...");

			// [NEW] Load the store directly from the JSON file (Instant)
			embeddingStore = InMemoryEmbeddingStore.fromFile(storePath);

			System.out.println("✅ Database loaded! Skipping CSV ingestion.");
		} else {
			// [NEW] If file doesn't exist, we must process the CSV (Slow path)
			System.out.println("⚠️ No vector store found. Starting fresh ingestion...");
			embeddingStore = new InMemoryEmbeddingStore<>();

			System.out.println("--- 2. Ingesting & Cleaning Data ---");
			List<TextSegment> segments = loadAndCleanCsv(CSV_PATH);

			if (segments.isEmpty()) {
				System.err.println("CRITICAL: No data loaded. Check 'adidas.csv' path.");
				return;
			}

			System.out.println("--- 3. Embedding " + segments.size() + " products (This happens once) ---");
			List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
			embeddingStore.addAll(embeddings, segments);

			// [NEW] Save the calculated embeddings to disk for next time
			System.out.println("💾 Saving vector store to: " + STORE_FILE);
			embeddingStore.serializeToFile(storePath);
		}
		// [END OF CHANGED BLOCK]

		// --- CHAT LOOP ---
		Scanner scanner = new Scanner(System.in);
		System.out.println("\n✅ System Ready. Try: 'Men's running shoes under $100'");

		while (true) {
			System.out.print("\n>> Search: ");
			String rawQuery = scanner.nextLine();

			if (rawQuery.equalsIgnoreCase("exit")) break;

			// [NEW] Added a command to delete the index file manually
			if (rawQuery.equalsIgnoreCase("reindex")) {
				try {
					Files.deleteIfExists(storePath);
					System.out.println("🗑️ Index deleted. Restart application to re-ingest data from CSV.");
				} catch (IOException e) {
					System.out.println("❌ Error deleting index: " + e.getMessage());
				}
				break;
			}

			SearchIntent intent = extractIntent(rawQuery);

			if (intent.hasFilters()) {
				System.out.printf("   [Filters Active] Price: <$%.0f | Gender: %s%n",
						intent.maxPrice, intent.genderFilter);
			}

			Embedding queryEmbedding = embeddingModel.embed(intent.semanticQuery).content();

			EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
					EmbeddingSearchRequest.builder()
							.queryEmbedding(queryEmbedding)
							.maxResults(MAX_RESULTS_TO_FETCH)
							.minScore(MIN_RELEVANCE_SCORE)
							.build()
			);

			// [UNCHANGED] The rest of the loop remains the same
			int count = 0;
			System.out.println("\n=== Top Matches for: \"" + intent.semanticQuery + "\" ===\n");

			for (EmbeddingMatch<TextSegment> match : result.matches()) {
				Metadata m = match.embedded().metadata();
				double price = parseDoubleSafe(m.getString("price"));
				if (price > intent.maxPrice) continue;

				String productGender = m.getString("category").toLowerCase();
				String productName = m.getString("name").toLowerCase();

				if (!intent.genderFilter.equals("ALL")) {
					if (intent.genderFilter.equals("MEN") && (productGender.contains("women") || productName.contains("women"))) continue;
					if (intent.genderFilter.equals("WOMEN") && (productGender.contains("men") && !productGender.contains("women"))) continue;
				}

				printProductCard(match, price);
				count++;
				if (count >= 3) break;
			}

			if (count == 0) System.out.println("❌ No exact matches found.");
		}
	}

	// [UNCHANGED] Method to load CSV
	private static List<TextSegment> loadAndCleanCsv(String csvPath) {
		List<TextSegment> segments = new ArrayList<>();
		try (Reader reader = new FileReader(csvPath)) {
			CSVFormat format = CSVFormat.DEFAULT.builder()
					.setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build();

			try (CSVParser csvParser = new CSVParser(reader, format)) {
				for (CSVRecord row : csvParser) {
					String cleanDesc = row.get("description").replaceAll("<[^>]*>", " ");
					String semanticText = String.format("%s %s. Color: %s. Details: %s",
							row.get("name"), row.get("category"), row.get("color"), cleanDesc);

					Metadata metadata = new Metadata();
					metadata.add("name", row.get("name"));
					metadata.add("category", row.get("category"));
					metadata.add("price", row.get("selling_price"));
					metadata.add("rating", row.get("average_rating"));
					metadata.add("url", row.get("url"));

					segments.add(TextSegment.from(semanticText, metadata));
				}
			}
		} catch (IOException e) { System.err.println("Error loading CSV: " + e.getMessage()); }
		return segments;
	}

	// [UNCHANGED] Intent extraction logic
	private static SearchIntent extractIntent(String query) {
		String lowerQuery = query.toLowerCase();
		double maxPrice = Double.MAX_VALUE;
		String gender = "ALL";

		Pattern pricePattern = Pattern.compile("(under|below|less than|<)\\s?(\\$?)(\\d+)");
		Matcher priceMatcher = pricePattern.matcher(lowerQuery);
		if (priceMatcher.find()) {
			maxPrice = Double.parseDouble(priceMatcher.group(3));
			lowerQuery = lowerQuery.replace(priceMatcher.group(0), "");
		}

		if (lowerQuery.contains("women") || lowerQuery.contains("ladies")) {
			gender = "WOMEN";
			lowerQuery = lowerQuery.replace("women", "").replace("ladies", "");
		} else if (lowerQuery.contains("men") || lowerQuery.contains(" man ")) {
			gender = "MEN";
			lowerQuery = lowerQuery.replace("men", "").replace("man", "");
		}

		String cleanQuery = lowerQuery.replaceAll("\\s+", " ").trim();
		if (cleanQuery.isEmpty()) cleanQuery = "shoes";
		return new SearchIntent(cleanQuery, maxPrice, gender);
	}

	static class SearchIntent {
		String semanticQuery;
		double maxPrice;
		String genderFilter;
		public SearchIntent(String q, double p, String g) {
			this.semanticQuery = q; this.maxPrice = p; this.genderFilter = g;
		}
		boolean hasFilters() { return maxPrice != Double.MAX_VALUE || !genderFilter.equals("ALL"); }
	}

	private static void printProductCard(EmbeddingMatch<TextSegment> match, double price) {
		Metadata m = match.embedded().metadata();
		System.out.printf("👟 %s%n", m.getString("name"));
		System.out.printf("   Score: %.2f | Price: $%.2f%n", match.score(), price);
		System.out.println("   Link: " + m.getString("url"));
		System.out.println("   -------------------------------------");
	}

	private static double parseDoubleSafe(String str) {
		if (str == null || str.isEmpty() || str.equals("null")) return 99999.0;
		try { return Double.parseDouble(str); } catch (NumberFormatException e) { return 99999.0; }
	}
}