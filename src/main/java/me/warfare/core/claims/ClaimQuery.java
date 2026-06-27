package me.warfare.core.claims;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Immutable result of a "who controls this spot" lookup.
 *
 * <p>Returned by {@link ClaimManager} location queries so listener code does not
 * juggle nullable claims and owner comparisons inline. A query with a
 * {@code null} claim means the location is unclaimed.</p>
 *
 * @param claim the claim covering the queried location, or {@code null} if none
 * @param actor the UUID whose access was being tested (may be {@code null} for
 *              non-player checks such as fluid flow)
 */
public record ClaimQuery(@Nullable Claim claim, @Nullable UUID actor) {

    /** @return {@code true} if a claim covers the queried location */
    public boolean isClaimed() {
        return claim != null;
    }

    /**
     * Whether the queried actor owns the covering claim.
     *
     * @return {@code true} if there is a claim and the actor is its owner
     */
    public boolean isOwner() {
        return claim != null && actor != null && claim.owner().equals(actor);
    }

    /**
     * Whether the actor is blocked here: the location is claimed and the actor
     * is not the owner. This is the common gate used by protection listeners.
     *
     * @return {@code true} if the actor should be denied at this location
     */
    public boolean isProtectedFromActor() {
        return isClaimed() && !isOwner();
    }

    /**
     * @return the covering claim; only call when {@link #isClaimed()} is true
     */
    public @NotNull Claim requireClaim() {
        if (claim == null) {
            throw new IllegalStateException("No claim present in query");
        }
        return claim;
    }
}
