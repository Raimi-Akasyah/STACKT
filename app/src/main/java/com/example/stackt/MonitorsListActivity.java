package com.example.stackt;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MonitorsListActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String currentUserId;

    private ListView lvMonitors;
    private TextView tvEmptyList;
    private ArrayAdapter<String> adapter;
    private List<String> monitorNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitors_list);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        initializeViews();
        fetchMonitors();
    }

    private void initializeViews() {
        lvMonitors = findViewById(R.id.lv_monitors);
        tvEmptyList = findViewById(R.id.tv_empty_list);
        monitorNames = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, monitorNames);
        lvMonitors.setAdapter(adapter);

        ImageView backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());
    }

    private void fetchMonitors() {
        db.collection("monitoring")
                .whereEqualTo("targetID", currentUserId)
                .whereEqualTo("status", "approved")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        tvEmptyList.setVisibility(View.VISIBLE);
                        lvMonitors.setVisibility(View.GONE);
                        return;
                    }

                    monitorNames.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String requesterId = document.getString("requesterID");
                        if (requesterId != null) {
                            // Fetch the requester's name from the 'user' collection
                            db.collection("user").document(requesterId).get()
                                    .addOnSuccessListener(userDoc -> {
                                        if (userDoc.exists()) {
                                            String name = userDoc.getString("name");
                                            monitorNames.add(name);
                                            adapter.notifyDataSetChanged();
                                        }
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load monitors list.", Toast.LENGTH_SHORT).show();
                });
    }
}
