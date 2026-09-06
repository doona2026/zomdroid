package com.zomdroid.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MotionAnimationsPolicyTest {
    @Test
    fun motionDurationsUseTheThreeDefinedTiers() {
        assertThat(MotionAnimations.FAST_DURATION_MS).isEqualTo(160L)
        assertThat(MotionAnimations.STANDARD_DURATION_MS).isEqualTo(220L)
        assertThat(MotionAnimations.EMPHASIS_DURATION_MS).isEqualTo(280L)
    }

    @Test
    fun pageTransitionKeepsTheOriginalWorkshopCadence() {
        assertThat(MotionAnimations.PAGE_ENTER_DURATION_MS).isEqualTo(220L)
        assertThat(MotionAnimations.PAGE_EXIT_DURATION_MS).isEqualTo(180L)
        assertThat(MotionAnimations.PAGE_OFFSET_PERCENT).isEqualTo("8%")
    }

    @Test
    fun listStaggerIsBoundedAndIncreasesByTheSharedInterval() {
        assertThat(MotionAnimations.listDelayForIndex(0)).isEqualTo(0L)
        assertThat(MotionAnimations.listDelayForIndex(1)).isEqualTo(24L)
        assertThat(MotionAnimations.listDelayForIndex(5)).isEqualTo(120L)
        assertThat(MotionAnimations.listDelayForIndex(6)).isEqualTo(0L)
        assertThat(MotionAnimations.listDelayForIndex(-1)).isEqualTo(0L)
    }

    @Test
    fun firstPageAnimationHasAtMostSixItems() {
        assertThat(MotionAnimations.MAX_STAGGERED_ITEMS).isEqualTo(6)
    }
}
