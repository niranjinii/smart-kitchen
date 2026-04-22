# FEAT: Recipe Factory Refactor (Local Notes)

## Why this refactor was done
The app creates Recipe objects from two distinct sources:
- user-submitted form data
- scraped URL-derived data

A source-aware factory was introduced so object construction is centralized and extensible, instead of mixing construction logic directly in service methods.

## Design intent from OOAD perspective
This is an application of the Factory design pattern (simple/source-aware factory style):
- Encapsulate object creation logic in one place.
- Hide branching/instantiation details from callers.
- Let service/controller focus on orchestration and business workflow.

## Exactly how Factory pattern is reflected in this code

### 1) Product
- `Recipe` entity is the product being created/populated.

### 2) Creation request abstraction
- `RecipeCreationRequest` wraps source-specific input and metadata.
- `RecipeSourceType` identifies source (`FORM`, `SCRAPED_URL`).
- This request object is what the factory inspects to choose creation path.

### 3) Factory
- `RecipeFactory` is the central creator.
- `create(request)` instantiates `Recipe` and delegates to `populate(...)`.
- `populate(recipe, request)` identifies source type and applies source-specific mapping.

### 4) Source-specific construction logic
- FORM path: maps from `RecipeForm` to `Recipe` fields, including tags and normalized URLs.
- SCRAPED_URL path: maps from `ScrapedRecipeData` + source URL to `Recipe` fields.

### 5) Client usage
- `RecipeService` now requests construction through factory:
  - create flow: `recipeFactory.create(RecipeCreationRequest.fromForm(form))`
  - update flow: `recipeFactory.populate(existingRecipe, RecipeCreationRequest.fromForm(form))`

## What intentionally stayed outside factory
To keep responsibilities clean and avoid behavior changes, factory does not own:
- authentication/user resolution
- validation orchestration
- repository save/transaction boundaries
- ingredient upsert logic
- cloud image cleanup side effects

These remain in `RecipeService`.

## Why this is a light/safe refactor
- Endpoint contracts unchanged.
- UI templates unchanged for this refactor.
- Existing create/update behavior preserved.
- Construction logic moved, not redesigned.

## Current structure after refactor
- `dto/RecipeSourceType.java`
- `dto/RecipeCreationRequest.java`
- `services/RecipeFactory.java`
- `services/RecipeService.java` (orchestration remains here; creation delegated)

## Notes for future extension
Adding another source (e.g., AI parser/import file) should only require:
1. Add a new source type.
2. Extend request payload.
3. Add a branch in `RecipeFactory.populate(...)`.
Without rewriting service flow.
