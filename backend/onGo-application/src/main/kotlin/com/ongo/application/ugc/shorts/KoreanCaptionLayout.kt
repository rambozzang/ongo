package com.ongo.application.ugc.shorts

/** 자막 한 단어(어절). 시각은 클립 시작 기준 ms. */
data class CaptionWord(val startMs: Long, val endMs: Long, val text: String)

/**
 * 화면에 한 번에 뜨는 자막 묶음. [lines] 는 최대 두 줄, 각 줄은 어절 목록이다.
 * [timed] 가 false 면 어절 시각은 글자 수로 나눈 **추정값**이라 단어 강조에 쓰지 않는다.
 */
data class CaptionCard(
    val startMs: Long,
    val endMs: Long,
    val lines: List<List<CaptionWord>>,
    val timed: Boolean,
) {
    val words: List<CaptionWord> get() = lines.flatten()
}

/**
 * 한국어 쇼츠 자막 배치.
 *
 * 예전에는 전사 문장 하나를 그대로 한 자막으로 띄웠다 — 한 화면에 30자가 넘는 문장이 3~4줄로
 * 쌓이고, 말보다 먼저 다음 내용이 보였다. 쇼츠는 **짧은 묶음이 말과 함께 바뀌어야** 읽힌다.
 *
 * 규칙:
 * - 어절(띄어쓰기) 경계에서만 끊는다. 단어 중간에서 줄이 갈리지 않는다
 * - 한 묶음은 [MAX_CARD_CHARS] 자 이하, [MAX_CARD_MS] 이하. 문장 끝(.?!…)이나 말 사이 쉼([PAUSE_MS] 이상)에서 새 묶음
 * - 한 줄이 [MAX_LINE_CHARS] 자를 넘으면 가운데에 가까운 어절 경계에서 두 줄로 나눈다
 * - 화면에 너무 짧게 떴다 사라지지 않도록 묶음은 최소 [MIN_CARD_MS] 동안 보인다(다음 묶음 시작 전까지만)
 */
object KoreanCaptionLayout {

    const val MAX_CARD_CHARS = 16
    const val MAX_LINE_CHARS = 9
    const val MAX_CARD_MS = 2_600L
    const val PAUSE_MS = 600L
    const val MIN_CARD_MS = 500L

    private val SENTENCE_END = Regex("[.?!…。？！~]$")

    /** 단어 시각이 있는 전사. */
    fun fromWords(words: List<CaptionWord>, clipDurationMs: Long): List<CaptionCard> =
        group(clean(words), clipDurationMs, timed = true)

    /**
     * 단어 시각이 없는 줄(예전 전사, 사용자가 고친 줄). 줄 안에서 글자 수에 비례해 시각을 나눈다.
     * 강조는 하지 않는다 — 추정 시각으로 단어를 칠하면 말과 어긋나 보인다.
     */
    fun fromLines(lines: List<Pair<LongRange, String>>, clipDurationMs: Long): List<CaptionCard> {
        val estimated = lines.flatMap { (range, text) -> spread(range.first, range.last, text) }
        return group(clean(estimated), clipDurationMs, timed = false)
    }

    /** 공백만 있는 단어를 버리고 시각을 클립 안으로 묶고 시각순으로 둔다. */
    private fun clean(words: List<CaptionWord>): List<CaptionWord> =
        words.mapNotNull { w ->
            val text = w.text.trim().replace(Regex("\\s+"), " ")
            if (text.isEmpty() || w.endMs <= w.startMs) null else w.copy(text = text, startMs = w.startMs.coerceAtLeast(0))
        }.sortedBy { it.startMs }

    private fun spread(start: Long, end: Long, text: String): List<CaptionWord> {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty() || end <= start) return emptyList()
        val total = tokens.sumOf { weight(it) }.toDouble()
        var cursor = start.toDouble()
        return tokens.map { token ->
            val span = (end - start) * weight(token) / total
            val w = CaptionWord(cursor.toLong(), (cursor + span).toLong().coerceAtLeast(cursor.toLong() + 1), token)
            cursor += span
            w
        }
    }

    private fun weight(token: String): Int = displayLength(token).coerceAtLeast(1)

    private fun group(words: List<CaptionWord>, clipDurationMs: Long, timed: Boolean): List<CaptionCard> {
        val cards = mutableListOf<List<CaptionWord>>()
        var current = mutableListOf<CaptionWord>()
        words.forEach { w ->
            val prev = current.lastOrNull()
            val breakHere = prev != null && (
                charsOf(current) + 1 + displayLength(w.text) > MAX_CARD_CHARS ||
                    w.startMs - prev.endMs >= PAUSE_MS ||
                    w.endMs - current.first().startMs > MAX_CARD_MS ||
                    SENTENCE_END.containsMatchIn(prev.text)
                )
            if (breakHere) {
                cards += current
                current = mutableListOf()
            }
            current += w
        }
        if (current.isNotEmpty()) cards += current

        return cards.mapIndexed { i, cardWords ->
            val nextStart = cards.getOrNull(i + 1)?.first()?.startMs ?: clipDurationMs
            val start = cardWords.first().startMs
            val end = maxOf(cardWords.last().endMs, minOf(start + MIN_CARD_MS, nextStart)).coerceAtMost(clipDurationMs)
            CaptionCard(start, end, splitLines(cardWords), timed)
        }.filter { it.endMs > it.startMs }
    }

    /** 한 줄에 넘치면 가운데에 가장 가까운 어절 경계에서 두 줄로 나눈다. */
    private fun splitLines(words: List<CaptionWord>): List<List<CaptionWord>> {
        if (words.size < 2 || charsOf(words) <= MAX_LINE_CHARS) return listOf(words)
        val total = charsOf(words)
        val cut = (1 until words.size).minBy { k -> kotlin.math.abs(charsOf(words.subList(0, k)) - (total - charsOf(words.subList(0, k)))) }
        return listOf(words.subList(0, cut), words.subList(cut, words.size))
    }

    /** 띄어쓰기를 포함한 표시 길이. */
    private fun charsOf(words: List<CaptionWord>): Int =
        words.sumOf { displayLength(it.text) } + (words.size - 1).coerceAtLeast(0)

    /** 화면 폭 기준 길이. 한글·한자는 1, 영문·숫자·기호는 반 칸으로 센다. */
    fun displayLength(text: String): Int {
        var half = 0
        text.forEach { c -> half += if (c.code < 0x1100) 1 else 2 }
        return (half + 1) / 2
    }
}
