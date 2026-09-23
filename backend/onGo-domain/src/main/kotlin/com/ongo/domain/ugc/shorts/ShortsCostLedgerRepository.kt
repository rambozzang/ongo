package com.ongo.domain.ugc.shorts

interface ShortsCostLedgerRepository {
    /** Measurement is best effort at the caller; each call represents one external request. */
    fun record(entry: ShortsCostEntry): ShortsCostEntry
}
