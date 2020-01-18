/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.wearable.wear.wearnotifications;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.material.snackbar.Snackbar;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.BigTextStyle;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.wear.ambient.AmbientMode;
import androidx.wear.widget.WearableLinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import com.example.android.wearable.wear.common.mock.MockDatabase;
import com.example.android.wearable.wear.common.util.NotificationUtil;
import com.example.android.wearable.wear.wearnotifications.handlers.BigTextIntentService;
import com.example.android.wearable.wear.wearnotifications.handlers.BigTextMainActivity;

/**
 * Demonstrates best practice for {@link NotificationCompat} Notifications created by local
 * standalone Wear apps. All {@link NotificationCompat} examples use
 * {@link NotificationCompat.Style}.
 */
public class StandaloneMainActivity extends Activity implements
        AmbientMode.AmbientCallbackProvider {

    private static final String TAG = "StandaloneMainActivity";

    public static final int NOTIFICATION_ID = 888;

    /*
     * Used to represent each major {@link NotificationCompat.Style} in the
     * {@link WearableRecyclerView}. These constants are also used in a switch statement when one
     * of the items is selected to create the appropriate {@link Notification}.
     */
    private static final String BIG_TEXT_STYLE = "BIG_TEXT_STYLE";

    /*
    Collection of major {@link NotificationCompat.Style} to create {@link CustomRecyclerAdapter}
    for {@link WearableRecyclerView}.
    */
    private static final String[] NOTIFICATION_STYLES = {BIG_TEXT_STYLE};

    private NotificationManagerCompat mNotificationManagerCompat;

    // Needed for {@link SnackBar} to alert users when {@link Notification} are disabled for app.
    private FrameLayout mMainFrameLayout;
    private WearableRecyclerView mWearableRecyclerView;
    private CustomRecyclerAdapter mCustomRecyclerAdapter;

    /**
     * Ambient mode controller attached to this display. Used by Activity to see if it is in
     * ambient mode.
     */
    private AmbientMode.AmbientController mAmbientController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        setContentView(R.layout.activity_main);

        // Enables Ambient mode.
        mAmbientController = AmbientMode.attachAmbientSupport(this);

        mNotificationManagerCompat = NotificationManagerCompat.from(getApplicationContext());

        mMainFrameLayout = findViewById(R.id.mainFrameLayout);
        mWearableRecyclerView = findViewById(R.id.recycler_view);

        // Aligns the first and last items on the list vertically centered on the screen.
        mWearableRecyclerView.setEdgeItemsCenteringEnabled(true);

        // Customizes scrolling so items farther away form center are smaller.
        ScalingScrollLayoutCallback scalingScrollLayoutCallback =
                new ScalingScrollLayoutCallback();
        mWearableRecyclerView.setLayoutManager(
                new WearableLinearLayoutManager(this, scalingScrollLayoutCallback));

        // Improves performance because we know changes in content do not change the layout size of
        // the RecyclerView.
        mWearableRecyclerView.setHasFixedSize(true);

        // Specifies an adapter (see also next example).
        mCustomRecyclerAdapter = new CustomRecyclerAdapter(
                NOTIFICATION_STYLES,
                // Controller passes selected data from the Adapter out to this Activity to trigger
                // updates in the UI/Notifications.
                new Controller(this));

        mWearableRecyclerView.setAdapter(mCustomRecyclerAdapter);
    }

    // Called by WearableRecyclerView when an item is selected (check onCreate() for
    // initialization).
    public void itemSelected(String data) {

        Log.d(TAG, "itemSelected()");

        boolean areNotificationsEnabled = mNotificationManagerCompat.areNotificationsEnabled();

        // If notifications are disabled, allow user to enable.
        if (!areNotificationsEnabled) {
            // Because the user took an action to create a notification, we create a prompt to let
            // the user re-enable notifications for this application again.
            Snackbar snackbar = Snackbar
                    .make(
                            mMainFrameLayout,
                            "", // Not enough space for both text and action text.
                            Snackbar.LENGTH_LONG)
                    .setAction("Enable Notifications", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            // Links to this app's notification settings.
                            openNotificationSettingsForApp();
                        }
                    });
            snackbar.show();
            return;
        }

        String notificationStyle = data;

        switch (notificationStyle) {
            case BIG_TEXT_STYLE:
                generateBigTextStyleNotification();
                break;
            default:
                // continue below.
        }
    }

    /*
     * Generates a BIG_TEXT_STYLE Notification that supports both Wear 1.+ and Wear 2.0.
     *
     * IMPORTANT NOTE:
     * This method includes extra code to replicate Notification Styles behavior from Wear 1.+ and
     * phones on Wear 2.0, i.e., the notification expands on click. To see the specific code in the
     * method, search for "REPLICATE_NOTIFICATION_STYLE_CODE".
     *
     * Notification Styles behave slightly different on Wear 2.0 when they are launched by a
     * native/local Wear app, i.e., they will NOT expand when the user taps them but will instead
     * take the user directly into the local app for the richest experience. In contrast, a bridged
     * Notification launched from the phone will expand with the style details (whether there is a
     * local app or not).
     *
     * If you want to see the new behavior, please review the generateBigPictureStyleNotification()
     * and generateMessagingStyleNotification() methods.
     */
    private void generateBigTextStyleNotification() {

        Log.d(TAG, "generateBigTextStyleNotification()");

        // Main steps for building a BIG_TEXT_STYLE notification:
        //      0. Get your data
        //      1. Create/Retrieve Notification Channel for O and beyond devices (26+)
        //      2. Build the BIG_TEXT_STYLE
        //      3. Set up main Intent for notification
        //      4. Create additional Actions for the Notification
        //      5. Build and issue the notification

        // 0. Get your data (everything unique per Notification).
        MockDatabase.BigTextStyleReminderAppData bigTextStyleReminderAppData =
                MockDatabase.getBigTextStyleData();

        // 1. Create/Retrieve Notification Channel for O and beyond devices (26+).
        String notificationChannelId =
                NotificationUtil.createNotificationChannel(this, bigTextStyleReminderAppData);

        // 2. Build the BIG_TEXT_STYLE
        BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle()
                // Overrides ContentText in the big form of the template.
                .bigText(bigTextStyleReminderAppData.getBigText())
                // Overrides ContentTitle in the big form of the template.
                .setBigContentTitle(bigTextStyleReminderAppData.getBigContentTitle())
                // Summary line after the detail section in the big form of the template
                // Note: To improve readability, don't overload the user with info. If Summary Text
                // doesn't add critical information, you should skip it.
                .setSummaryText(bigTextStyleReminderAppData.getSummaryText());


        // 3. Set up main Intent for notification.
        Intent mainIntent = new Intent(this, BigTextMainActivity.class);

        PendingIntent mainPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );


        // 4. Create additional Actions (Intents) for the Notification.

        // In our case, we create two additional actions: a Snooze action and a Dismiss action.

        // Snooze Action.
        Intent snoozeIntent = new Intent(this, BigTextIntentService.class);
        snoozeIntent.setAction(BigTextIntentService.ACTION_SNOOZE);

        PendingIntent snoozePendingIntent = PendingIntent.getService(this, 0, snoozeIntent, 0);
        NotificationCompat.Action snoozeAction =
                new NotificationCompat.Action.Builder(
                        R.drawable.ic_alarm_white_48dp,
                        "Snooze",
                        snoozePendingIntent)
                        .build();

        // Dismiss Action.
        Intent dismissIntent = new Intent(this, BigTextIntentService.class);
        dismissIntent.setAction(BigTextIntentService.ACTION_DISMISS);

        PendingIntent dismissPendingIntent = PendingIntent.getService(this, 0, dismissIntent, 0);
        NotificationCompat.Action dismissAction =
                new NotificationCompat.Action.Builder(
                        R.drawable.ic_cancel_white_48dp,
                        "Dismiss",
                        dismissPendingIntent)
                        .build();


        // 5. Build and issue the notification.

        // Because we want this to be a new notification (not updating a previous notification), we
        // create a new Builder. Later, we use the same global builder to get back the notification
        // we built here for the snooze action, that is, canceling the notification and relaunching
        // it several seconds later.

        // Notification Channel Id is ignored for Android pre O (26).
        NotificationCompat.Builder notificationCompatBuilder =
                new NotificationCompat.Builder(
                        getApplicationContext(), notificationChannelId);

        GlobalNotificationBuilder.setNotificationCompatBuilderInstance(notificationCompatBuilder);

        notificationCompatBuilder
                // BIG_TEXT_STYLE sets title and content.
                .setStyle(bigTextStyle)
                .setContentTitle(bigTextStyleReminderAppData.getContentTitle())
                .setContentText(bigTextStyleReminderAppData.getContentText())
                .setSmallIcon(R.drawable.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(
                        getResources(),
                        R.drawable.ic_alarm_white_48dp))
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                // Set primary color (important for Wear 2.0 Notifications).
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary))

                .setCategory(Notification.CATEGORY_REMINDER)

                // Sets priority for 25 and below. For 26 and above, 'priority' is deprecated for
                // 'importance' which is set in the NotificationChannel. The integers representing
                // 'priority' are different from 'importance', so make sure you don't mix them.
                .setPriority(bigTextStyleReminderAppData.getPriority())

                // Sets lock-screen visibility for 25 and below. For 26 and above, lock screen
                // visibility is set in the NotificationChannel.
                .setVisibility(bigTextStyleReminderAppData.getChannelLockscreenVisibility())

                // Adds additional actions specified above.
                .addAction(snoozeAction)
                .addAction(dismissAction);

        /* REPLICATE_NOTIFICATION_STYLE_CODE:
         * You can replicate Notification Style functionality on Wear 2.0 (24+) by not setting the
         * main content intent, that is, skipping the call setContentIntent(). However, you need to
         * still allow the user to open the native Wear app from the Notification itself, so you
         * add an action to launch the app.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            // Enables launching app in Wear 2.0 while keeping the old Notification Style behavior.
            NotificationCompat.Action mainAction = new NotificationCompat.Action.Builder(
                    R.drawable.ic_launcher,
                    "Open",
                    mainPendingIntent)
                    .build();

            notificationCompatBuilder.addAction(mainAction);

        } else {
            // Wear 1.+ still functions the same, so we set the main content intent.
            notificationCompatBuilder.setContentIntent(mainPendingIntent);
        }


        Notification notification = notificationCompatBuilder.build();

        mNotificationManagerCompat.notify(NOTIFICATION_ID, notification);

        // Close app to demonstrate notification in steam.
        finish();
    }

    /**
     * Helper method for the SnackBar action, i.e., if the user has this application's notifications
     * disabled, this opens up the dialog to turn them back on after the user requests a
     * Notification launch.
     *
     * IMPORTANT NOTE: You should not do this action unless the user takes an action to see your
     * Notifications like this sample demonstrates. Spamming users to re-enable your notifications
     * is a bad idea.
     */
    private void openNotificationSettingsForApp() {
        // Links to this app's notification settings
        Intent intent = new Intent();
        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        intent.putExtra("app_package", getPackageName());
        intent.putExtra("app_uid", getApplicationInfo().uid);
        startActivity(intent);
    }

    @Override
    public AmbientMode.AmbientCallback getAmbientCallback() {
        return new MyAmbientCallback();
    }

    private class MyAmbientCallback extends AmbientMode.AmbientCallback {
        /** Prepares the UI for ambient mode. */
        @Override
        public void onEnterAmbient(Bundle ambientDetails) {
            super.onEnterAmbient(ambientDetails);

            Log.d(TAG, "onEnterAmbient() " + ambientDetails);
            // In our case, the assets are already in black and white, so we don't update UI.
        }

        /** Restores the UI to active (non-ambient) mode. */
        @Override
        public void onExitAmbient() {
            super.onExitAmbient();

            Log.d(TAG, "onExitAmbient()");
            // In our case, the assets are already in black and white, so we don't update UI.
        }
    }

}
