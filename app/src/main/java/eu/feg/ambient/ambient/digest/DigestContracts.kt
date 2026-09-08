package eu.feg.ambient.ambient.digest

import kotlinx.datetime.Instant

/**
 * One thing that happened while the customer was not looking.
 *
 * [momentId] points back at the ledger row it came from, so a digest item can be explained by
 * the same "Why this?" machinery as anything else we showed — a digest is a decision like any
 * other, not a summary that floats free of the record.
 */
data class DigestItem(
    val text: String,
    val at: Instant,
    val momentId: String,
    val important: Boolean,
)

/**
 * The catch-up card.
 *
 * COMPLIANCE — this is the surface that replaces the overnight push, so it is the one most
 * likely to drift into being a nudge. It carries what happened and nothing about what could
 * happen next: no odds, no offers, no "back in play". The narrator writes the words and
 * NarratorGuard checks them, exactly as everywhere else.
 */
data class Digest(
    /** At most four, ranked. Beyond four it stops being a glance. */
    val items: List<DigestItem>,
    val headline: String,
    val detail: String,
    val spokenText: String,
    val since: Instant,
    val generatedAt: Instant,
)

/**
 * Builds the digest, or declines to.
 *
 * NULL IS THE IMPORTANT RETURN VALUE. A card reading "nothing happened" is worse than no card:
 * it spends the customer's attention to tell them their attention was not needed, which is the
 * precise behaviour this product exists to stop. One trivial item is also null.
 */
interface DigestBuilder {
    suspend fun build(since: Instant): Digest?
}
