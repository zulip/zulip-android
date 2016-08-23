package com.zulip.android.activities;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.zulip.android.R;
import com.zulip.android.networking.AsyncDevGetEmails;
import com.zulip.android.networking.AsyncLogin;
import com.zulip.android.networking.LoginInterface;
import com.zulip.android.networking.ZulipAsyncPushTask;
import com.zulip.android.util.AuthClickListener;
import com.zulip.android.util.ZLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity where the Emails for the DevAuthBackend are displayed.
 */
public class DevAuthActivity extends Activity implements LoginInterface {
    private RecyclerView recyclerView;
    private ProgressDialog connectionProgressDialog;
    public static final int ADD_REALM_REQUEST = 566;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dev_auth);
        String json = getIntent().getStringExtra(AsyncDevGetEmails.EMAIL_JSON);
        final String realmName = getIntent().getStringExtra(AsyncDevGetEmails.REALM_NAME_JSON);
        final String serverURL = getIntent().getStringExtra(AsyncDevGetEmails.SERVER_URL_JSON);
        final boolean startedFromAddRealm = getIntent().getBooleanExtra(AsyncDevGetEmails.ADD_REALM_BOOLEAN_JSON, false);
        recyclerView = (RecyclerView) findViewById(R.id.devAuthRecyclerView);
        List<String> emails = new ArrayList<>();
        int directAdminSize = 1;
        connectionProgressDialog = new ProgressDialog(this);
        connectionProgressDialog.setMessage(getString(R.string.signing_in));

        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray jsonArray = jsonObject.getJSONArray("direct_admins");
            for (int i = 0; i < jsonArray.length(); i++) {
                emails.add(jsonArray.get(i).toString());
            }
            directAdminSize = jsonArray.length();
            jsonArray = jsonObject.getJSONArray("direct_users");
            for (int i = 0; i < jsonArray.length(); i++) {
                emails.add(jsonArray.get(i).toString());
            }
        } catch (JSONException e) {
            ZLog.logException(e);
        }
        AuthEmailAdapter authEmailAdapter = new AuthEmailAdapter(emails, directAdminSize, DevAuthActivity.this);
        recyclerView.setAdapter(authEmailAdapter);
        authEmailAdapter.setOnItemClickListener(new AuthClickListener() {
            @Override
            public void onItemClick(String email) {
                AsyncLogin asyncLogin = new AsyncLogin(DevAuthActivity.this, email, null, realmName, startedFromAddRealm, serverURL, true);
                asyncLogin.execute();
                connectionProgressDialog.show();
                asyncLogin.setCallback(new ZulipAsyncPushTask.AsyncTaskCompleteListener() {
                    @Override
                    public void onTaskComplete(String result, JSONObject jsonObject) {
                        connectionProgressDialog.dismiss();
                    }

                    @Override
                    public void onTaskFailure(String result) {
                        connectionProgressDialog.dismiss();
                    }
                });
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(DevAuthActivity.this) {
        });
    }

    @Override
    public void openHome() {
        // Cancel before leaving activity to avoid leaking windows
        connectionProgressDialog.dismiss();
        Intent i = new Intent(this, ZulipActivity.class);
        startActivity(i);
        finish();
    }
}
