package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AECraftingPattern;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared dependency-pattern generation logic used by all mixins.
 *
 * Performance notes:
 * - The full crafting recipe collection is indexed ONCE per recipe manager
 *   (i.e. once per server start / datapack reload) into an output-item -> recipes
 *   map. Lookups per item are then O(1) instead of scanning the entire recipe
 *   book (30k+ recipes in large packs) for every input item at every recursion
 *   level.
 * - The index is shared across ALL pattern providers, so N providers on a
 *   network no longer each redo the full scan.
 * - Generation is server-side only, so the client level never competes for the
 *   single cache slot in singleplayer.
 */
public final class RecursivePatternGenerator {

    private RecursivePatternGenerator() {}

    /**
     * Cached index plus the identity of the recipe manager it was built from.
     * The manager is held weakly so an unloaded level can still be collected, and
     * the recipe count acts as a cheap fingerprint for in-place recipe replacement
     * (KubeJS / CraftTweaker) on an otherwise unchanged manager instance.
     */
    private record CachedIndex(WeakReference<RecipeManager> manager, int recipeCount,
                               Map<Item, List<RecipeHolder<CraftingRecipe>>> index) {
        boolean matches(RecipeManager current, int currentRecipeCount) {
            return manager.get() == current && recipeCount == currentRecipeCount;
        }
    }

    private static volatile CachedIndex cached;

    /**
     * Get (building if needed) the output-item -> recipes index for the given level.
     * Automatically rebuilt when the recipe manager instance or its recipe count changes.
     */
    private static Map<Item, List<RecipeHolder<CraftingRecipe>>> getRecipeIndex(Level level) {
        RecipeManager manager = level.getRecipeManager();
        CachedIndex snapshot = cached;
        if (snapshot != null && snapshot.matches(manager, manager.getRecipes().size())) {
            return snapshot.index();
        }
        synchronized (RecursivePatternGenerator.class) {
            int recipeCount = manager.getRecipes().size();
            snapshot = cached;
            if (snapshot != null && snapshot.matches(manager, recipeCount)) {
                return snapshot.index();
            }
            long start = System.nanoTime();
            Map<Item, List<RecipeHolder<CraftingRecipe>>> newIndex = new HashMap<>();
            var registryAccess = level.registryAccess();
            for (var recipe : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
                try {
                    ItemStack output = recipe.value().getResultItem(registryAccess);
                    if (!output.isEmpty()) {
                        newIndex.computeIfAbsent(output.getItem(), k -> new ArrayList<>()).add(recipe);
                    }
                } catch (Exception e) {
                    RecursiveAE2PatternProvider.LOGGER.debug("Skipping recipe {} while indexing: {}",
                            recipe.id(), e.getMessage());
                }
            }
            cached = new CachedIndex(new WeakReference<>(manager), recipeCount, newIndex);
            RecursiveAE2PatternProvider.LOGGER.info(
                    "Indexed {} crafting recipes by output item in {} ms",
                    newIndex.values().stream().mapToInt(List::size).sum(),
                    (System.nanoTime() - start) / 1_000_000);
            return newIndex;
        }
    }

    /**
     * Collect and decode the recursive patterns held in a provider's pattern inventory.
     */
    public static List<IPatternDetails> collectRecursivePatterns(Iterable<ItemStack> patternInventory, Level level) {
        List<IPatternDetails> recursivePatterns = new ArrayList<>();
        if (patternInventory == null || level == null) {
            return recursivePatterns;
        }

        for (ItemStack stack : patternInventory) {
            if (PatternUtil.isRecursive(stack)) {
                var details = PatternDetailsHelper.decodePattern(stack, level);
                if (details != null) {
                    recursivePatterns.add(details);
                }
            }
        }
        return recursivePatterns;
    }

    /**
     * Generate dependency patterns for the given recursive patterns.
     * Returns the list of auto-generated patterns to append to the provider.
     */
    public static List<IPatternDetails> generate(List<IPatternDetails> recursivePatterns,
                                                 Level level, int maxDepth) {
        List<IPatternDetails> generated = new ArrayList<>();
        if (level == null || level.isClientSide() || recursivePatterns.isEmpty()) {
            return generated;
        }
        Map<Item, List<RecipeHolder<CraftingRecipe>>> index = getRecipeIndex(level);
        Set<String> processedRecipes = new HashSet<>();
        Set<AEItemKey> processedItems = new HashSet<>();

        for (IPatternDetails recursivePattern : recursivePatterns) {
            generateDependencyPatterns(recursivePattern, level, 0, maxDepth,
                    index, processedRecipes, processedItems, generated);
        }
        return generated;
    }

    /**
     * Append generated patterns to the provider's pattern list, registering their
     * inputs when the provider tracks a pattern input collection.
     */
    public static void appendGenerated(List<IPatternDetails> generated,
                                       List<IPatternDetails> patterns,
                                       Collection<? super AEKey> patternInputs) {
        for (var autoPattern : generated) {
            patterns.add(autoPattern);
            if (patternInputs == null) {
                continue;
            }
            for (var patternInput : autoPattern.getInputs()) {
                for (var inputCandidate : patternInput.getPossibleInputs()) {
                    patternInputs.add(inputCandidate.what().dropSecondary());
                }
            }
        }
    }

    /**
     * Recursively generate patterns for all dependencies of a given pattern.
     */
    private static void generateDependencyPatterns(IPatternDetails pattern, Level level,
                                                   int currentDepth, int maxDepth,
                                                   Map<Item, List<RecipeHolder<CraftingRecipe>>> index,
                                                   Set<String> processedRecipes,
                                                   Set<AEItemKey> processedItems,
                                                   List<IPatternDetails> generated) {
        if (level == null) return;

        // Check depth limit (-1 means no limit)
        if (maxDepth >= 0 && currentDepth >= maxDepth) {
            return;
        }

        for (var patternInput : pattern.getInputs()) {
            for (var inputCandidate : patternInput.getPossibleInputs()) {
                try {
                    var key = inputCandidate.what();
                    if (!(key instanceof AEItemKey itemKey)) {
                        continue;
                    }

                    if (!processedItems.add(itemKey)) {
                        continue;
                    }

                    ItemStack inputStack = itemKey.toStack();

                    findAndCreatePatternsForItem(inputStack, index, level, pattern,
                            currentDepth, maxDepth, processedRecipes, processedItems, generated);

                } catch (Exception e) {
                    RecursiveAE2PatternProvider.LOGGER.debug("Error processing input candidate: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Find and create patterns for a specific item using the pre-built index.
     */
    private static void findAndCreatePatternsForItem(ItemStack targetItem,
                                                     Map<Item, List<RecipeHolder<CraftingRecipe>>> index,
                                                     Level level, IPatternDetails parentPattern,
                                                     int currentDepth, int maxDepth,
                                                     Set<String> processedRecipes,
                                                     Set<AEItemKey> processedItems,
                                                     List<IPatternDetails> generated) {
        // O(1) lookup: only recipes whose output item matches the target
        List<RecipeHolder<CraftingRecipe>> candidates = index.get(targetItem.getItem());
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        for (var recipe : candidates) {
            String recipeId = recipe.id().toString();

            if (processedRecipes.contains(recipeId)) {
                continue;
            }

            try {
                ItemStack recipeOutput = recipe.value().getResultItem(level.registryAccess());
                if (recipeOutput.isEmpty() || !recipeOutput.is(targetItem.getItem())) {
                    continue;
                }

                if (ItemStack.isSameItemSameComponents(targetItem, recipeOutput)) {
                    processedRecipes.add(recipeId);

                    ItemStack[] inputs = getRecipeInputsAs3x3Grid(recipe.value());
                    ItemStack patternStack = AEItems.CRAFTING_PATTERN.stack();

                    // Extract substitute settings from parent pattern
                    boolean allowSubstitutes = Config.DEFAULT_ALLOW_SUBSTITUTES.get();
                    boolean allowFluidSubstitutes = Config.DEFAULT_ALLOW_FLUID_SUBSTITUTES.get();

                    // If parent pattern is an AECraftingPattern, inherit its substitute settings
                    if (parentPattern instanceof AECraftingPattern parentCraftingPattern) {
                        allowSubstitutes = parentCraftingPattern.canSubstitute();
                        allowFluidSubstitutes = parentCraftingPattern.canSubstituteFluids();
                    }

                    AECraftingPattern.encode(patternStack, recipe, inputs, recipeOutput, allowSubstitutes, allowFluidSubstitutes);
                    AECraftingPattern aePattern = new AECraftingPattern(
                            Objects.requireNonNull(AEItemKey.of(patternStack)), level);

                    if (aePattern.getOutputs() != null && !aePattern.getOutputs().isEmpty()) {
                        generated.add(aePattern);
                        // Continue recursion with incremented depth
                        generateDependencyPatterns(aePattern, level, currentDepth + 1, maxDepth,
                                index, processedRecipes, processedItems, generated);
                    }
                }

            } catch (Exception e) {
                RecursiveAE2PatternProvider.LOGGER.debug("Failed to create dependency pattern for recipe: {} - {}",
                        recipe.id(), e.getMessage());
            }
        }
    }

    /**
     * Convert recipe ingredients to a 3x3 grid format.
     */
    private static ItemStack[] getRecipeInputsAs3x3Grid(CraftingRecipe recipe) {
        ItemStack[] inputs = new ItemStack[9];
        Arrays.fill(inputs, ItemStack.EMPTY);

        var ingredients = recipe.getIngredients();

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            int width = shapedRecipe.getWidth();
            int height = shapedRecipe.getHeight();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int ingredientIndex = y * width + x;
                    int gridIndex = y * 3 + x;

                    if (ingredientIndex < ingredients.size()) {
                        var ingredient = ingredients.get(ingredientIndex);
                        if (!ingredient.isEmpty() && ingredient.getItems().length > 0) {
                            inputs[gridIndex] = ingredient.getItems()[0].copy();
                        }
                    }
                }
            }
        } else {
            int index = 0;
            for (var ingredient : ingredients) {
                if (!ingredient.isEmpty() && ingredient.getItems().length > 0 && index < 9) {
                    inputs[index] = ingredient.getItems()[0].copy();
                    index++;
                }
            }
        }

        return inputs;
    }
}
