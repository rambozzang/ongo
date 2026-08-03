package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.ClipValidation
import com.ongo.domain.ugc.shorts.ClipValidationRepository
import com.ongo.domain.ugc.shorts.ClipValidationSeverity
import com.ongo.infrastructure.persistence.jooq.Fields.CLIP_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.PASSED
import com.ongo.infrastructure.persistence.jooq.Fields.RULE_CODE
import com.ongo.infrastructure.persistence.jooq.Fields.SEVERITY
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_VALIDATIONS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.ZoneOffset

@Repository
class ShortsClipValidationJooqRepository(
    private val dsl: DSLContext,
) : ClipValidationRepository {

    override fun saveAll(validations: List<ClipValidation>): List<ClipValidation> = validations.map { validation ->
        val id = dsl.insertInto(UGC_SHORTS_VALIDATIONS)
            .set(CLIP_ID, validation.clipId)
            .set(RULE_CODE, validation.ruleCode)
            .set(SEVERITY, validation.severity.name)
            .set(PASSED, validation.passed)
            .set(MESSAGE, validation.message)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        findById(id)!!
    }

    private fun findById(id: Long): ClipValidation? =
        dsl.select()
            .from(UGC_SHORTS_VALIDATIONS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toValidation()

    private fun Record.toValidation() = ClipValidation(
        id = get(ID),
        clipId = get(CLIP_ID),
        ruleCode = get(RULE_CODE),
        severity = ClipValidationSeverity.valueOf(get(SEVERITY)),
        passed = get(PASSED),
        message = get(MESSAGE),
        createdAt = localDateTime(CREATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
    )
}
