package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;
import fr.maxlego08.zauctionhouse.utils.MarketMaterials;

import java.util.Locale;
import java.util.Map;

public final class OrderFilterRuleLoader implements RuleLoader {
    @Override
    public String getType() {
        return "raven-order-filter";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        String filter = String.valueOf(configuration.get("filter")).toLowerCase(Locale.ROOT);
        return switch (filter) {
            case "blocks" -> context -> context.getMaterial().isBlock();
            case "tools" -> context -> MarketMaterials.isTool(context.getMaterial());
            case "food" -> context -> context.getMaterial().isEdible();
            case "combat" -> context -> MarketMaterials.isCombat(context.getMaterial());
            case "potions" -> context -> MarketMaterials.isPotion(context.getMaterial());
            case "books" -> context -> MarketMaterials.isBook(context.getMaterial());
            case "ingredients" -> context -> MarketMaterials.isIngredient(context.getMaterial());
            case "utilities" -> context -> MarketMaterials.isUtility(context.getMaterial());
            default -> null;
        };
    }
}
