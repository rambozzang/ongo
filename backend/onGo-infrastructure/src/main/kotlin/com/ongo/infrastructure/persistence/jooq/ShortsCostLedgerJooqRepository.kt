package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.ShortsCostEntry
import com.ongo.domain.ugc.shorts.ShortsCostLedgerRepository
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_COST_LEDGER
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

private val COST_ID = DSL.field(DSL.name("ugc_shorts_cost_ledger", "id"), Long::class.javaObjectType)
private val COST_RUN_ID = DSL.field(DSL.name("ugc_shorts_cost_ledger", "run_id"), Long::class.javaObjectType)
private val COST_KIND = DSL.field(DSL.name("ugc_shorts_cost_ledger", "kind"), String::class.java)
private val COST_STAGE = DSL.field(DSL.name("ugc_shorts_cost_ledger", "stage"), String::class.java)
private val COST_PROVIDER = DSL.field(DSL.name("ugc_shorts_cost_ledger", "provider"), String::class.java)
private val COST_MODEL = DSL.field(DSL.name("ugc_shorts_cost_ledger", "model"), String::class.java)
private val COST_AUDIO_SECONDS = DSL.field(DSL.name("ugc_shorts_cost_ledger", "audio_duration_seconds"), java.math.BigDecimal::class.java)
private val COST_INPUT_TOKENS = DSL.field(DSL.name("ugc_shorts_cost_ledger", "input_tokens"), Long::class.javaObjectType)
private val COST_OUTPUT_TOKENS = DSL.field(DSL.name("ugc_shorts_cost_ledger", "output_tokens"), Long::class.javaObjectType)

@Repository
class ShortsCostLedgerJooqRepository(
    private val dsl: DSLContext,
) : ShortsCostLedgerRepository {

    override fun record(entry: ShortsCostEntry): ShortsCostEntry {
        val id = dsl.insertInto(UGC_SHORTS_COST_LEDGER)
            .set(COST_RUN_ID, entry.runId)
            .set(COST_KIND, entry.kind.name)
            .set(COST_STAGE, entry.stage)
            .set(COST_PROVIDER, entry.provider)
            .set(COST_MODEL, entry.model)
            .set(COST_AUDIO_SECONDS, entry.audioDurationSeconds)
            .set(COST_INPUT_TOKENS, entry.inputTokens)
            .set(COST_OUTPUT_TOKENS, entry.outputTokens)
            .returningResult(COST_ID)
            .fetchOne()!!
            .get(COST_ID)
        return entry.copy(id = id)
    }
}
