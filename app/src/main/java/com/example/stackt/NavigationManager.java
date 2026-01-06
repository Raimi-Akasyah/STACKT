package com.example.stackt;

import android.app.Activity;
import android.content.Intent;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class NavigationManager {

    public static void setup(final Activity activity, BottomNavigationView bottomNavigationView, int currentNavItemId) {
        bottomNavigationView.setSelectedItemId(currentNavItemId);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == currentNavItemId) {
                return true; // Do nothing if already on the current screen
            }

            if (itemId == R.id.navigation_home) {
                activity.startActivity(new Intent(activity, MainActivity.class));
                activity.finish();
                return true;
            } else if (itemId == R.id.navigation_geo) {
                activity.startActivity(new Intent(activity, GeolocationActivity.class));
                activity.finish();
                return true;
            } else if (itemId == R.id.navigation_scan) {
                activity.startActivity(new Intent(activity, OCRActivity.class));
                activity.finish();
                return true;
            } else if (itemId == R.id.navigation_monitor) {
                // Check if we have a valid monitoring session
                if (MonitoringState.isMonitoring(activity)) {
                    String targetId = MonitoringState.getTargetUserId(activity);
                    if (targetId != null && !targetId.isEmpty()) {
                        // Valid monitoring session exists, go to MonitoringActivity
                        Intent intent = new Intent(activity, MultiMonitoringActivity.class);
                        activity.startActivity(intent);
                        activity.finish();
                    } else {
                        // Invalid state, clear and go to login
                        MonitoringState.stopMonitoring(activity);
                        activity.startActivity(new Intent(activity, MonitoringLoginActivity.class));
                        activity.finish();
                    }
                } else {
                    // No monitoring session, go to login
                    activity.startActivity(new Intent(activity, MonitoringLoginActivity.class));
                    activity.finish();
                }
                return true;
            }

            return false;
        });
    }
}