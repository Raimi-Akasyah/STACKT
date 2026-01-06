package com.example.stackt;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class MonitoringLoginActivity extends AppCompatActivity {

    private static final String TAG = "MonitoringLogin";

    private FirebaseFirestore db;
    private String currentUserId;
    private ListenerRegistration monitoringRequestListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitoring_login);

        db = FirebaseFirestore.getInstance();

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Log.d(TAG, "Current User ID: " + currentUserId);

        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> goToMainActivity());

        // Handle back button press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goToMainActivity();
            }
        });

        cleanupCorruptSelfMonitoring();

        EditText emailInput = findViewById(R.id.et_target_user_email);
        Button sendButton = findViewById(R.id.btn_request_access);

        sendButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim().toLowerCase();
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter an email", Toast.LENGTH_SHORT).show();
                return;
            }
            sendRequest(email);
        });
    }

    private void cleanupCorruptSelfMonitoring() {
        db.collection("monitoring")
                .whereEqualTo("requesterID", currentUserId)
                .whereEqualTo("targetID", currentUserId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Log.w(TAG, "Found and deleting " + queryDocumentSnapshots.size() + " corrupt self-monitoring entries.");
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            doc.getReference().delete();
                        }
                    }
                });
    }

    private void sendRequest(String email) {
        db.collection("user")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(result -> {
                    if (result.isEmpty()) {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String targetUserId = result.getDocuments().get(0).getId();
                    String targetUserName = result.getDocuments().get(0).getString("name");
                    String targetUserEmail = result.getDocuments().get(0).getString("email");

                    if (targetUserId.equals(currentUserId)) {
                        Toast.makeText(this, "You cannot monitor yourself", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Check if already monitoring this user
                    if (MonitoringState.hasTarget(this, targetUserId)) {
                        Toast.makeText(this, "You are already monitoring this user", Toast.LENGTH_SHORT).show();
                        MonitoringState.setActiveTarget(this, targetUserId);
                        Intent intent = new Intent(this, MultiMonitoringActivity.class);
                        startActivity(intent);
                        finish();
                        return;
                    }

                    createRequest(targetUserId, targetUserName, targetUserEmail);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Find user error", e);
                    Toast.makeText(this, "Error finding user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void createRequest(String targetUserId, String targetUserName, String targetUserEmail) {
        String monitoringId = currentUserId + "_" + targetUserId;
        db.collection("monitoring").document(monitoringId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && "approved".equals(doc.getString("status"))) {
                        // Already approved, add to monitoring list
                        MonitoringState.addMonitoringTarget(this, targetUserId, targetUserName, targetUserEmail);
                        Toast.makeText(this, "You are already monitoring this user.", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, MultiMonitoringActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                        return;
                    }

                    if (doc.exists() && "pending".equals(doc.getString("status"))) {
                        Toast.makeText(this, "Request already pending", Toast.LENGTH_SHORT).show();
                        waitForApproval(monitoringId, targetUserId, targetUserName, targetUserEmail);
                        return;
                    }

                    Map<String, Object> monitoring = new HashMap<>();
                    monitoring.put("monitoringID", monitoringId);
                    monitoring.put("requesterID", currentUserId);
                    monitoring.put("targetID", targetUserId);
                    monitoring.put("status", "pending");
                    monitoring.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("monitoring").document(monitoringId)
                            .set(monitoring)
                            .addOnSuccessListener(aVoid -> {
                                createNotification(targetUserId, monitoringId);
                                waitForApproval(monitoringId, targetUserId, targetUserName, targetUserEmail);
                            })
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to create monitoring request", e));
                });
    }

    private void createNotification(String targetUserId, String monitoringId) {
        String notificationId = db.collection("monitoring_notifications").document().getId();
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationID", notificationId);
        notification.put("monitoringID", monitoringId);
        notification.put("fromUserID", currentUserId);
        notification.put("toUserID", targetUserId);
        notification.put("type", "monitoring_request");
        notification.put("sendAt", FieldValue.serverTimestamp());

        db.collection("monitoring_notifications")
                .document(notificationId)
                .set(notification)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Notification document created successfully!"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create notification document", e));
    }

    private void waitForApproval(String monitoringId, String targetUserId, String targetUserName, String targetUserEmail) {
        setContentView(R.layout.activity_pending);
        Toast.makeText(this, "Request sent! Waiting for approval.", Toast.LENGTH_SHORT).show();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (monitoringRequestListener != null) {
                    monitoringRequestListener.remove();
                }
                goToMainActivity();
            }
        });

        monitoringRequestListener = db.collection("monitoring").document(monitoringId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        handleRequestStatus(snapshot, targetUserId, targetUserName, targetUserEmail);
                    }
                });
    }

    private void handleRequestStatus(DocumentSnapshot doc, String targetUserId, String targetUserName, String targetUserEmail) {
        String status = doc.getString("status");

        if ("approved".equals(status)) {
            if (monitoringRequestListener != null) {
                monitoringRequestListener.remove();
            }

            // Save to monitoring list
            MonitoringState.addMonitoringTarget(this, targetUserId, targetUserName, targetUserEmail);

            Toast.makeText(this, "Request approved! Loading monitoring data...", Toast.LENGTH_LONG).show();

            Intent intent = new Intent(this, MultiMonitoringActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();

        } else if ("denied".equals(status)) {
            if (monitoringRequestListener != null) {
                monitoringRequestListener.remove();
            }
            doc.getReference().delete();
            Toast.makeText(this, "Your request was denied.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, MonitoringLoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void goToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (monitoringRequestListener != null) {
            monitoringRequestListener.remove();
        }
    }
}