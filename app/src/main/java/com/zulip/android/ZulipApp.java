package com.zulip.android;

import java.sql.SQLException;
import java.util.Map;
import java.util.Queue;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.RuntimeExceptionDao;

import org.json.JSONArray;
import org.json.JSONException;

public class ZulipApp extends Application {
    public static final String API_KEY = "api_key";
    public static final String EMAIL = "email";
    private static ZulipApp instance;
    private static final String USER_AGENT = "ZulipMobile";
    Person you;
    SharedPreferences settings;
    String api_key;
    private int max_message_id;
    DatabaseHelper databaseHelper;
    Set<String> mutedTopics;
    private static final String MUTED_TOPIC_KEY = "mutedTopics";

    /**
     * Handler to manage batching of unread messages
     */
    private Handler unreadMessageHandler;

    private String eventQueueId;
    private int lastEventId;

    private int pointer;

    // This object's intrinsic lock is used to prevent multiple threads from
    // making conflicting updates to ranges
    public Object updateRangeLock = new Object();

    /**
     * Mapping of email address to presence information for that user. This is
     * updated every 2 minutes by a background thread (see AsyncStatusUpdate)
     */
    public final Map<String, Presence> presences = new ConcurrentHashMap<String, Presence>();

    /**
     * Queue of message ids to be marked as read. This queue should be emptied
     * every couple of seconds
     */
    public final Queue<Integer> unreadMessageQueue = new ConcurrentLinkedQueue<Integer>();

    public static ZulipApp get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ZulipApp.instance = this;

        // This used to be from HumbugActivity.getPreferences, so we keep that
        // file name.
        this.settings = getSharedPreferences("HumbugActivity",
                Context.MODE_PRIVATE);

        max_message_id = settings.getInt("max_message_id", -1);
        eventQueueId = settings.getString("eventQueueId", null);
        lastEventId = settings.getInt("lastEventId", -1);
        pointer = settings.getInt("pointer", -1);

        if (BuildHelper.shouldLogToCrashlytics()) {
            Crashlytics.start(this);
        }

        this.api_key = settings.getString(API_KEY, null);

        if (api_key != null) {
            afterLogin();
        }

         mutedTopics = new HashSet<String>(settings.getStringSet(MUTED_TOPIC_KEY, new HashSet<String>()));
        // create unread message queue
        unreadMessageHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(android.os.Message message) {
                if (message.what == 0) {
                    AsyncUnreadMessagesUpdate task = new AsyncUnreadMessagesUpdate(
                            ZulipApp.this);
                    task.execute();
                }

                // documentation doesn't say what this value does for
                // Handler.Callback,
                // and Handler.handleMessage returns void
                // so this just returns true.
                return true;
            }
        });
    }

    int getAppVersion() {
        try {
            PackageInfo packageInfo = this.getPackageManager().getPackageInfo(
                    this.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            throw new RuntimeException("Could not get package version: " + e);
        }
    }

    public void afterLogin() {
        String email = settings.getString(EMAIL, null);
        setEmail(email);
    }

    public Boolean isLoggedIn() {
        return this.api_key != null;
    }

    public void clearConnectionState() {
        setEventQueueId(null);
    }

    /**
     * Determines the server URI applicable for the user.
     *
     * @return either the production or staging server's URI
     */
    public String getServerURI() {
        if (getEmail().equals("iago@zulip.com")) {
            return "http://10.0.2.2:9991/api/";
        }
        return "https://api.zulip.com/";
    }
    public String getApiKey() {
        return api_key;
    }

    public String getUserAgent() {
        try {
            return ZulipApp.USER_AGENT
                    + "/"
                    + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
            // This should… never happen, but okay.
            ZLog.logException(e);
            return ZulipApp.USER_AGENT + "/unknown";
        }
    }

    public void addToMutedTopics(JSONArray jsonArray) {
        Stream stream;

        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONArray mutedTopic = jsonArray.getJSONArray(i);
                stream = Stream.getByName(this, mutedTopic.get(0).toString());
                mutedTopics.add(stream.getId() + mutedTopic.get(1).toString());
            } catch (JSONException e) {
                Log.e("JSONException", "JSON Is not correct", e);
            }
        }
        SharedPreferences.Editor editor = settings.edit();
        editor.putStringSet(MUTED_TOPIC_KEY, new HashSet<String>(mutedTopics));
        editor.apply();
    }

    public void setEmail(String email) {
        databaseHelper = new DatabaseHelper(this, email);
        this.you = Person.getOrUpdate(this, email, null, null);
    }

    public void setLoggedInApiKey(String apiKey) {
        this.api_key = apiKey;
        Editor ed = this.settings.edit();
        ed.putString(EMAIL, this.getEmail());
        ed.putString(API_KEY, api_key);
        ed.apply();
        afterLogin();
    }

    public void logOut() {
        Editor ed = this.settings.edit();
        ed.remove(EMAIL);
        ed.remove(API_KEY);
        ed.apply();
        this.api_key = null;
        setEventQueueId(null);
    }

    public String getEmail() {
        return you == null ? "" : you.getEmail();
    }

    public DatabaseHelper getDatabaseHelper() {
        return databaseHelper;
    }

    @SuppressWarnings("unchecked")
    public <C, T> RuntimeExceptionDao<C, T> getDao(Class<C> cls) {
        try {
            return new RuntimeExceptionDao<C, T>(
                    (Dao<C, T>) databaseHelper.getDao(cls));
        } catch (SQLException e) {
            // Well that's sort of awkward.
            throw new RuntimeException(e);
        }
    }

    public void setContext(Context targetContext) {
        this.attachBaseContext(targetContext);
    }

    public String getEventQueueId() {
        return eventQueueId;
    }

    public void setEventQueueId(String eventQueueId) {
        this.eventQueueId = eventQueueId;
        Editor ed = settings.edit();
        ed.putString("eventQueueId", this.eventQueueId);
        ed.apply();
    }

    public int getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(int lastEventId) {
        this.lastEventId = lastEventId;
        Editor ed = settings.edit();
        ed.putInt("lastEventId", lastEventId);
        ed.apply();
    }

    public int getMaxMessageId() {
        return max_message_id;
    }

    public void setMaxMessageId(int max_message_id) {
        this.max_message_id = max_message_id;
        if (settings != null) {
            Editor ed = settings.edit();
            ed.putInt("max_message_id", max_message_id);
            ed.apply();
        }
    }

    public int getPointer() {
        return pointer;
    }

    public void setPointer(int pointer) {
        this.pointer = pointer;
        Editor ed = settings.edit();
        ed.putInt("pointer", pointer);
        ed.apply();
    }

    public void resetDatabase() {
        databaseHelper.resetDatabase(databaseHelper.getWritableDatabase());
    }

    public void onResetDatabase() {
        setPointer(-1);
        setMaxMessageId(-1);
        setLastEventId(-1);
        setEventQueueId(null);
    }

    public void markMessageAsRead(Message message) {
        if (unreadMessageHandler == null) {
            Log.e("zulipApp",
                    "markMessageAsRead called before unreadMessageHandler was instantiated");
            return;
        }

        unreadMessageQueue.offer(message.getID());
        if (!unreadMessageHandler.hasMessages(0)) {
            unreadMessageHandler.sendEmptyMessageDelayed(0, 2000);
        }
    }
    public void muteTopic(Message message) {
        mutedTopics.add(message.concatStreamAndTopic());
        SharedPreferences.Editor editor = settings.edit();
        editor.putStringSet(MUTED_TOPIC_KEY, new HashSet<String>(mutedTopics));
        editor.apply();
    }

    public boolean isTopicMute(Message message) {
        return mutedTopics.contains(message.concatStreamAndTopic());
    }
}
