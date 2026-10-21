package fr.maxlego08.zauctionhouse.cluster;

import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class UnavailableAuctionClusterBridge extends LocalAuctionClusterBridge {
    @Override public CompletableFuture<Boolean> checkAvailability(Item item) {
        return CompletableFuture.completedFuture(false);
    }
    @Override public CompletableFuture<LockToken> lockItem(Item item, UUID buyerId, StorageType storageType) {
        return CompletableFuture.completedFuture(LockToken.noop());
    }
    @Override public boolean isDistributed() { return true; }
}
