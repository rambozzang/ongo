package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.brandvoice.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.BRAND_VOICE_PROFILES
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class BrandVoiceJooqRepository(
    private val dsl: DSLContext,
) : BrandVoiceRepository {

    companion object {
        private val TONE = DSL.field("tone", String::class.java)
        private val TRAIN_STATUS = DSL.field("train_status", String::class.java)
        private val SAMPLE_COUNT = DSL.field("sample_count", Int::class.java)
        private val VOCABULARY = DSL.field("vocabulary", String::class.java)
        private val AVOID_WORDS = DSL.field("avoid_words", String::class.java)
        private val EMOJI_USAGE = DSL.field("emoji_usage", String::class.java)
        private val AVG_SENTENCE_LENGTH = DSL.field("avg_sentence_length", Int::class.java)
        private val SIGNATURE_PHRASE = DSL.field("signature_phrase", String::class.java)
    }

    override fun findById(id: Long): BrandVoiceProfile? =
        dsl.select()
            .from(BRAND_VOICE_PROFILES)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun findByUserId(userId: Long): List<BrandVoiceProfile> =
        dsl.select()
            .from(BRAND_VOICE_PROFILES)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun save(profile: BrandVoiceProfile): BrandVoiceProfile {
        val id = dsl.insertInto(BRAND_VOICE_PROFILES)
            .set(USER_ID, profile.userId)
            .set(NAME, profile.name)
            .set(TONE, profile.tone)
            .set(TRAIN_STATUS, profile.trainStatus)
            .set(SAMPLE_COUNT, profile.sampleCount)
            .set(VOCABULARY, profile.vocabulary)
            .set(AVOID_WORDS, profile.avoidWords)
            .set(EMOJI_USAGE, profile.emojiUsage)
            .set(AVG_SENTENCE_LENGTH, profile.avgSentenceLength)
            .set(SIGNATURE_PHRASE, profile.signaturePhrase)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(profile: BrandVoiceProfile): BrandVoiceProfile {
        dsl.update(BRAND_VOICE_PROFILES)
            .set(NAME, profile.name)
            .set(TONE, profile.tone)
            .set(TRAIN_STATUS, profile.trainStatus)
            .set(SAMPLE_COUNT, profile.sampleCount)
            .set(VOCABULARY, profile.vocabulary)
            .set(AVOID_WORDS, profile.avoidWords)
            .set(EMOJI_USAGE, profile.emojiUsage)
            .set(AVG_SENTENCE_LENGTH, profile.avgSentenceLength)
            .set(SIGNATURE_PHRASE, profile.signaturePhrase)
            .set(UPDATED_AT, java.time.LocalDateTime.now())
            .where(ID.eq(profile.id))
            .execute()

        return findById(profile.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(BRAND_VOICE_PROFILES)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toEntity(): BrandVoiceProfile = BrandVoiceProfile(
        id = get(ID),
        userId = get(USER_ID),
        name = get(NAME),
        tone = get(TONE) ?: "",
        trainStatus = get(TRAIN_STATUS) ?: "PENDING",
        sampleCount = get(SAMPLE_COUNT) ?: 0,
        vocabulary = get(VOCABULARY) ?: "[]",
        avoidWords = get(AVOID_WORDS) ?: "[]",
        emojiUsage = get(EMOJI_USAGE) ?: "NONE",
        avgSentenceLength = get(AVG_SENTENCE_LENGTH) ?: 0,
        signaturePhrase = get(SIGNATURE_PHRASE),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
