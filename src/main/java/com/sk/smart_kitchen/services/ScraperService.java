package com.sk.smart_kitchen.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.smart_kitchen.dto.IngredientLineForm;
import com.sk.smart_kitchen.dto.ScrapedRecipeData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ScraperService {

    private static final Pattern SERVINGS_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern ISO_MINUTES_PATTERN = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern FRACTION_PATTERN = Pattern.compile("^(\\d+)\\/(\\d+)$");
    private static final Set<String> COMMON_UNITS = Set.of(
            "g", "kg", "mg", "ml", "l", "oz", "lb", "tbsp", "tsp", "cup", "cups", "teaspoon", "teaspoons",
            "tablespoon", "tablespoons", "pinch", "clove", "cloves", "slice", "slices", "count", "bunch", "head"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScrapedRecipeData scrapeRecipeFromUrl(String rawUrl) {
        String url = validateAndNormalizeUrl(rawUrl);
        Document doc;

        try {
            doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36")
                    .timeout((int) Duration.ofSeconds(12).toMillis())
                    .followRedirects(true)
                    .get();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not fetch recipe URL. Please check the link and try again.", ex);
        }

        ScrapedRecipeData data = parseJsonLdRecipe(doc);
        if (data == null) {
            data = new ScrapedRecipeData();
        }

        applyFallbacks(doc, data);
        normalizeForForm(data);

        if (isBlank(data.getTitle()) || data.getInstructionSteps().isEmpty() || data.getIngredients().isEmpty()) {
            throw new IllegalArgumentException("This URL does not look like a supported recipe page yet. Try another recipe URL.");
        }

        return data;
    }

    private String validateAndNormalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Recipe URL is required.");
        }

        String cleaned = rawUrl.trim();
        URI uri;
        try {
            uri = new URI(cleaned);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Recipe URL format is invalid.", ex);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Only http/https recipe URLs are supported.");
        }

        return cleaned;
    }

    private ScrapedRecipeData parseJsonLdRecipe(Document doc) {
        Elements scripts = doc.select("script[type=application/ld+json]");
        for (Element script : scripts) {
            String json = script.data();
            if (isBlank(json)) {
                continue;
            }

            try {
                JsonNode root = objectMapper.readTree(json);
                JsonNode recipeNode = findRecipeNode(root);
                if (recipeNode != null) {
                    return mapRecipeNode(recipeNode);
                }
            } catch (Exception ignored) {
                // Ignore malformed JSON-LD blocks and continue scanning.
            }
        }
        return null;
    }

    private JsonNode findRecipeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findRecipeNode(child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        if (node.isObject()) {
            JsonNode typeNode = node.get("@type");
            if (isRecipeType(typeNode)) {
                return node;
            }

            JsonNode graph = node.get("@graph");
            if (graph != null) {
                JsonNode found = findRecipeNode(graph);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private boolean isRecipeType(JsonNode typeNode) {
        if (typeNode == null || typeNode.isNull()) {
            return false;
        }
        if (typeNode.isTextual()) {
            return "recipe".equalsIgnoreCase(typeNode.asText());
        }
        if (typeNode.isArray()) {
            for (JsonNode type : typeNode) {
                if (type.isTextual() && "recipe".equalsIgnoreCase(type.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private ScrapedRecipeData mapRecipeNode(JsonNode recipeNode) {
        ScrapedRecipeData data = new ScrapedRecipeData();
        data.setTitle(text(recipeNode.get("name")));
        data.setDescription(text(recipeNode.get("description")));
        data.setImageUrl(extractImageUrl(recipeNode.get("image")));
        data.setPrepTimeMins(parseMinutes(text(recipeNode.get("totalTime")), text(recipeNode.get("prepTime"))));
        data.setDefaultServings(parseServings(text(recipeNode.get("recipeYield"))));
        data.setMealType(normalizeMealType(text(recipeNode.get("recipeCategory"))));
        data.setIngredients(parseIngredientLines(jsonArrayToStrings(recipeNode.get("recipeIngredient"))));
        data.setInstructionSteps(parseInstructions(recipeNode.get("recipeInstructions")));
        return data;
    }

    private void applyFallbacks(Document doc, ScrapedRecipeData data) {
        if (isBlank(data.getTitle())) {
            String ogTitle = doc.select("meta[property=og:title]").attr("content");
            data.setTitle(!isBlank(ogTitle) ? ogTitle : doc.title());
        }

        if (isBlank(data.getDescription())) {
            String ogDescription = doc.select("meta[property=og:description]").attr("content");
            if (isBlank(ogDescription)) {
                ogDescription = doc.select("meta[name=description]").attr("content");
            }
            data.setDescription(ogDescription);
        }

        if (isBlank(data.getImageUrl())) {
            data.setImageUrl(doc.select("meta[property=og:image]").attr("content"));
        }

        if (data.getIngredients() == null || data.getIngredients().isEmpty()) {
            List<String> fallbackLines = new ArrayList<>();
            for (Element li : doc.select("[class*=ingredient] li, li[class*=ingredient], .ingredients-item")) {
                String line = li.text();
                if (!isBlank(line)) {
                    fallbackLines.add(line);
                }
            }
            data.setIngredients(parseIngredientLines(fallbackLines));
        }

        if (data.getInstructionSteps() == null || data.getInstructionSteps().isEmpty()) {
            List<String> steps = new ArrayList<>();
            for (Element step : doc.select("[class*=instruction] li, li[class*=instruction], .instruction")) {
                String line = step.text();
                if (!isBlank(line)) {
                    steps.add(line);
                }
            }
            data.setInstructionSteps(steps);
        }
    }

    private void normalizeForForm(ScrapedRecipeData data) {
        data.setTitle(trimToNull(data.getTitle()));
        data.setDescription(trimToNull(data.getDescription()));
        data.setImageUrl(trimToNull(data.getImageUrl()));

        if (data.getPrepTimeMins() == null || data.getPrepTimeMins() < 1) {
            data.setPrepTimeMins(30);
        }
        if (data.getDefaultServings() == null || data.getDefaultServings() < 1) {
            data.setDefaultServings(2);
        }

        if (isBlank(data.getMealType())) {
            data.setMealType("Dinner");
        }

        List<IngredientLineForm> normalizedIngredients = new ArrayList<>();
        if (data.getIngredients() != null) {
            for (IngredientLineForm line : data.getIngredients()) {
                if (line == null || isBlank(line.getName())) {
                    continue;
                }
                IngredientLineForm cleaned = new IngredientLineForm();
                cleaned.setName(line.getName().trim());
                cleaned.setQuantity(isBlank(line.getQuantity()) ? "1" : line.getQuantity().trim());
                cleaned.setUnit(trimToNull(line.getUnit()));
                cleaned.setPreparation(trimToNull(line.getPreparation()));
                normalizedIngredients.add(cleaned);
            }
        }
        data.setIngredients(normalizedIngredients);

        List<String> steps = new ArrayList<>();
        if (data.getInstructionSteps() != null) {
            for (String step : data.getInstructionSteps()) {
                String cleaned = trimToNull(step);
                if (cleaned != null) {
                    steps.add(cleaned);
                }
            }
        }
        data.setInstructionSteps(steps);
    }

    private List<String> jsonArrayToStrings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || node.isNull()) {
            return values;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            }
        } else if (node.isTextual()) {
            values.add(node.asText());
        }
        return values;
    }

    private List<String> parseInstructions(JsonNode instructionNode) {
        List<String> steps = new ArrayList<>();
        if (instructionNode == null || instructionNode.isNull()) {
            return steps;
        }

        if (instructionNode.isTextual()) {
            String text = trimToNull(instructionNode.asText());
            if (text != null) {
                for (String split : text.split("\\r?\\n")) {
                    String line = trimToNull(split);
                    if (line != null) {
                        steps.add(line);
                    }
                }
            }
            return steps;
        }

        if (instructionNode.isArray()) {
            for (JsonNode item : instructionNode) {
                if (item.isTextual()) {
                    String line = trimToNull(item.asText());
                    if (line != null) {
                        steps.add(line);
                    }
                } else if (item.isObject()) {
                    String line = text(item.get("text"));
                    if (!isBlank(line)) {
                        steps.add(line.trim());
                    }
                }
            }
        }
        return steps;
    }

    private List<IngredientLineForm> parseIngredientLines(List<String> lines) {
        List<IngredientLineForm> parsed = new ArrayList<>();
        if (lines == null) {
            return parsed;
        }

        for (String rawLine : lines) {
            String line = trimToNull(rawLine);
            if (line == null) {
                continue;
            }

            IngredientLineForm form = new IngredientLineForm();
            form.setPreparation("");

            String[] tokens = line.split("\\s+");
            double qty = 0.0;
            int consumed = 0;

            if (tokens.length >= 2 && isNumericOrFraction(tokens[0]) && isNumericOrFraction(tokens[1])) {
                qty = parseNumeric(tokens[0]) + parseNumeric(tokens[1]);
                consumed = 2;
            } else if (tokens.length >= 1 && isNumericOrFraction(tokens[0])) {
                qty = parseNumeric(tokens[0]);
                consumed = 1;
            }

            if (qty > 0) {
                form.setQuantity(stripTrailingZero(qty));
                if (tokens.length > consumed && isLikelyUnit(tokens[consumed])) {
                    form.setUnit(tokens[consumed].toLowerCase(Locale.ROOT));
                    consumed += 1;
                } else {
                    form.setUnit("");
                }

                StringBuilder name = new StringBuilder();
                for (int i = consumed; i < tokens.length; i++) {
                    if (name.length() > 0) {
                        name.append(' ');
                    }
                    name.append(tokens[i]);
                }
                form.setName(trimToNull(name.toString()));
            } else {
                form.setQuantity("1");
                form.setUnit("");
                form.setName(line);
            }

            if (!isBlank(form.getName())) {
                parsed.add(form);
            }
        }

        return parsed;
    }

    private String extractImageUrl(JsonNode imageNode) {
        if (imageNode == null || imageNode.isNull()) {
            return null;
        }
        if (imageNode.isTextual()) {
            return imageNode.asText();
        }
        if (imageNode.isArray()) {
            for (JsonNode item : imageNode) {
                String value = extractImageUrl(item);
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        if (imageNode.isObject()) {
            String url = text(imageNode.get("url"));
            if (!isBlank(url)) {
                return url;
            }
        }
        return null;
    }

    private Integer parseMinutes(String totalTime, String prepTime) {
        Integer total = parseIsoDurationToMinutes(totalTime);
        if (total != null && total > 0) {
            return total;
        }
        Integer prep = parseIsoDurationToMinutes(prepTime);
        return (prep != null && prep > 0) ? prep : null;
    }

    private Integer parseIsoDurationToMinutes(String value) {
        if (isBlank(value)) {
            return null;
        }

        Matcher matcher = ISO_MINUTES_PATTERN.matcher(value.trim().toUpperCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }

        int hours = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : 0;
        int minutes = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        int total = (hours * 60) + minutes;
        return total > 0 ? total : null;
    }

    private Integer parseServings(String yield) {
        if (isBlank(yield)) {
            return null;
        }
        Matcher matcher = SERVINGS_PATTERN.matcher(yield);
        if (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String normalizeMealType(String rawCategory) {
        if (isBlank(rawCategory)) {
            return null;
        }
        String category = rawCategory.toLowerCase(Locale.ROOT);
        if (category.contains("breakfast")) {
            return "Breakfast";
        }
        if (category.contains("lunch")) {
            return "Lunch";
        }
        if (category.contains("dinner") || category.contains("main")) {
            return "Dinner";
        }
        if (category.contains("snack") || category.contains("appetizer")) {
            return "Snack";
        }
        if (category.contains("drink") || category.contains("beverage")) {
            return "Beverage";
        }
        return null;
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = text(item);
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private boolean isLikelyUnit(String token) {
        if (token == null) {
            return false;
        }
        String cleaned = token.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return COMMON_UNITS.contains(cleaned);
    }

    private boolean isNumericOrFraction(String token) {
        if (token == null) {
            return false;
        }
        return token.matches("^\\d+(?:\\.\\d+)?$") || FRACTION_PATTERN.matcher(token).matches();
    }

    private double parseNumeric(String token) {
        if (token == null) {
            return 0.0;
        }
        Matcher fraction = FRACTION_PATTERN.matcher(token);
        if (fraction.matches()) {
            double numerator = Double.parseDouble(fraction.group(1));
            double denominator = Double.parseDouble(fraction.group(2));
            if (denominator == 0) {
                return 0.0;
            }
            return numerator / denominator;
        }
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private String stripTrailingZero(double value) {
        if (value % 1 == 0) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }
}
