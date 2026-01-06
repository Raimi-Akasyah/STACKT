package com.example.stackt;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NotificationActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String currentUserId;
    private NotificationAdapter adapter;
    private final List<DocumentSnapshot> notificationList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        db = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        RecyclerView rvNotifications = findViewById(R.id.rv_notifications);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(notificationList, this::handleRequest);
        rvNotifications.setAdapter(adapter);

        runDiagnostic();
    }

    private void runDiagnostic() {
        // THIS IS A DIAGNOSTIC. IT IS NOT THE FINAL CODE.
        db.collection("monitoring_notifications").get().addOnSuccessListener(allDocs -> {
            db.collection("monitoring_notifications").whereEqualTo("toUserID", currentUserId).get().addOnSuccessListener(filteredDocs -> {
                String toastMessage = "DIAGNOSTIC RESULT - All Docs: " + allDocs.size() + ", Filtered Docs: " + filteredDocs.size();
                Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();

                Log.d("DIAGNOSTIC", "==================================================");
                Log.d("DIAGNOSTIC", "App is looking for User ID: [" + currentUserId + "]");

                for (QueryDocumentSnapshot doc : allDocs) {
                    String dbUserId = doc.getString("toUserID");
                    Log.d("DIAGNOSTIC", "Database has User ID:     [" + dbUserId + "]");
                    if (!currentUserId.equals(dbUserId)) {
                        Log.e("DIAGNOSTIC", "MISMATCH FOUND!");
                    }
                }
                Log.d("DIAGNOSTIC", "==================================================");

                // Display whatever the filtered query found
                notificationList.clear();
                notificationList.addAll(filteredDocs.getDocuments());
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void handleRequest(DocumentSnapshot notification, boolean approved) {
        String status = approved ? "approved" : "denied";
        String monitoringId = notification.getString("monitoringID");

        if (monitoringId == null) {
            notification.getReference().delete();
            return;
        }

        db.collection("monitoring").document(monitoringId)
                .update("status", status)
                .addOnSuccessListener(aVoid -> notification.getReference().delete())
                .addOnCompleteListener(task -> {
                    if (approved) {
                        Toast.makeText(this, "Request Approved!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Request Denied.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // --- RecyclerView Adapter (No changes needed) ---
    private static class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
        private final List<DocumentSnapshot> notifications;
        private final NotificationActionHandler handler;
        interface NotificationActionHandler { void onAction(DocumentSnapshot notification, boolean approved); }
        NotificationAdapter(List<DocumentSnapshot> notifications, NotificationActionHandler handler) { this.notifications = notifications; this.handler = handler; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.notification_item, parent, false); return new ViewHolder(view); }
        @Override public int getItemCount() { return notifications != null ? notifications.size() : 0; }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = notifications.get(position);
            String fromUserId = doc.getString("fromUserID");
            Timestamp sendAt = doc.getTimestamp("sendAt");
            if (sendAt != null) { holder.time.setText(getRelativeTime(sendAt)); } else { holder.time.setText(""); }
            if (fromUserId == null || fromUserId.isEmpty()) { holder.message.setText("Someone wants to monitor your expenses.");
            } else { holder.message.setText("Loading...");
                FirebaseFirestore.getInstance().collection("user").document(fromUserId).get()
                        .addOnCompleteListener(task -> {
                            if (holder.getAdapterPosition() != position) return;
                            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                                DocumentSnapshot userDoc = task.getResult();
                                String name = userDoc.getString("name");
                                holder.message.setText((name != null ? name : "Someone") + " wants to monitor your expenses.");
                            } else { holder.message.setText(fromUserId + " wants to monitor your expenses."); }
                        });
            }
            holder.approveButton.setOnClickListener(v -> handler.onAction(doc, true));
            holder.denyButton.setOnClickListener(v -> handler.onAction(doc, false));
        }
        private String getRelativeTime(Timestamp timestamp) {
            long diff = new Date().getTime() - timestamp.toDate().getTime();
            long seconds = diff / 1000; long minutes = seconds / 60; long hours = minutes / 60; long days = hours / 24;
            if (days > 0) return days + "d ago"; if (hours > 0) return hours + "h ago"; if (minutes > 0) return minutes + "m ago"; return "Just now";
        }
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView message, time; Button approveButton, denyButton;
            ViewHolder(@NonNull View itemView) { super(itemView); message = itemView.findViewById(R.id.tv_notification_message); time = itemView.findViewById(R.id.tv_notification_time); approveButton = itemView.findViewById(R.id.btn_approve); denyButton = itemView.findViewById(R.id.btn_deny); }
        }
    }
}
