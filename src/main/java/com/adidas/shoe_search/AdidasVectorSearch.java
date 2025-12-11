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

	public static void main(String[] args) {
		String csvPath = "adidas.csv";

		System.out.println("--- 1. Initializing Local AI Model ---");
		EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
		InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

		System.out.println("--- 2. Smart-Loading CSV Data ---");
		List<TextSegment> segments = loadCsvAsSmartSegments(csvPath);

		if (segments.isEmpty()) {
			System.err.println("CRITICAL ERROR: Could not find 'adidas.csv'.");
			return;
		}

		System.out.println("--- 3. Embedding " + segments.size() + " items... ---");
		List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
		embeddingStore.addAll(embeddings, segments);

		// --- INTERACTIVE CHAT LOOP ---
		java.util.Scanner scanner = new java.util.Scanner(System.in);


		while (true) {
			System.out.print("\n>> Search: ");
			String queryText = scanner.nextLine();

			if (queryText.equalsIgnoreCase("exit")) {
				System.out.println("Bye! 👋");
				break;
			}

			// --- STEP 1: DETECT PRICE FILTER (The "Math" Part) ---
			double maxPriceFilter = Double.MAX_VALUE;

			// Regex to find "under 40", "below 100", "< 50"
			Pattern pricePattern = Pattern.compile("(under|below|less than|<)\\s?(\\d+)");
			Matcher matcher = pricePattern.matcher(queryText.toLowerCase());

			if (matcher.find()) {
				maxPriceFilter = Double.parseDouble(matcher.group(2));
				System.out.println("   (Filtering for items cheaper than $" + maxPriceFilter + ")");
			}

			// --- STEP 2: VECTOR SEARCH (The "Vibe" Part) ---
			Embedding queryEmbedding = embeddingModel.embed(queryText).content();

			EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
					EmbeddingSearchRequest.builder()
							.queryEmbedding(queryEmbedding)
							.maxResults(15) // Fetch MORE results so we have room to filter
							.minScore(0.5)
							.build()
			);

			// --- STEP 3: APPLY FILTER & PRINT ---
			System.out.println("\n=== Results ===\n");
			int count = 0;

			for (EmbeddingMatch<TextSegment> match : result.matches()) {
				// Check Price
				double itemPrice = parsePrice(match.embedded().metadata().getString("price"));

				if (itemPrice <= maxPriceFilter) {
					printSmartProductDetails(match);
					count++;
				}

				if (count >= 3) break; // Stop after finding 3 valid matches
			}

			if (count == 0) {
				System.out.println("❌ No matches found within that price range.");
			}
		}
	}

	// --- HELPER: Turn "$40" string into 40.0 number ---
	private static double parsePrice(String priceStr) {
		if (priceStr == null || priceStr.isEmpty() || priceStr.equals("null")) return 99999.0;
		try {
			return Double.parseDouble(priceStr);
		} catch (NumberFormatException e) {
			return 99999.0; // If price is weird, put it at end of list
		}
	}

	// --- SAME LOADING LOGIC AS BEFORE ---
	private static List<TextSegment> loadCsvAsSmartSegments(String csvPath) {
		List<TextSegment> segments = new ArrayList<>();
		try (Reader reader = new FileReader(csvPath)) {
			CSVFormat format = CSVFormat.DEFAULT.builder()
					.setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build();

			try (CSVParser csvParser = new CSVParser(reader, format)) {
				for (CSVRecord row : csvParser) {
					String semanticText = String.format(
							"Product: %s. Category: %s. Color: %s. Description: %s",
							row.get("name"), row.get("category"), row.get("color"), row.get("description")
					);
					Metadata metadata = new Metadata();
					metadata.add("name", row.get("name"));
					metadata.add("price", row.get("selling_price"));
					metadata.add("currency", row.get("currency"));
					metadata.add("rating", row.get("average_rating"));
					metadata.add("url", row.get("url"));
					segments.add(TextSegment.from(semanticText, metadata));
				}
			}
		} catch (IOException e) { System.err.println("Error: " + e.getMessage()); }
		return segments;
	}

	private static void printSmartProductDetails(EmbeddingMatch<TextSegment> match) {
		Metadata m = match.embedded().metadata();
		String price = m.getString("price");
		if (price == null || price.equals("null") || price.isEmpty()) price = "N/A";

		System.out.println("");
		System.out.println(m.getString("name"));
		System.out.println(price + " " + m.getString("currency") + "  •  " + m.getString("rating") + " Stars");
		System.out.println("See details: " + m.getString("url"));
	}
}