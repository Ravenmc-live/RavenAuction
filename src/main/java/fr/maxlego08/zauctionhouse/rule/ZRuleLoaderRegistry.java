package fr.maxlego08.zauctionhouse.rule;

import fr.maxlego08.menu.api.utils.version.MinecraftVersion;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoaderRegistry;
import fr.maxlego08.zauctionhouse.rule.loaders.*;

import java.util.*;

/**
 * Implementation of {@link RuleLoaderRegistry}.
 * Manages registration and lookup of rule loaders.
 */
public class ZRuleLoaderRegistry implements RuleLoaderRegistry {

    private final Map<String, RuleLoader> loaders = new HashMap<>();
    private final AuctionPlugin plugin;

    public ZRuleLoaderRegistry(AuctionPlugin auctionPlugin) {
        this.plugin = auctionPlugin;
    }


    @Override
    public void registerDefaultLoaders() {
        register(new MaterialRuleLoader());
        register(new MaterialSuffixRuleLoader());
        register(new MaterialPrefixRuleLoader());
        register(new MaterialContainsRuleLoader());
        register(new TagRuleLoader(this.plugin));
        register(new NameRuleLoader());
        register(new LoreRuleLoader());
        register(new CustomModelDataRuleLoader());
        register(new AndRuleLoader(this));
        if (MinecraftVersion.getCurrentVersion().isAtLeast(MinecraftVersion.parse("1.21.4"))) {
            register(new ItemModelRuleLoader());
        }
    }

    @Override
    public void register(RuleLoader loader) {
        String type = loader.getType().toLowerCase(Locale.ROOT);
        this.loaders.put(type, loader);

        // Register aliases
        for (String alias : loader.getAliases()) {
            this.loaders.put(alias.toLowerCase(Locale.ROOT), loader);
        }
    }

    @Override
    public void unregister(String type) {
        RuleLoader loader = this.loaders.remove(type.toLowerCase(Locale.ROOT));
        if (loader != null) {
            // Also remove aliases
            for (String alias : loader.getAliases()) {
                this.loaders.remove(alias.toLowerCase(Locale.ROOT));
            }
        }
    }

    @Override
    public Optional<RuleLoader> getLoader(String type) {
        return Optional.ofNullable(this.loaders.get(type.toLowerCase(Locale.ROOT)));
    }

    @Override
    public List<RuleLoader> getLoaders() {
        // Return unique loaders (aliases may point to same loader)
        return List.copyOf(new HashSet<>(this.loaders.values()));
    }

    @Override
    public Rule loadRule(Map<?, ?> configuration) {
        String type = RuleConfigHelper.getString(configuration, "type");
        if (type == null) {
            return null;
        }

        return getLoader(type).map(loader -> loader.load(configuration)).orElse(null);
    }

    @Override
    public List<Rule> loadRules(Map<?, ?> configuration) {
        Rule rule = loadRule(configuration);
        return rule != null ? List.of(rule) : List.of();
    }

    @Override
    public List<Rule> loadRulesFromList(List<Map<?, ?>> configurations) {
        List<Rule> rules = new ArrayList<>();

        for (Map<?, ?> config : configurations) {
            Rule rule = loadRule(config);
            if (rule != null) {

                if (!rule.isValid()) {
                    this.plugin.getLogger().warning("Invalid rule: " + config.get("type"));
                    continue;
                }

                rules.add(rule);
            }
        }

        return rules;
    }
}
