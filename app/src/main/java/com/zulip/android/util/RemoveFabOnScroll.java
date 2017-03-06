package com.zulip.android.util;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v7.widget.LinearLayoutManager;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;

import com.zulip.android.R;
import com.zulip.android.activities.RecyclerMessageAdapter;

import java.util.List;

/**
 * This hides the {@link android.support.design.widget.FloatingActionButton} when the
 * recyclerView is scrolled, used in here {@link com.zulip.android.R.layout#main} as a behaviour.
 * This also shrinks the {@link android.support.design.widget.FloatingActionButton} when the snackbar comes
 * and goes in and out of view.
 */


public class RemoveFabOnScroll extends CoordinatorLayout.Behavior<FloatingActionButton> {

    private static final Interpolator FAST_OUT_SLOW_IN_INTERPOLATOR = new FastOutSlowInInterpolator();
    private static float toolbarHeight;
    private int changeInYDir;
    private boolean mIsShowing;
    private boolean isViewHidden;
    private View chatBox;
    private LinearLayoutManager linearLayoutManager;
    private RecyclerMessageAdapter adapter;

    public RemoveFabOnScroll(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true))
            toolbarHeight = TypedValue.complexToDimensionPixelSize(tv.data, context.getResources().getDisplayMetrics());
    }

    public RemoveFabOnScroll(LinearLayoutManager linearLayoutManager, RecyclerMessageAdapter adapter) {
        this.linearLayoutManager = linearLayoutManager;
        this.adapter = adapter;
    }

    @Override
    public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout, FloatingActionButton child, View directTargetChild, View target, int nestedScrollAxes) {
        return (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @SuppressLint("NewApi")
    @Override
    public void onNestedPreScroll(CoordinatorLayout coordinatorLayout, FloatingActionButton child, View target, int dx, int dy, int[] consumed) throws NullPointerException {
        //count index starts from 1 where as position starts from 0, thus difference 1
        //we have 2 loading layouts one at top and another at bottom of the messages which should be ignored
        //resulting in a overall difference of 3
        if (linearLayoutManager.findLastCompletelyVisibleItemPosition() < adapter.getItemCount() - 3) {
            if (dy > 0 && changeInYDir < 0 || dy < 0 && changeInYDir > 0) {
                child.animate().cancel();
                changeInYDir = 0;
            }

            changeInYDir += dy;
            if ((changeInYDir > toolbarHeight && child.getVisibility() == View.VISIBLE) && (!isViewHidden || isTopSnackBar(child)))
                hideView(child);
            else if (changeInYDir < 0 && (child.getVisibility() == View.GONE && !mIsShowing) || isTopSnackBar(child)) {
                if (chatBox == null)
                    chatBox = coordinatorLayout.findViewById(R.id.messageBoxContainer);
                if (chatBox.getVisibility() == View.VISIBLE) {
                    return;
                }
                showView(child);
            }
        }
    }

    private boolean isTopSnackBar(View child) {
        return (child.getId() != R.id.appBarLayout && !(child instanceof FloatingActionButton));
    }

    @SuppressLint("NewApi")
    private void hideView(final View view) {
        isViewHidden = true;
        int y = view.getHeight();
        ;
        if (view instanceof AppBarLayout) {
            y = -1 * view.getHeight();
        } else if (view instanceof LinearLayout) {
            y = 0;
        }
        ViewPropertyAnimator animator = view.animate()
                .translationY(y)
                .setInterpolator(FAST_OUT_SLOW_IN_INTERPOLATOR)
                .setDuration(200);

        animator.setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                isViewHidden = false;
                view.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                isViewHidden = false;
                if (!mIsShowing)
                    showView(view);
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }
        });
        animator.start();
    }

    @SuppressLint("NewApi")
    public void showView(final View view) {
        mIsShowing = true;
        ViewPropertyAnimator animator = view.animate()
                .translationY((view.getId() == R.id.appBarLayout || view instanceof FloatingActionButton) ? 0 : toolbarHeight)
                .setInterpolator(FAST_OUT_SLOW_IN_INTERPOLATOR)
                .setDuration(200);

        animator.setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                mIsShowing = false;
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                mIsShowing = false;
                if (!isViewHidden)
                    hideView(view);
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }
        });
        animator.start();
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, FloatingActionButton child, View dependency) {
        return dependency instanceof Snackbar.SnackbarLayout;
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, FloatingActionButton child, View dependency) {
        float translationY = getFabTranslationYForSnackbar(parent, child);
        float percentComplete = -translationY / dependency.getHeight();
        float scaleFactor = 1 - percentComplete;

        child.setScaleX(scaleFactor);
        child.setScaleY(scaleFactor);
        return false;
    }

    private float getFabTranslationYForSnackbar(CoordinatorLayout parent,
                                                FloatingActionButton fab) {
        float minOffset = 0;
        final List<View> dependencies = parent.getDependencies(fab);
        for (int i = 0, z = dependencies.size(); i < z; i++) {
            final View view = dependencies.get(i);
            if (view instanceof Snackbar.SnackbarLayout && parent.doViewsOverlap(fab, view)) {
                minOffset = Math.min(minOffset,
                        ViewCompat.getTranslationY(view) - view.getHeight());
            }
        }

        return minOffset;
    }

}