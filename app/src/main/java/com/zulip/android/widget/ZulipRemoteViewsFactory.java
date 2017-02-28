package com.zulip.android.widget;

import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.j256.ormlite.stmt.QueryBuilder;
import com.zulip.android.R;
import com.zulip.android.ZulipApp;
import com.zulip.android.filters.NarrowFilter;
import com.zulip.android.filters.NarrowFilterByDate;
import com.zulip.android.models.Message;
import com.zulip.android.models.MessageType;
import com.zulip.android.util.ZLog;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

import static com.zulip.android.widget.WidgetPreferenceFragment.FROM_PREFERENCE;

public class ZulipRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context context;
    private List<Message> messageList;
    private String from;

    public ZulipRemoteViewsFactory(Context applicationContext, Intent intent) {
        context = applicationContext;
        from = intent.getStringExtra(FROM_PREFERENCE);
    }

    @Override
    public void onCreate() {

    }

    private Calendar getMinDate() {
        Calendar calendar = Calendar.getInstance();
        switch (from) {
            //These values present in R.arrays.from_values
            case "today":
                break;
            case "yesterday":
                calendar.add(Calendar.DATE, -1);
                break;
            case "week":
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                break;
            case "all":
            default:
                calendar = null;
        }
        return calendar;
    }

    @Override
    public void onDataSetChanged() {
        try {
            Log.i("ZULIP_WIDGET", "onDataSetChanged() = Data reloaded");
            QueryBuilder<Message, Object> queryBuilder = ZulipApp.get().getDao(Message.class).queryBuilder();
            Calendar minDate = getMinDate();
            if (minDate != null) {
                NarrowFilter minDateFilter = new NarrowFilterByDate(minDate.getTime(), true);
                minDateFilter.modWhere(queryBuilder.where());
            }
            messageList = queryBuilder.query();
        } catch (SQLException e) {
            ZLog.logException(e);
        }
    }

    @Override
    public void onDestroy() {

    }

    public int getCount() {
        return messageList.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        RemoteViews remoteView = new RemoteViews(context.getPackageName(), R.layout.widget_row);
        Message message = messageList.get(position);
        if (message.getType() == MessageType.STREAM_MESSAGE) {
            remoteView.setTextViewText(R.id.widget_header, message.getStream().getName() + " > " + message.getSubject());
        } else {
            remoteView.setTextViewText(R.id.widget_header, message.getDisplayRecipient(ZulipApp.get()));
        }
        remoteView.setTextViewText(R.id.widget_sendername, message.getSender().getName());
        remoteView.setTextViewText(R.id.widget_message, message.getFormattedContent(ZulipApp.get()));

        if (from.equals("today")) {
            remoteView.setTextViewText(R.id.widget_timestamp, DateUtils.formatDateTime(context, message.getTimestamp().getTime(), DateUtils.FORMAT_SHOW_TIME));
        } else {
            remoteView.setTextViewText(R.id.widget_timestamp, DateUtils.formatDateTime(context, message
                    .getTimestamp().getTime(), DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_ABBREV_MONTH
                    | DateUtils.FORMAT_SHOW_TIME));
        }
        return remoteView;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
