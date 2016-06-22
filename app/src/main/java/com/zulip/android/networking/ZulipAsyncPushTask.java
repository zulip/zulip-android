package com.zulip.android.networking;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpResponseException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.zulip.android.util.ZLog;
import com.zulip.android.ZulipApp;

/* General AsyncTask for use in making various web requests to Humbug.
 * 
 * This class should be extended by each asynchronous operation you
 * want to run. Most clients will need to override onPostExecute. 
 */
public class ZulipAsyncPushTask extends AsyncTask<String, String, String> {

    private static final String TAG = "ZulipAsyncPushTask";

    public ZulipApp app;
    HTTPRequest request;
    AsyncTaskCompleteListener callback;

    /**
     * Interface implemented by callbacks which are run at the end of a task.
     * <p/>
     * Clients overriding onPostExecute will need to finish with
     * <p/>
     * callback.onTaskComplete(result);
     * <p/>
     * if they want to honor declared callback.
     */
    public interface AsyncTaskCompleteListener {
        void onTaskComplete(String result, JSONObject jsonObject);

        void onTaskFailure(String result);
    }

    /**
     * Declares a new HumbugAsyncPushTask, passing the activity as context.
     *
     * @param app the zulip app god object
     */
    public ZulipAsyncPushTask(ZulipApp app) {
        this.app = app;
        callback = new AsyncTaskCompleteListener() {
            @Override
            public void onTaskComplete(String result, JSONObject jsonObject) {
                // Dummy method which does nothing
            }

            public void onTaskFailure(String result) {
            }
        };
        this.request = new HTTPRequest(app);
    }

    /*
     * Target newer API than the general application because
     * THREAD_POOL_EXECUTOR was not available on older Androids. This is okay
     * because on pre-honeycomb, post-donut Android always used a thread pool.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public AsyncTask<String, String, String> execute(String method, String url) {
        try {
            return this.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, method, url);
        } catch (NoSuchFieldError e) {
            Log.e(TAG, e.getMessage(), e);
            return super.execute(method, url);
        }
    }

    /**
     * Sets the callback to run when the task is complete.
     *
     * @param listener AsyncTaskCompleteListener to run
     */
    public void setCallback(AsyncTaskCompleteListener listener) {
        callback = listener;
    }

    /**
     * Sets a parameter for the request.
     */
    public void setProperty(String key, String value) {
        this.request.setProperty(key, value);
    }

    protected String doInBackground(String... api_path) {
        try {
            return request.execute(api_path[0], api_path[1]);
        } catch (HttpResponseException e) {
            handleHTTPError(e);
        } catch (IOException e) {
            handleError(e);
        }
        return "";
    }

    /**
     * Prints a stacktrace and cancels the task.
     * <p/>
     * This function is called whenever the backend ends up in an error
     * condition.
     * <p/>
     * Override this to specify custom error behavior in your Task.
     *
     * @param e the Exception that triggered this handler
     */
    protected void handleError(Exception e) {
        // Ignore things that are obviously not our fault
        if (!(e instanceof UnknownHostException
                || e instanceof NoHttpResponseException || e instanceof SocketException)) {
            ZLog.logException(e);
        }
        this.cancel(true);
    }

    /**
     * Prints the error reason and response and cancels the task.
     * <p/>
     * This function is called if the server does not return a 200 OK as a
     * response to a request.
     * <p/>
     * Override this to specify custom behavior in your task.
     *
     * @param e the Exception that triggered this handler
     */
    protected void handleHTTPError(HttpResponseException e) {
        if (e.getStatusCode() < 500 && e.getStatusCode() >= 400) {
            ZLog.logException(e);
        } else {
            Log.e("hapt", "http error", e);
        }
        this.cancel(true);
    }

    @Override
    protected void onPostExecute(String result) {
        callback.onTaskComplete(result, null);
    }

    @Override
    protected void onCancelled(String result) {
        callback.onTaskFailure(result);
    }
}
