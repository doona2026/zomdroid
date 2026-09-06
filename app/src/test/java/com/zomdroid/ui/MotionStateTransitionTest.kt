package com.zomdroid.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MotionStateTransitionTest {
    @Test
    fun repeatedStateDoesNotRequestAnotherCrossfade() {
        assertThat(MotionAnimations.shouldCrossfade(false, false)).isFalse()
        assertThat(MotionAnimations.shouldCrossfade(true, true)).isFalse()
    }

    @Test
    fun loadingAndContentOrEmptyAndContentRequestOneCrossfade() {
        assertThat(MotionAnimations.shouldCrossfade(false, true)).isTrue()
        assertThat(MotionAnimations.shouldCrossfade(true, false)).isTrue()
    }
}
