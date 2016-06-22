package com.zulip.android.networking;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;

import org.apache.commons.lang.time.StopWatch;
import org.apache.http.client.HttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.SystemClock;
import android.util.Log;

import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.misc.TransactionManager;
import com.zulip.android.models.Message;
import com.zulip.android.models.MessageRange;
import com.zulip.android.models.Person;
import com.zulip.android.models.Stream;
import com.zulip.android.util.ZLog;
import com.zulip.android.activities.ZulipActivity;
import com.zulip.android.ZulipApp;

public class AsyncGetEvents extends Thread {
    private static final String TAG = "AsyncGetEvents";
    public static final String ASYNC_GET_EVENTS = "asyncGetEvents";
    public static final String POINTER = "pointer";
    ZulipActivity activity;
    ZulipApp app;

    HTTPRequest request;

    AsyncGetEvents that = this;
    int failures = 0;
    boolean registeredOrGotEventsThisRun;

    public AsyncGetEvents(ZulipActivity humbugActivity) {
        super();
        app = (ZulipApp) humbugActivity.getApplication();
        activity = humbugActivity;
        request = new HTTPRequest(app);
    }

    public void start() {
        registeredOrGotEventsThisRun = false;
        super.start();
    }

    public void abort() {
        // TODO: does this have race conditions? (if the thread is not in a
        // request when called)
        Log.i(ASYNC_GET_EVENTS, "Interrupting thread");
        request.abort();
    }

    private void backoff(Exception e) {
        if (e != null) {
            ZLog.logException(e);
        }
        failures += 1;
        long backoff = (long) (Math.exp(failures / 2.0) * 1000);
        Log.e(ASYNC_GET_EVENTS, "Failure " + failures + ", sleeping for "
                + backoff);
        SystemClock.sleep(backoff);
    }

    /**
     * Registers for a new event queue with the Zulip API
     */
    private void register() throws JSONException, IOException {
        request.setProperty("apply_markdown", "true");

        StopWatch watch = new StopWatch();
        watch.start();
        String responseData = request.execute("POST", "v1/register");
        watch.stop();
        Log.i("perf", "net: v1/register: " + watch.toString());

        watch.reset();
        watch.start();
        JSONObject response = new JSONObject(responseData);
        watch.stop();
        Log.i("perf", "json: v1/register: " + watch.toString());

        registeredOrGotEventsThisRun = true;
        app.setEventQueueId(response.getString("queue_id"));
        app.setLastEventId(response.getInt("last_event_id"));

        processRegister(response);
    }

    public void run() {
        try {
            while (true) {
                try {
                    request.clearProperties();
                    if (app.getEventQueueId() == null) {
                        register();
                    }
                    request.setProperty("queue_id", app.getEventQueueId());
                    request.setProperty("last_event_id",
                            "" + app.getLastEventId());
                    if (!registeredOrGotEventsThisRun) {
                        request.setProperty("dont_block", "true");
                    }
                    JSONObject response = new JSONObject(request.execute("GET",
                            "v1/events"));

                    JSONArray events = response.getJSONArray("events");
                    if (events.length() > 0) {
                        this.processEvents(events);

                        JSONObject lastEvent = events.getJSONObject(events
                                .length() - 1);
                        app.setLastEventId(lastEvent.getInt("id"));

                        failures = 0;
                    }

                    if (!registeredOrGotEventsThisRun) {
                        registeredOrGotEventsThisRun = true;
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                activity.onReadyToDisplay(false);
                            }
                        });
                    }
                } catch (HttpResponseException e) {
                    if (e.getStatusCode() == 400) {
                        String msg = e.getMessage();
                        if (msg.contains("Bad event queue id")
                                || msg.contains("too old")) {
                            // Queue dead. Register again.
                            Log.w(ASYNC_GET_EVENTS, "Queue dead");
                            app.setEventQueueId(null);
                            continue;
                        }
                    }
                    backoff(e);
                } catch (SocketTimeoutException e) {
                    Log.e(TAG, e.getMessage(), e);
                    // Retry without backoff, since it's already been a while
                } catch (IOException e) {
                    if (request.aborting) {
                        Log.i(ASYNC_GET_EVENTS, "Thread aborted");
                        return;
                    } else {
                        backoff(e);
                    }
                } catch (JSONException e) {
                    backoff(e);
                }
            }
        } catch (Exception e) {
            ZLog.logException(e);
        }
    }

    protected void processRegister(final JSONObject response) {
        // In task thread
        try {
            app.setPointer(response.getInt(POINTER));
            app.setMaxMessageId(response.getInt("max_message_id"));

            StopWatch watch = new StopWatch();
            watch.start();
            Message.trim(5000, this.app);
            watch.stop();
            Log.i("perf", "trim: " + watch.toString());

            watch.reset();
            watch.start();
            TransactionManager.callInTransaction(app.getDatabaseHelper()
                    .getConnectionSource(), new Callable<Void>() {
                public Void call() throws Exception {

                    // Get subscriptions
                    JSONArray subscriptions = response
                            .getJSONArray("subscriptions");
                    JSONObject jsonObject = new JSONObject(response.toString());
                    JSONArray mutedTopics = jsonObject.getJSONArray("muted_topics");
                    app.addToMutedTopics(mutedTopics);
                    RuntimeExceptionDao<Stream, Object> streamDao = app
                            .getDao(Stream.class);
                    Log.i("stream", "" + subscriptions.length() + " streams");

                    // Mark all existing subscriptions as not subscribed
                    streamDao.updateBuilder().updateColumnValue(
                            Stream.SUBSCRIBED_FIELD, false);

                    for (int i = 0; i < subscriptions.length(); i++) {
                        Stream stream = Stream.getFromJSON(app,
                                subscriptions.getJSONObject(i));
                        stream.setSubscribed(true);
                        streamDao.createOrUpdate(stream);
                    }

                    // Get people
                    JSONArray people = response.getJSONArray("realm_users");
                    RuntimeExceptionDao<Person, Object> personDao = app
                            .getDao(Person.class);

                    // Mark all existing people as inactive
                    personDao.updateBuilder().updateColumnValue(
                            Person.ISACTIVE_FIELD, false);

                    for (int i = 0; i < people.length(); i++) {
                        Person person = Person.getFromJSON(app,
                                people.getJSONObject(i));
                        person.setActive(true);
                        personDao.createOrUpdate(person);
                    }
                    return null;
                }
            });
            watch.stop();
            Log.i("perf", "DB people and streams: " + watch.toString());

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    that.activity.getPeopleAdapter().refresh();
                    that.activity.getStreamsAdapter().refresh();
                    activity.onReadyToDisplay(true);
                }
            });
        } catch (JSONException e) {
            ZLog.logException(e);
        } catch (SQLException e) {
            ZLog.logException(e);
        }
    }

    /**
     * Handles any event returned by the server that we care about.
     */
    protected void processEvents(JSONArray events) {
        // In task thread
        try {
            StopWatch watch = new StopWatch();
            watch.start();

            ArrayList<Message> messages = new ArrayList<Message>();
            HashMap<String, Person> personCache = new HashMap<String, Person>();
            HashMap<String, Stream> streamCache = new HashMap<String, Stream>();

            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);
                String type = event.getString("type");

                if (type.equals("message")) {
                    Message message = new Message(this.app,
                            event.getJSONObject("message"), personCache,
                            streamCache);
                    messages.add(message);
                } else if (type.equals(POINTER)) {
                    // Keep our pointer synced with global pointer
                    app.setPointer(event.getInt(POINTER));
                }
            }

            watch.stop();
            Log.i("perf", "Processing events: " + watch.toString());

            watch.reset();
            watch.start();

            if (!messages.isEmpty()) {
                Log.i("AsyncGetEvents", "Received " + messages.size()
                        + " messages");
                Message.createMessages(app, messages);
                processMessages(messages);
            }

            watch.stop();
            Log.i("perf", "Inserting event messages: " + watch.toString());

        } catch (JSONException e) {
            ZLog.logException(e);
        }
    }

    protected void processMessages(final ArrayList<Message> messages) {
        // In task thread
        int lastMessageId = messages.get(messages.size() - 1).getID();
        MessageRange.updateNewMessagesRange(app, lastMessageId);

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.onNewMessages(messages.toArray(new Message[messages.size()]));
            }
        });
    }
}
