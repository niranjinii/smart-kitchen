# FEAT: Web Scraper Importer (Local Notes)

## Purpose
Add a URL-based recipe import option to the Create Recipe page without changing existing create/edit/publish logic.

## Implementation Approach (Additive Only)
The feature was implemented as a side-path that prefills the existing form.
- Existing create flow, validation flow, image upload flow, and publish submit flow remain intact.
- Import is optional and only runs when user clicks Import from URL.
- Imported data is mapped into already-existing form fields and row templates.

## What Was Added
- Maven dependencies:
  - jsoup (HTML fetch/parsing)
  - jackson-databind (JSON-LD parsing)
- New DTO:
  - ScrapedRecipeData
- New service:
  - ScraperService
- New controller endpoint:
  - POST /recipes/import-url
- Publish page UI wiring:
  - Top-right "Import from URL" button next to page title (Create mode only)
  - Import modal with URL input + Import action
  - JS prefill into title/description/image/time/yield/category/ingredients/instructions
- Responsive behavior for the import modal:
  - Desktop/tablet: centered standard modal
  - Mobile: near full-width modal and large thumb-friendly Import button

## Why This Design
1. Safety
- Keeps existing recipe publish behavior untouched.
- Limits blast radius to one new endpoint and one optional UI action.

2. Compatibility
- Reuses existing dynamic ingredient/instruction row templates and naming logic.
- Reuses existing hidden image URL field behavior.

3. Resilience
- Primary extraction reads JSON-LD Recipe schema (most recipe sites).
- Fallback extraction reads OG/meta tags and common ingredient/instruction selectors.

## Scraper Flow
1. Validate URL (http/https).
2. Fetch document with timeout and browser-like user-agent.
3. Parse JSON-LD and locate @type Recipe.
4. Extract title, description, image, total/prep time, yield, category, ingredients, instructions.
5. Apply fallbacks from meta/common selectors if JSON-LD is missing/incomplete.
6. Normalize values for existing RecipeForm expectations.
7. Return JSON payload for frontend prefill.

## Frontend Flow
1. User clicks Import from URL.
2. Modal opens, user pastes URL.
3. Frontend POSTs to /recipes/import-url.
4. On success, fields are prefilled and ingredient/instruction rows are reconstructed.
5. User can edit imported values, then publish normally.

## Known Limits
- Some sites block scraping or hide recipe data behind scripts/logins.
- Some instruction formats may import as one long step and need manual splitting.
- Unit parsing is heuristic; unusual units may need manual cleanup.

## Suggested Next Improvements
- Add unit tests for ScraperService parsing heuristics.
- Add integration tests for /recipes/import-url success/error responses.
- Optionally add per-domain extraction rules for high-traffic recipe domains.
