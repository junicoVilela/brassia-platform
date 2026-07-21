package br.com.brew.brassia.recipe.domain;

public record RecipeName(String value) {
    public RecipeName {
        value = value == null ? "" : value.trim();
        if (value.isBlank() || value.length() > 120) {
            throw new IllegalArgumentException("recipe name must contain 1 to 120 characters");
        }
    }
}
