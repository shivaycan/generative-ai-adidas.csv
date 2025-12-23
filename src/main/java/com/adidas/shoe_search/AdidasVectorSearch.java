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
import java.util.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdidasVectorSearch {

	// Tuning Parameters
	private static final double MIN_RELEVANCE_SCORE = 0.60; // Increased threshold for higher quality
	private static final int MAX_RESULTS_TO_FETCH = 25;     // Fetch more initially for better filtering capacity

	public static void main(String[] args) {
		String csvPath = "adidas.csv";

		System.out.println("--- 1. Initializing AI Model (Fine-tuned for retrieval) ---");
		EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
		InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

		System.out.println("--- 2. Ingesting & Cleaning Data ---");
		List<TextSegment> segments = loadAndCleanCsv(csvPath);

		if (segments.isEmpty()) {
			System.err.println("CRITICAL: No data loaded. Check 'adidas.csv' path.");
			return;
		}

		System.out.println("--- 3. Embedding " + segments.size() + " products... ---");
		List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
		embeddingStore.addAll(embeddings, segments);

		// --- CHAT LOOP ---
		java.util.Scanner scanner = new java.util.Scanner(System.in);
		System.out.println("\n✅ System Ready. Try: 'Men's running shoes under $100' or 'Blue soccer cleats for kids'");

		while (true) {
			System.out.print("\n>> Search: ");
			String rawQuery = scanner.nextLine();

			if (rawQuery.equalsIgnoreCase("exit")) break;

			// --- STEP 1: INTENT EXTRACTION (The Logic Layer) ---
			// We separate "What they want" (Vector) from "Constraints" (Filters)
			SearchIntent intent = extractIntent(rawQuery);

			if (intent.hasFilters()) {
				System.out.printf("   [Filters Active] Price: <$%.0f | Gender: %s%n",
						intent.maxPrice, intent.genderFilter);
			}

			// --- STEP 2: SEMANTIC SEARCH (The AI Layer) ---
			// We search using the 'cleaned' query (removing price/gender words) to focus AI on the product type
			Embedding queryEmbedding = embeddingModel.embed(intent.semanticQuery).content();

			EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
					EmbeddingSearchRequest.builder()
							.queryEmbedding(queryEmbedding)
							.maxResults(MAX_RESULTS_TO_FETCH)
							.minScore(MIN_RELEVANCE_SCORE)
							.build()
			);

			// --- STEP 3: HYBRID FILTERING ---
			int count = 0;
			System.out.println("\n=== Top Matches for: \"" + intent.semanticQuery + "\" ===\n");

			for (EmbeddingMatch<TextSegment> match : result.matches()) {
				Metadata m = match.embedded().metadata();

				// 1. Check Price Filter
				double price = parseDoubleSafe(m.getString("price"));
				if (price > intent.maxPrice) continue;

				// 2. Check Gender Filter (if user specified one)
				String productGender = m.getString("category").toLowerCase(); // e.g. "men's shoes"
				String productName = m.getString("name").toLowerCase();

				if (!intent.genderFilter.equals("ALL")) {
					// If user wants Men, reject Women's specific items (and vice versa)
					// We allow 'unisex' or items that don't specify.
					if (intent.genderFilter.equals("MEN") && (productGender.contains("women") || productName.contains("women"))) continue;
					if (intent.genderFilter.equals("WOMEN") && (productGender.contains("men") && !productGender.contains("women"))) continue;
				}

				printProductCard(match, price);
				count++;
				if (count >= 3) break; // Limit display to top 3
			}

			if (count == 0) {
				System.out.println("❌ No exact matches found. Try broadening your price range or query.");
			}
		}
	}

	// --- ENHANCED DATA LOADING ---
	private static List<TextSegment> loadAndCleanCsv(String csvPath) {
		List<TextSegment> segments = new ArrayList<>();
		try (Reader reader = new FileReader(csvPath)) {
			CSVFormat format = CSVFormat.DEFAULT.builder()
					.setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build();

			try (CSVParser csvParser = new CSVParser(reader, format)) {
				for (CSVRecord row : csvParser) {
					// CLEANING: Remove HTML tags if description has them
					String cleanDesc = row.get("description").replaceAll("<[^>]*>", " ");

					// ENRICHMENT: Structure text for better embedding weight.
					// Putting Name and Category first gives them higher priority in vector similarity.
					String semanticText = String.format(
							"%s %s. Color: %s. Details: %s",
							row.get("name"),       // Important
							row.get("category"),   // Important
							row.get("color"),
							cleanDesc
					);

					Metadata metadata = new Metadata();
					metadata.add("name", row.get("name"));
					metadata.add("category", row.get("category")); // Store category for filtering
					metadata.add("price", row.get("selling_price"));
					metadata.add("currency", row.get("currency"));
					metadata.add("rating", row.get("average_rating"));
					metadata.add("url", row.get("url"));

					segments.add(TextSegment.from(semanticText, metadata));
				}
			}
		} catch (IOException e) { System.err.println("Error loading CSV: " + e.getMessage()); }
		return segments;
	}

	// --- INTENT PARSING LOGIC ---
	private static SearchIntent extractIntent(String query) {
		String lowerQuery = query.toLowerCase();
		double maxPrice = Double.MAX_VALUE;
		String gender = "ALL";

		// 1. Extract Price Limit
		// Matches: "under 50", "below 100", "< 90"
		Pattern pricePattern = Pattern.compile("(under|below|less than|<)\\s?(\\$?)(\\d+)");
		Matcher priceMatcher = pricePattern.matcher(lowerQuery);
		if (priceMatcher.find()) {
			maxPrice = Double.parseDouble(priceMatcher.group(3));
			// Remove the price phrase from query so it doesn't confuse the vector search
			lowerQuery = lowerQuery.replace(priceMatcher.group(0), "");
		}

		// 2. Extract Gender
		if (lowerQuery.contains("women") || lowerQuery.contains("ladies")) {
			gender = "WOMEN";
			lowerQuery = lowerQuery.replace("women", "").replace("ladies", "");
		} else if (lowerQuery.contains("men") || lowerQuery.contains(" man ")) {
			gender = "MEN";
			lowerQuery = lowerQuery.replace("men", "").replace("man", "");
		} else if (lowerQuery.contains("kid") || lowerQuery.contains("boy") || lowerQuery.contains("girl")) {
			gender = "KIDS"; // You could implement specific logic for kids
		}

		// Clean up double spaces created by removals
		String cleanQuery = lowerQuery.replaceAll("\\s+", " ").trim();
		if (cleanQuery.isEmpty()) cleanQuery = "shoes"; // Fallback

		return new SearchIntent(cleanQuery, maxPrice, gender);
	}

	// --- HELPER CLASSES & METHODS ---

	// Simple container for our parsed query
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
		System.out.printf("   Score: %.2f | Price: $%.2f | Rating: %s⭐%n",
				match.score(), price, m.getString("rating"));
		System.out.println("   Link: " + m.getString("url"));
		System.out.println("   -------------------------------------");
	}

	private static double parseDoubleSafe(String str) {
		if (str == null || str.isEmpty() || str.equals("null")) return 99999.0;
		try { return Double.parseDouble(str); } catch (NumberFormatException e) { return 99999.0; }
	}
}