package com.ella.music.ui.player

import com.ella.music.data.model.LyricWord
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicLyricSpacingTest {
    @Test
    fun stackedRomanizationAndTranslationShareTheSameGapFromTheOriginal() {
        val above = appleMusicLyricStackedSecondaryPadding(aboveOriginal = true)
        val below = appleMusicLyricStackedSecondaryPadding(aboveOriginal = false)
        assertEquals(AppleMusicLyricSecondaryRowSpacing, above.calculateBottomPadding())
        assertEquals(0.dp, above.calculateTopPadding())
        assertEquals(AppleMusicLyricSecondaryRowSpacing, below.calculateTopPadding())
        assertEquals(0.dp, below.calculateBottomPadding())
        assertEquals(above.calculateBottomPadding(), below.calculateTopPadding())
    }

    @Test
    fun miniLyricsDoNotReserveBlankRowsForInactiveXbg() {
        assertFalse(MINI_LYRICS_RESERVE_EXTRA_LYRIC_SPACE)
    }

    @Test
    fun lineDoubleTapOnlyOwnsTheGestureWhenWordSeekIsDisabled() {
        assertTrue(lyricLineDoubleTapEnabled(wordSeekEnabled = false))
        assertFalse(lyricLineDoubleTapEnabled(wordSeekEnabled = true))
    }

    @Test
    fun disablingWordLiftZeroesLiftButDoesNotDisableKaraokeProgress() {
        assertEquals(
            0f,
            appleMusicKaraokeRestingLiftPx(
                wordLiftEnabled = false, textSizePx = 48f, isActive = true, hasStarted = true
            )
        )
        assertTrue(
            appleMusicKaraokeRestingLiftPx(
                wordLiftEnabled = true, textSizePx = 48f, isActive = true, hasStarted = true
            ) > 0f
        )
    }

    @Test
    fun liftRisesAndHoldsWhileActiveRatherThanBouncingBackDown() {
        // Real Apple Music doesn't bounce each already-sung word back down: a word rises once it
        // starts being sung and holds at that elevation for as long as its line stays active,
        // falling back to baseline only once a new line takes over (isActive becomes false) —
        // not on any fixed timer. A word that hasn't started yet, or whose line isn't the active
        // one, must not be lifted at all.
        assertEquals(
            "not started yet",
            0f,
            appleMusicKaraokeRestingLiftPx(
                wordLiftEnabled = true, textSizePx = 48f, isActive = true, hasStarted = false
            )
        )
        assertEquals(
            "line isn't active, even if this word's own timing has technically started",
            0f,
            appleMusicKaraokeRestingLiftPx(
                wordLiftEnabled = true, textSizePx = 48f, isActive = false, hasStarted = true
            )
        )
        val lifted = appleMusicKaraokeRestingLiftPx(
            wordLiftEnabled = true, textSizePx = 48f, isActive = true, hasStarted = true
        )
        assertTrue("active and started should be lifted", lifted > 0f)
    }

    @Test
    fun characterMotionRespectsPerCharacterStaggerDelay() {
        // Ported from the reference (lyrics.binimum.org / AmLyrics.ts): each character's motion
        // starts only after its own stagger delay. For a 2s/5-char word the per-character delay
        // is 160ms, so at 300ms elapsed the first character (delay 160ms) should already be
        // rising while the last (delay 800ms) hasn't started at all yet.
        val params = AppleMusicCharacterMotionParams(durationSec = 2f, charCount = 5, isCjk = false)
        val firstChar = appleMusicCharacterMotionAt(params, charIndex = 0, elapsedSinceSyllableStartMs = 300L)
        val lastChar = appleMusicCharacterMotionAt(params, charIndex = 4, elapsedSinceSyllableStartMs = 300L)
        assertTrue("the first character should already be rising", firstChar.translateYPx < 0f)
        assertEquals("the last character's delay hasn't elapsed yet", 0f, lastChar.translateYPx)
    }

    @Test
    fun cjkCharactersGetNoEmphasisLiftOrGlowBonus() {
        // The reference zeroes emphasis/lift/glow for CJK entirely regardless of duration —
        // only the base rise curve applies, using a different (underdamped spring) shape too.
        val cjkParams = AppleMusicCharacterMotionParams(durationSec = 3f, charCount = 3, isCjk = true)
        assertEquals(0f, cjkParams.emphasis)
        assertEquals(0f, cjkParams.glowBoost)
        val motion = appleMusicCharacterMotionAt(cjkParams, charIndex = 1, elapsedSinceSyllableStartMs = 900L)
        assertEquals("CJK gets no horizontal spread", 0f, motion.translateXEm)
        assertEquals("CJK gets no scale emphasis", 1f, motion.scale)
    }

    @Test
    fun latinEmphasisOnlyRampsInPastOneSecondOfDuration() {
        val shortWord = AppleMusicCharacterMotionParams(durationSec = 0.6f, charCount = 4, isCjk = false)
        val longHeldWord = AppleMusicCharacterMotionParams(durationSec = 2.5f, charCount = 4, isCjk = false)
        assertEquals("under 1s duration gets no emphasis bonus at all", 0f, shortWord.emphasis)
        assertTrue("a long held word gets real emphasis", longHeldWord.emphasis > 0f)
        assertTrue("emphasis is clamped, not unbounded", longHeldWord.emphasis <= 1f)
    }

    @Test
    fun kanaPronunciationSitsOnTheFirstKanjiWordNotTheLatinPrefix() {
        val words = listOf(
            LyricWord("OK! ", 0L, 400L),
            LyricWord("風が ", 400L, 800L),
            LyricWord("変わっても", 800L, 1_400L)
        )
        assertEquals(
            listOf("", "かぜ", "か"),
            rubiesForTimedWords(words, emptyList(), "かぜがかわっても")
        )
        assertTrue(isInlineRubyPronunciation("かぜが"))
        assertFalse(isInlineRubyPronunciation("ka ku se i READY OK"))
    }

    @Test
    fun kanaRubyMapsOntoMatchingKanjiNotTheFollowingKana() {
        val words = listOf(
            LyricWord("風", 0L, 300L),
            LyricWord("が", 300L, 400L),
            LyricWord("変", 400L, 700L),
            LyricWord("わっても", 700L, 1_400L)
        )
        assertEquals(
            listOf("かぜ", "", "か", ""),
            attachRubyByCorrespondence(words, "かぜがかわっても")
        )
    }

    @Test
    fun consecutiveKanjiSplitReadingsInsteadOfPilingThem() {
        val words = listOf(
            LyricWord("見", 0L, 100L),
            LyricWord("守", 100L, 500L),
            LyricWord("ってくれてるよ", 500L, 1_000L)
        )
        assertEquals(
            listOf("み", "まも", ""),
            attachRubyByCorrespondence(words, "みまも")
        )
        assertEquals(
            listOf("み", "まも", ""),
            attachRubyByCorrespondence(words, "みまもってくれてるよ")
        )
    }

    @Test
    fun appleMusicTimedFuriganaStaysOnEachKanjiSpan() {
        val words = listOf(
            LyricWord("OK! ", 17_601L, 18_114L),
            LyricWord("風", 18_718L, 19_254L),
            LyricWord("が ", 19_254L, 19_459L),
            LyricWord("変", 19_459L, 19_775L),
            LyricWord("わっ", 19_775L, 20_179L),
            LyricWord("て", 20_179L, 20_506L),
            LyricWord("も", 20_506L, 20_913L)
        )
        val ruby = listOf(
            LyricWord("かぜ", 18_718L, 19_254L),
            LyricWord("か", 19_459L, 19_775L)
        )
        assertEquals(
            listOf("", "かぜ", "", "か", "", "", ""),
            rubiesForTimedWords(words, ruby, "かぜか")
        )
    }

    @Test
    fun appleMusicTimedFuriganaDoesNotSwallowTheSecondKanji() {
        val words = listOf(
            LyricWord("みん", 59_292L, 59_493L),
            LyricWord("な", 59_493L, 59_595L),
            LyricWord("を ", 59_595L, 59_806L),
            LyricWord("見", 59_806L, 59_907L),
            LyricWord("守", 59_907L, 60_444L),
            LyricWord("っ", 60_444L, 60_457L),
            LyricWord("てくれてるよ", 60_457L, 63_362L)
        )
        val ruby = listOf(
            LyricWord("み", 59_806L, 59_907L),
            LyricWord("まも", 59_907L, 60_444L)
        )
        assertEquals(
            listOf("", "", "", "み", "まも", "", ""),
            rubiesForTimedWords(words, ruby, "みまも")
        )
    }

    @Test
    fun timedPronunciationWordsStayOnMatchingMainWords() {
        val words = listOf(
            LyricWord("風", 0L, 400L),
            LyricWord("が", 400L, 600L),
            LyricWord("変わって", 600L, 1_000L)
        )
        val ruby = listOf(
            LyricWord("かぜ", 0L, 400L),
            LyricWord("かわ", 600L, 1_000L)
        )
        assertEquals(
            listOf("かぜ", "", "かわ"),
            rubiesForTimedWords(words, ruby, "かぜかわ")
        )
    }

    @Test
    fun leadingWordSpacesMoveToPreviousWordForFlushWrappedRows() {
        val words = listOf(
            LyricWord("It's", 0L, 400L),
            LyricWord(" been", 400L, 800L),
            LyricWord(" a", 800L, 1_000L),
            LyricWord(" long", 1_000L, 1_500L)
        )

        assertEquals(
            listOf("It's ", "been ", "a ", "long"),
            words.moveLeadingSpacesToPreviousWord().map { it.text }
        )
    }

    @Test
    fun trailingPaddingLetsFinalLyricReachTheFocusOffset() {
        assertEquals(
            376.dp,
            resolveAppleMusicLyricsTrailingPadding(
                viewportHeight = 600.dp,
                focusOffsetRatio = 0.24f,
                trailingLineHeight = 80.dp,
                minimumBottomPadding = 132.dp
            )
        )
    }

    @Test
    fun trailingPaddingKeepsTheMinimumBottomInset() {
        assertEquals(
            132.dp,
            resolveAppleMusicLyricsTrailingPadding(
                viewportHeight = 200.dp,
                focusOffsetRatio = 0.24f,
                trailingLineHeight = 100.dp,
                minimumBottomPadding = 132.dp
            )
        )
    }

    @Test
    fun leadingPaddingReservesTheFocusOffsetForTheFirstLyric() {
        assertEquals(
            72.dp,
            resolveAppleMusicLyricsLeadingPadding(
                viewportHeight = 600.dp,
                focusOffsetRatio = 0.12f,
                minimumTopPadding = 8.dp
            )
        )
    }

    @Test
    fun leadingPaddingDoesNotShrinkAnExistingTopInset() {
        assertEquals(
            72.dp,
            resolveAppleMusicLyricsLeadingPadding(
                viewportHeight = 200.dp,
                focusOffsetRatio = 0.12f,
                minimumTopPadding = 72.dp
            )
        )
    }

    @Test
    fun leadingPaddingUsesFixedFocusOffsetWhenProvided() {
        assertEquals(
            110.dp,
            resolveAppleMusicLyricsLeadingPadding(
                viewportHeight = 800.dp,
                focusOffsetRatio = 0.22f,
                minimumTopPadding = 16.dp,
                fixedFocusOffset = 110.dp
            )
        )
    }

    @Test
    fun trailingPaddingUsesFixedFocusOffsetWhenProvided() {
        assertEquals(
            650.dp,
            resolveAppleMusicLyricsTrailingPadding(
                viewportHeight = 800.dp,
                focusOffsetRatio = 0.22f,
                trailingLineHeight = 40.dp,
                minimumBottomPadding = 56.dp,
                fixedFocusOffset = 110.dp
            )
        )
    }

    @Test
    fun focusOffsetIsClampedWhenLyricRowIsTallerThanTheViewport() {
        assertEquals(
            0,
            resolveAppleMusicLyricsFocusOffset(
                viewportHeightPx = 240,
                focusOffsetRatio = 0.12f,
                itemHeightPx = 300
            )
        )
    }

    @Test
    fun focusOffsetUsesThePreferredPositionForNormalRows() {
        assertEquals(
            144,
            resolveAppleMusicLyricsFocusOffset(
                viewportHeightPx = 600,
                focusOffsetRatio = 0.24f,
                itemHeightPx = 80
            )
        )
    }

    @Test
    fun initialScrollTargetStartsAtTheCurrentLyricInsteadOfTheFirstRow() {
        assertEquals(
            18,
            resolveAppleMusicLyricsScrollTargetIndex(
                activeLyricIndex = 18,
                activeInterlude = null,
                interludes = emptyList()
            )
        )
    }

    @Test
    fun scrollTargetAccountsForInsertedInterludeRows() {
        val interludes = listOf(
            AppleMusicInterlude(startMs = 0L, endMs = 8_000L, nextLineIndex = 0),
            AppleMusicInterlude(startMs = 40_000L, endMs = 50_000L, nextLineIndex = 5)
        )

        assertEquals(
            7,
            resolveAppleMusicLyricsScrollTargetIndex(
                activeLyricIndex = 5,
                activeInterlude = null,
                interludes = interludes
            )
        )
        assertEquals(
            6,
            resolveAppleMusicLyricsScrollTargetIndex(
                activeLyricIndex = 4,
                activeInterlude = interludes[1],
                interludes = interludes
            )
        )
    }

    @Test
    fun waitingDotsUseAppleGroupExitBeforeTheNextLyric() {
        val interlude = AppleMusicInterlude(startMs = 10_000L, endMs = 20_000L, nextLineIndex = 2)

        assertEquals(1f, resolveAppleMusicInterludeGroupState(interlude, positionMs = 19_000L).scale)
        assertEquals(1.2f, resolveAppleMusicInterludeGroupState(interlude, positionMs = 19_750L).scale)
        assertEquals(0.5f, resolveAppleMusicInterludeGroupState(interlude, positionMs = 20_000L).scale)
        assertEquals(0f, resolveAppleMusicInterludeGroupState(interlude, positionMs = 20_000L).alpha)
    }

    @Test
    fun waitingDotsBreatheAsOneAppleStyleGroup() {
        val interlude = AppleMusicInterlude(startMs = 10_000L, endMs = 20_000L, nextLineIndex = 2)

        assertEquals(1f, resolveAppleMusicInterludeGroupState(interlude, positionMs = 10_000L).scale)
        assertEquals(1.2f, resolveAppleMusicInterludeGroupState(interlude, positionMs = 12_000L).scale)
        assertEquals(1f, resolveAppleMusicInterludeGroupState(interlude, positionMs = 14_000L).scale)
    }

    @Test
    fun waitingDotsRetireSequentiallyFromPlaybackTime() {
        val interlude = AppleMusicInterlude(startMs = 0L, endMs = 9_000L, nextLineIndex = 0)

        assertEquals(1f, resolveAppleMusicInterludeDotAlpha(interlude, 0L, 0), 0.001f)
        assertEquals(0f, resolveAppleMusicInterludeDotAlpha(interlude, 3_250L, 0), 0.001f)
        assertEquals(1f, resolveAppleMusicInterludeDotAlpha(interlude, 3_250L, 1), 0.001f)
        assertEquals(0f, resolveAppleMusicInterludeDotAlpha(interlude, 6_500L, 1), 0.001f)
        assertEquals(1f, resolveAppleMusicInterludeDotAlpha(interlude, 6_500L, 2), 0.001f)
        assertEquals(0f, resolveAppleMusicInterludeDotAlpha(interlude, 9_000L, 2), 0.001f)
    }

    @Test
    fun waitingDotsRetireFromRightToLeftInVisualOrder() {
        assertEquals(2, resolveAppleMusicInterludeDotTimelineIndex(0))
        assertEquals(1, resolveAppleMusicInterludeDotTimelineIndex(1))
        assertEquals(0, resolveAppleMusicInterludeDotTimelineIndex(2))
    }

    @Test
    fun longCjkTimedPhraseIsSplitForSequentialWrappedRows() {
        assertTrue(
            LyricWord("星空下拥抱着快凋零的温存", 0L, 4_000L)
                .shouldSplitForAppleMusicCharacters()
        )
        assertFalse(
            LyricWord("星空", 0L, 800L)
                .shouldSplitForAppleMusicCharacters()
        )
    }

    @Test
    fun englishWordsAreNeverSplitIntoCharacters() {
        assertFalse(
            LyricWord("stranger", 0L, 4_000L)
                .shouldSplitForAppleMusicCharacters()
        )
        assertFalse(
            LyricWord("falling in love in stranger", 0L, 4_000L)
                .shouldSplitForAppleMusicCharacters()
        )
    }

    @Test
    fun minorPlaybackRegressionIsIgnoredButSeekJumpIsAccepted() {
        assertTrue(
            shouldIgnoreMinorPlaybackRegression(
                currentUiPositionMs = 2_000L,
                nextPositionMs = 1_700L,
                isPlaying = true
            )
        )
        assertFalse(
            shouldIgnoreMinorPlaybackRegression(
                currentUiPositionMs = 2_000L,
                nextPositionMs = 800L,
                isPlaying = true
            )
        )
    }

    @Test
    fun timedWordsWithPronunciationWordsMapDirectly() {
        val words = listOf(
            LyricWord("なんで", 0L, 500L),
            LyricWord("少しだけ", 500L, 1_000L),
            LyricWord("夢をみた", 1_000L, 2_000L)
        )
        val pronunciationWords = listOf(
            LyricWord("nante", 0L, 500L),
            LyricWord("sukoshi dake", 500L, 1_000L),
            LyricWord("yumeo mita", 1_000L, 2_000L)
        )
        val rubies = rubiesForTimedWords(words, pronunciationWords, "")
        assertEquals(listOf("nante", "sukoshi dake", "yumeo mita"), rubies)
    }

    @Test
    fun lineRomanizationIsNotInlineRuby() {
        assertFalse(isInlineRubyPronunciation("mou bo ku wa o to na ni na 't te"))
        assertFalse(isInlineRubyPronunciation("ni hao wo de peng you"))
        assertFalse(isInlineRubyPronunciation("ka ku se i READY OK"))
        assertTrue(isInlineRubyPronunciation("かぜがかわっても"))
    }
}
