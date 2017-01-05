package com.zulip.android.viewholders;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.View;

import com.zulip.android.activities.RecyclerMessageAdapter;

/**
 * An {@link android.support.v7.widget.RecyclerView.ItemDecoration} for the recyclerView
 * which adds margin to every MessageHeader.
 */
public class HeaderSpaceItemDecoration extends RecyclerView.ItemDecoration {

    private int toolbarHeight;

    public HeaderSpaceItemDecoration(Context context) {
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            toolbarHeight = TypedValue.complexToDimensionPixelSize(tv.data, context.getResources().getDisplayMetrics());
        }
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                               RecyclerView.State state) {

        int position = parent.getChildAdapterPosition(view);
        int size = parent.getAdapter().getItemCount();
        int viewType = parent.getAdapter().getItemViewType(position);
        if (viewType == RecyclerMessageAdapter.VIEWTYPE_HEADER) {
            outRect.top = toolbarHeight;
        }
        outRect.bottom = 0;
    }
}
