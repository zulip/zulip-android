package com.zulip.android.util;

/**
 * List of all Constants used in the projects
 */

public class Constants {
    public final static String IS_CONTENT_EDIT_PARAM_SAVED = "isContentEditParamSaved";
    public final static String IS_EDITING_ALLOWED = "isEditingAllowed";
    public final static String MAXIMUM_CONTENT_EDIT_LIMIT = "maximumContentEditLimit";
    //Default maximum time limit for editing message(Same as server)
    public final static int DEFAULT_MAXIMUM_CONTENT_EDIT_LIMIT = 600;
    public final static boolean DEFAULT_EDITING_ALLOWED = true;
    public final static String SERVER_URL = "SERVER_URL";
    public static final int HIDE_FAB_AFTER_SEC = 5;
    public static final int REQUEST_PICK_FILE = 3;
    public static final long STATUS_UPDATER_INTERVAL = 25 * 1000; //in milliseconds
    public static final int INACTIVE_TIME_OUT = 2 * 60; //in milliseconds
    public static final int PEOPLE_DRAWER_ACTIVE_GROUP_ID = 0;
    public static final int PEOPLE_DRAWER_RECENT_PM_GROUP_ID = 1;
    public static final int PEOPLE_DRAWER_OTHERS_GROUP_ID = 2;
    // row number which is used to differentiate the 'All private messages' in people drawer
    public static final int ALL_PEOPLE_ID = -1;
    public static final String CANCEL = "Cancel";
    public static int MILLISECONDS_IN_A_MINUTE = 1000;
    public static String DATE_FORMAT = "dd/MM/yyyy";
    // row number which is used to differentiate the '@-mentions' in people drawer
    public static int MENTIONS = -2;
}
