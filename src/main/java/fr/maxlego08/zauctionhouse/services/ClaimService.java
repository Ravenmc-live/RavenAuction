package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionClaimService;
import fr.maxlego08.zauctionhouse.api.services.result.ClaimResult;
import fr.maxlego08.zauctionhouse.api.storage.dto.TransactionDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.TransactionRepository;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ClaimService extends AuctionService implements AuctionClaimService {

    private final AuctionPlugin plugin;

    /**
     * One claim at a time per player: the join auto-claim and a manual /ah claim used to run
     * concurrently, both read the same PENDING rows and paid them twice (or, worse, marked rows
     * as retrieved without paying them).
     */
    private final Set<UUID> claimsInProgress = ConcurrentHashMap.newKeySet();

    public ClaimService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<ClaimResult> claimMoney(Player player) {
        UUID uniqueId = player.getUniqueId();

        if (!this.claimsInProgress.add(uniqueId)) {
            message(this.plugin, player, Message.CLAIM_NO_PENDING);
            return CompletableFuture.completedFuture(ClaimResult.nothingToClaim("Claim already in progress"));
        }

        return getPendingTransactions(uniqueId)
                .thenCompose(transactions -> payOnPlayerThread(player, transactions))
                .whenComplete((result, throwable) -> this.claimsInProgress.remove(uniqueId));
    }

    /**
     * Deposits run on the player's thread: Vault/currency providers are not thread safe, and a
     * deposit that throws on an async thread used to be swallowed while the transaction was still
     * marked as retrieved, which is exactly the "sold an item, never got the money" report.
     */
    private CompletableFuture<ClaimResult> payOnPlayerThread(Player player, List<TransactionDTO> transactions) {
        if (transactions.isEmpty()) {
            return onPlayer(player, () -> {
                message(this.plugin, player, Message.CLAIM_NO_PENDING);
                return ClaimResult.nothingToClaim("No pending transactions");
            });
        }

        return onPlayer(player, () -> pay(player, transactions));
    }

    private ClaimResult pay(Player player, List<TransactionDTO> transactions) {
        if (!player.isOnline()) {
            return ClaimResult.failure("Player is offline");
        }

        var economyManager = this.plugin.getEconomyManager();
        var depositReason = this.plugin.getConfiguration().getAutoClaimConfiguration().depositReason();

        Map<String, List<TransactionDTO>> byEconomy = transactions.stream()
                .filter(transaction -> transaction.value() != null)
                .filter(transaction -> transaction.value().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(TransactionDTO::economy_name));

        if (byEconomy.isEmpty()) {
            message(this.plugin, player, Message.CLAIM_NO_PENDING);
            return ClaimResult.nothingToClaim("No positive amount to claim");
        }

        List<Integer> paidTransactionIds = new ArrayList<>();
        BigDecimal totalClaimed = BigDecimal.ZERO;
        AuctionEconomy lastEconomy = null;
        int failedEconomies = 0;

        for (var entry : byEconomy.entrySet()) {
            String economyName = entry.getKey();
            List<TransactionDTO> economyTransactions = entry.getValue();

            var optionalEconomy = economyManager.getEconomy(economyName);
            if (optionalEconomy.isEmpty()) {
                // Keep the rows PENDING: the economy may be registered again after a reload.
                failedEconomies++;
                this.plugin.getLogger().warning("Economy not found: " + economyName
                        + ", " + economyTransactions.size() + " pending transaction(s) kept for " + player.getName());
                continue;
            }

            AuctionEconomy economy = optionalEconomy.get();
            BigDecimal economyTotal = economyTransactions.stream()
                    .map(TransactionDTO::value)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            try {
                economy.deposit(player.getUniqueId(), economyTotal, depositReason);
            } catch (Exception exception) {
                failedEconomies++;
                this.plugin.getLogger().severe("Failed to deposit " + economyTotal + " to " + player.getName()
                        + " for economy " + economyName + ": " + exception.getMessage());
                continue;
            }

            economyTransactions.stream().map(TransactionDTO::id).forEach(paidTransactionIds::add);
            totalClaimed = totalClaimed.add(economyTotal);
            lastEconomy = economy;

            message(this.plugin, player, Message.CLAIM_ECONOMY_SUCCESS,
                    "%amount%", economyManager.format(economy, economyTotal),
                    "%economy%", economy.getDisplayName());
        }

        markRetrieved(paidTransactionIds, player);

        if (totalClaimed.compareTo(BigDecimal.ZERO) > 0) {
            message(this.plugin, player, Message.CLAIM_SUCCESS);

            if (failedEconomies > 0) {
                this.plugin.getLogger().warning("Partially claimed money for " + player.getName()
                        + ": " + failedEconomies + " economy/economies could not be paid and stay pending.");
            }

            return ClaimResult.success("Money claimed successfully", totalClaimed.doubleValue(), lastEconomy);
        }

        if (failedEconomies > 0) {
            message(this.plugin, player, Message.CLAIM_PENDING_NOTIFY,
                    "%amount%", fundsStillPending(byEconomy),
                    "%count%", String.valueOf(byEconomy.size()));
        } else {
            message(this.plugin, player, Message.CLAIM_NO_PENDING);
        }
        return ClaimResult.nothingToClaim("No positive amount to claim");
    }

    private String fundsStillPending(Map<String, List<TransactionDTO>> byEconomy) {
        Map<String, BigDecimal> amounts = new HashMap<>();
        byEconomy.forEach((economy, transactions) -> amounts.put(economy, transactions.stream()
                .map(TransactionDTO::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        return formatPendingMoney(amounts);
    }

    /** Only the transactions that were really paid are closed, the others stay claimable. */
    private void markRetrieved(List<Integer> transactionIds, Player player) {
        if (transactionIds.isEmpty()) return;

        var repository = this.plugin.getStorageManager().with(TransactionRepository.class);
        CompletableFuture.runAsync(() -> {
            try {
                repository.updateStatus(transactionIds, TransactionStatus.RETRIEVED);
            } catch (RuntimeException exception) {
                this.plugin.getLogger().severe("Paid " + player.getName() + " but could not mark "
                        + transactionIds.size() + " transaction(s) as retrieved: " + exception.getMessage());
            }
        }, this.plugin.getExecutorService());
    }

    private <T> CompletableFuture<T> onPlayer(Player player, java.util.function.Supplier<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        this.plugin.getScheduler().runAtEntityWithFallback(player, task -> {
            try {
                future.complete(action.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        }, () -> future.completeExceptionally(new IllegalStateException("Player is no longer online")));
        return future;
    }

    @Override
    public CompletableFuture<Map<String, BigDecimal>> getPendingMoneyByEconomy(UUID playerUniqueId) {
        return getPendingTransactions(playerUniqueId).thenApply(transactions -> {
            Map<String, BigDecimal> result = new HashMap<>();

            for (TransactionDTO transaction : transactions) {
                // Only count positive values (money to receive)
                if (transaction.value().compareTo(BigDecimal.ZERO) > 0) {
                    result.merge(transaction.economy_name(), transaction.value(), BigDecimal::add);
                }
            }

            return result;
        });
    }

    @Override
    public CompletableFuture<BigDecimal> getTotalPendingMoney(UUID playerUniqueId) {
        return getPendingMoneyByEconomy(playerUniqueId).thenApply(byEconomy -> byEconomy.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Override
    public CompletableFuture<List<TransactionDTO>> getPendingTransactions(UUID playerUniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);
            return repository.selectByPlayerAndStatus(playerUniqueId, TransactionStatus.PENDING);
        }, this.plugin.getExecutorService());
    }

    @Override
    public void handlePlayerJoin(Player player) {
        var config = this.plugin.getConfiguration().getAutoClaimConfiguration();

        getPendingMoneyByEconomy(player.getUniqueId()).thenAccept(pendingByEconomy -> {
            // Check if player is still online at the start of async callback
            if (!player.isOnline()) {
                return;
            }

            if (pendingByEconomy.isEmpty()) {
                return;
            }

            BigDecimal total = pendingByEconomy.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            // Claiming is not optional any more: purchases pay the seller directly, so anything
            // still pending is a leftover row written by an older build. It is paid silently on
            // join instead of waiting for a click or a command that no longer exists.
            long delay = config.delayTicks();
            if (delay <= 0) {
                claimMoney(player);
            } else {
                this.plugin.getScheduler().runLater(task -> {
                    if (player.isOnline()) {
                        claimMoney(player);
                    }
                }, delay);
            }
        }).exceptionally(throwable -> {
            this.plugin.getLogger().warning("Could not read pending money for " + player.getName() + ": " + throwable.getMessage());
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> clearPendingTransactions(UUID playerUniqueId, boolean giveMoney) {
        return getPendingTransactions(playerUniqueId).thenAccept(transactions -> {
            if (transactions.isEmpty()) return;

            if (giveMoney) {
                var economyManager = this.plugin.getEconomyManager();
                var depositReason = this.plugin.getConfiguration().getAutoClaimConfiguration().depositReason();

                Map<String, List<TransactionDTO>> byEconomy = transactions.stream().collect(Collectors.groupingBy(TransactionDTO::economy_name));

                for (var entry : byEconomy.entrySet()) {
                    var optionalEconomy = economyManager.getEconomy(entry.getKey());
                    if (optionalEconomy.isEmpty()) {
                        this.plugin.getLogger().warning("Economy not found: " + entry.getKey());
                        continue;
                    }

                    var economy = optionalEconomy.get();
                    BigDecimal economyTotal = entry.getValue().stream()
                            .map(TransactionDTO::value)
                            .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    if (economyTotal.compareTo(BigDecimal.ZERO) > 0) {
                        try {
                            economy.deposit(playerUniqueId, economyTotal, depositReason);
                        } catch (Exception e) {
                            this.plugin.getLogger().severe("Failed to deposit " + economyTotal + " to " + playerUniqueId + " for economy " + entry.getKey() + ": " + e.getMessage());
                        }
                    }
                }
            }

            var transactionIds = transactions.stream().map(TransactionDTO::id).toList();
            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);
            repository.updateStatus(transactionIds, TransactionStatus.RETRIEVED);
        });
    }

    private String formatPendingMoney(Map<String, BigDecimal> pendingByEconomy) {
        var economyManager = this.plugin.getEconomyManager();
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (var entry : pendingByEconomy.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;

            var optionalEconomy = economyManager.getEconomy(entry.getKey());
            if (optionalEconomy.isPresent()) {
                sb.append(economyManager.format(optionalEconomy.get(), entry.getValue()));
            } else {
                sb.append(entry.getValue().setScale(2, java.math.RoundingMode.HALF_UP))
                        .append(" ").append(entry.getKey());
            }
        }

        return sb.toString();
    }
}
