Recursive AE2 Pattern Provider

[![Build](https://github.com/cryolithic/recursiveae2patternprovider/actions/workflows/build.yml/badge.svg)](https://github.com/cryolithic/recursiveae2patternprovider/actions/workflows/build.yml)


## ⬇️ Download

This is a community-maintained build with fixes applied while we wait for the
upstream maintainer to merge them. There are two ways to grab the jar:

- **Stable release (recommended):** Head to the
  [**Releases page**](https://github.com/cryolithic/recursiveae2patternprovider/releases)
  and download the latest `recursiveae2patternprovider-<version>.jar` from the
  assets. Drop it into your `mods/` folder.
- **Latest build:** Open the
  [**Build workflow**](https://github.com/cryolithic/recursiveae2patternprovider/actions/workflows/build.yml),
  click the most recent successful run, and download the
  `recursiveae2patternprovider-jar` artifact (requires being signed in to GitHub).

Requires Minecraft **1.21.1**, **NeoForge 21.1.203+**, and **Applied Energistics 2**.


🔄 Overview


**Recursive AE2 Pattern Provider** revolutionizes Applied Energistics 2 automation by automatically generating dependency patterns for complex recipes. Say goodbye to tedious manual pattern creation and hello to effortless multi-tier crafting automation!


## ✨ Features


### 🎯 **Automatic Pattern Generation**
- **Smart Dependencies**: Automatically creates patterns for missing crafting ingredients
- **Recursive Logic**: Generates patterns for ingredients of ingredients, as deep as you configure
- **Recipe Discovery**: Only creates patterns for items that have valid crafting recipes


### 🔧 **Per-Pattern Control**
- **Add Recursion**: Craft any AE2 pattern with the configured recipe item (default: iron ingot) to make it recursive
- **Universal Support**: Works with ALL AE2 pattern types:
- Crafting Patterns
- Processing Patterns
- Smithing Table Patterns
- Stonecutting Patterns
- **Remove Recursion**: Craft recursive pattern alone (without the recipe item) to make it normal


### 🎨 **Visual Feedback**
- **Enhanced Tooltips**: Clear indicators showing recursive status and usage instructions
- **Color Tinting**: Recursive patterns show with distinctive green coloring
- **Smart Instructions**: Tooltips show how to enable/disable recursion


### ⚙️ **Configuration Options**
- **Recursion Depth**: Control how deep pattern generation goes (-1 = unlimited, 0 = disabled, 1+ = limited)
- **Recipe Item**: Customize which item is required to craft recursive patterns (default: `minecraft:iron_ingot`)
- **Substitute Inheritance**: Auto-generated patterns inherit substitute settings from parent patterns
- **Default Settings**: Configure default substitute behavior for patterns without parent context


## 🎮 How to Use


### **Step 1: Create Your Pattern**
Create any AE2 pattern normally (e.g., Iron Pickaxe recipe)


### **Step 2: Make it Recursive**
```
[AE2 Pattern] + [Recipe Item] → [Recursive Pattern]
```
Craft the pattern with the configured recipe item (default: iron ingot) in any crafting table

### **Step 2b: Remove Recursion (Optional)**
```
[Recursive Pattern] → [Normal Pattern]
```
Craft the recursive pattern alone (without the recipe item) to make it normal again

### **💡 Customizing the Recipe Item**
To change which item is required for crafting recursive patterns, edit your mod configuration:
```toml
[recursiveae2patternprovider-common.toml]
recipeItem = "minecraft:diamond"  # Use diamond instead of iron
# Or use any other item:
# recipeItem = "minecraft:gold_ingot"
# recipeItem = "minecraft:emerald"
# recipeItem = "ae2:calculation_processor"
```

### **Step 3: Install in Pattern Provider**
Place the recursive pattern in your Pattern Provider


### **Step 4: Automatic Magic**
The mod automatically generates patterns for:
- Sticks (if Iron Pickaxe needs them)
- Iron Ingots (if you're using raw iron)
- Any other missing intermediate components


### **Example Scenario**
**Traditional AE2**: To autocraft Iron Pickaxes, you manually create:
- Iron Pickaxe pattern
- Stick pattern
- Iron Ingot smelting pattern (if using raw iron)
- Wood plank pattern (if making sticks from logs)
- ... and so on


**With This Mod**: Create ONE recursive Iron Pickaxe pattern → All dependencies auto-generated!


## 📋 Configuration


The mod includes several configuration options in `config/recursiveae2patternprovider-common.toml`:


```toml
# Enable/disable the entire mod
enableRecursiveAE2PatternProvider = true


# Maximum recursion depth (-1 = unlimited, 0 = disabled, 1+ = limited depth)
recursionDepth = -1


# Default substitute settings for auto-generated patterns
defaultAllowSubstitutes = false
defaultAllowFluidSubstitutes = false
```


## 🔧 Technical Details


### **Compatibility**
- **Minecraft**: 1.21.1
- **NeoForge**: Latest
- **Applied Energistics 2**: Required
- **Server/Client**: Works on both dedicated servers and single-player


### **Performance**
- **Smart Caching**: Prevents duplicate pattern generation
- **Efficient Processing**: Only generates patterns when actually needed
- **AE2 Integration**: Uses AE2's existing pattern system for maximum compatibility


**Transform your AE2 experience from tedious pattern management to effortless automation mastery!**