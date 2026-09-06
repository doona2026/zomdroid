package com.zomdroid.ui;

import android.view.View;
import android.view.ViewGroup;

import androidx.navigation.NavOptions;

import com.zomdroid.R;

/**
 * Shared, deliberately small animation policy for launcher UI surfaces.
 *
 * <p>Business and navigation state stay in the callers. This class only owns motion resources,
 * timing, and safe cancellation so individual screens do not grow their own animation dialect.</p>
 */
public final class MotionAnimations {
    static final long FAST_DURATION_MS = 160L;
    static final long STANDARD_DURATION_MS = 220L;
    static final long EMPHASIS_DURATION_MS = 280L;
    static final long PAGE_ENTER_DURATION_MS = 220L;
    static final long PAGE_EXIT_DURATION_MS = 180L;
    static final String PAGE_OFFSET_PERCENT = "8%";
    static final long LIST_STAGGER_MS = 24L;
    static final int MAX_STAGGERED_ITEMS = 6;

    private MotionAnimations() {
    }

    public static NavOptions forwardNavOptions() {
        return new NavOptions.Builder()
                .setEnterAnim(R.anim.motion_forward_enter)
                .setExitAnim(R.anim.motion_forward_exit)
                .setPopEnterAnim(R.anim.motion_back_enter)
                .setPopExitAnim(R.anim.motion_back_exit)
                .build();
    }

    public static void animateContentEnter(View view, long delayMs) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;

        view.animate().cancel();
        view.setAlpha(0f);
        view.setTranslationY(view.getResources().getDimension(R.dimen.motion_content_offset_dp));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(Math.max(0L, delayMs))
                .setDuration(view.getResources().getInteger(R.integer.motion_standard))
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    public static void animateFirstVisibleChildren(ViewGroup parent) {
        if (parent == null || Boolean.TRUE.equals(parent.getTag(R.id.motion_content_animated))) return;

        int visibleCount = 0;
        for (int i = 0; i < parent.getChildCount() && visibleCount < MAX_STAGGERED_ITEMS; i++) {
            View child = parent.getChildAt(i);
            if (child != null && child.getVisibility() == View.VISIBLE) {
                visibleCount++;
            }
        }
        if (visibleCount == 0) return;

        parent.setTag(R.id.motion_content_animated, Boolean.TRUE);
        int animatedCount = 0;
        for (int i = 0; i < parent.getChildCount() && animatedCount < MAX_STAGGERED_ITEMS; i++) {
            View child = parent.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            animateContentEnter(child, listDelayForIndex(animatedCount));
            animatedCount++;
        }
    }

    public static void crossfade(View outgoing, View incoming) {
        if (incoming == null) return;
        if (outgoing == incoming) {
            incoming.animate().cancel();
            incoming.setVisibility(View.VISIBLE);
            incoming.setAlpha(1f);
            return;
        }

        final long duration = incoming.getResources().getInteger(R.integer.motion_standard);
        if (outgoing != null) outgoing.animate().cancel();
        incoming.animate().cancel();
        incoming.setVisibility(View.VISIBLE);
        incoming.setAlpha(0f);
        incoming.animate()
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        if (outgoing != null && outgoing.getVisibility() == View.VISIBLE) {
            outgoing.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> {
                        outgoing.setVisibility(View.GONE);
                        outgoing.setAlpha(1f);
                    })
                    .start();
        }
    }

    public static boolean shouldCrossfade(boolean previousState, boolean nextState) {
        return previousState != nextState;
    }

    public static void setExpanded(View content, boolean expanded) {
        if (content == null) return;
        if (expanded && content.getVisibility() == View.VISIBLE && content.getAlpha() == 1f) return;
        if (!expanded && content.getVisibility() != View.VISIBLE) return;

        final float offset = content.getResources().getDimension(R.dimen.motion_content_offset_dp);
        final long duration = content.getResources().getInteger(R.integer.motion_standard);
        content.animate().cancel();

        if (expanded) {
            content.setVisibility(View.VISIBLE);
            content.setAlpha(0f);
            content.setTranslationY(-offset);
            content.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(duration)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        } else {
            content.animate()
                    .alpha(0f)
                    .translationY(-offset)
                    .setDuration(duration)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> {
                        content.setVisibility(View.GONE);
                        content.setAlpha(1f);
                        content.setTranslationY(0f);
                    })
                    .start();
        }
    }

    public static void cancel(View... views) {
        if (views == null) return;
        for (View view : views) {
            if (view == null) continue;
            view.animate().cancel();
            view.clearAnimation();
        }
    }

    static long listDelayForIndex(int index) {
        if (index < 0 || index >= MAX_STAGGERED_ITEMS) return 0L;
        return index * LIST_STAGGER_MS;
    }
}
