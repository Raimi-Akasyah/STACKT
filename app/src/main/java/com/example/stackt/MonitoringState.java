package com.example.stackt;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MonitoringState {
    private static final String PREF_NAME = "MonitoringPrefs";
    private static final String KEY_TARGETS = "monitoring_targets";
    private static final String KEY_ACTIVE_TARGET = "active_target";
    private static final String KEY_TARGET_NAMES_PREFIX = "target_name_";
    private static final String KEY_TARGET_EMAILS_PREFIX = "target_email_";

    // Add a new monitoring target
    public static void addMonitoringTarget(Context context, String targetId, String name, String email) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> targets = prefs.getStringSet(KEY_TARGETS, new HashSet<>());

        // Create a new set to modify (SharedPreferences StringSet is immutable)
        Set<String> newTargets = new HashSet<>(targets);
        newTargets.add(targetId);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(KEY_TARGETS, newTargets);
        editor.putString(KEY_TARGET_NAMES_PREFIX + targetId, name != null ? name : "User");
        editor.putString(KEY_TARGET_EMAILS_PREFIX + targetId, email != null ? email : "");

        // If this is the first target, set it as active
        if (getActiveTargetId(context) == null) {
            editor.putString(KEY_ACTIVE_TARGET, targetId);
        }

        editor.apply();
    }

    // Get all monitoring targets
    public static List<String> getMonitoringTargets(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> targetSet = prefs.getStringSet(KEY_TARGETS, new HashSet<>());
        return new ArrayList<>(targetSet);
    }

    // Get active target ID
    public static String getActiveTargetId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_ACTIVE_TARGET, null);
    }

    // Set active target
    public static void setActiveTarget(Context context, String targetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ACTIVE_TARGET, targetId).apply();
    }

    // Get target name
    public static String getTargetName(Context context, String targetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TARGET_NAMES_PREFIX + targetId, "User");
    }

    // Get target email
    public static String getTargetEmail(Context context, String targetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TARGET_EMAILS_PREFIX + targetId, "");
    }

    // Remove a target
    public static void removeMonitoringTarget(Context context, String targetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> targets = prefs.getStringSet(KEY_TARGETS, new HashSet<>());
        Set<String> newTargets = new HashSet<>(targets);
        newTargets.remove(targetId);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(KEY_TARGETS, newTargets);
        editor.remove(KEY_TARGET_NAMES_PREFIX + targetId);
        editor.remove(KEY_TARGET_EMAILS_PREFIX + targetId);

        // If we removed the active target, set a new active target
        if (targetId.equals(getActiveTargetId(context))) {
            if (!newTargets.isEmpty()) {
                editor.putString(KEY_ACTIVE_TARGET, newTargets.iterator().next());
            } else {
                editor.remove(KEY_ACTIVE_TARGET);
            }
        }

        editor.apply();
    }

    // Clear all monitoring
    public static void clearAllMonitoring(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Get all targets to clear their names/emails
        Set<String> targets = prefs.getStringSet(KEY_TARGETS, new HashSet<>());
        for (String targetId : targets) {
            editor.remove(KEY_TARGET_NAMES_PREFIX + targetId);
            editor.remove(KEY_TARGET_EMAILS_PREFIX + targetId);
        }

        editor.remove(KEY_TARGETS);
        editor.remove(KEY_ACTIVE_TARGET);
        editor.apply();
    }

    // Check if target exists
    public static boolean hasTarget(Context context, String targetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> targets = prefs.getStringSet(KEY_TARGETS, new HashSet<>());
        return targets.contains(targetId);
    }

    // Get number of targets
    public static int getTargetCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> targets = prefs.getStringSet(KEY_TARGETS, new HashSet<>());
        return targets.size();
    }

    public static boolean isMonitoring(Context context) {
        return getActiveTargetId(context) != null;
    }

    public static String getTargetUserId(Context context) {
        return getActiveTargetId(context);
    }

    public static void stopMonitoring(Context context) {
        clearAllMonitoring(context);
    }
}