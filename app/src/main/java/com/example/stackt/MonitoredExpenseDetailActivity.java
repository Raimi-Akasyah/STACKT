package com.example.stackt;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class MonitoredExpenseDetailActivity extends AppCompatActivity {

    private static final String TAG = "MonitoredExpenseDetail";

    private FirebaseFirestore db;
    private ExpandableListView expandableListView;
    private ExpenseAdapter adapter;
    private List<String> listDataHeader;
    private Map<String, List<String>> listDataChild;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_history_detail);

        Log.d(TAG, "=== MonitoredExpenseDetailActivity Created ===");

        db = FirebaseFirestore.getInstance();
        expandableListView = findViewById(R.id.expenses_expandable_list_view);
        TextView categoryTitle = findViewById(R.id.category_title);
        ImageView backButton = findViewById(R.id.back_button);

        backButton.setOnClickListener(v -> finish());

        String category = getIntent().getStringExtra("CATEGORY");
        String targetUserId = getIntent().getStringExtra("TARGET_USER_ID");

        Log.d(TAG, "Received category: " + category);
        Log.d(TAG, "Received targetUserId: " + targetUserId);

        if (category != null && targetUserId != null) {
            categoryTitle.setText(getString(R.string.category_history, category));
            loadExpenses(category, targetUserId);
        } else {
            Log.e(TAG, "Category or target user ID is null");
            Toast.makeText(this, "Category or target user not specified.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadExpenses(String category, String targetUserId) {
        Log.d(TAG, "=== Loading expenses for category: " + category + ", targetUserId: " + targetUserId + " ===");

        // Calculate the date 4 months ago
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -4);
        Date fourMonthsAgo = cal.getTime();

        SimpleDateFormat debugFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Log.d(TAG, "Filtering expenses after: " + debugFormat.format(fourMonthsAgo));

        db.collection("expenses")
                .whereEqualTo("userID", targetUserId)
                .whereEqualTo("category", category)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Query successful. Total documents found: " + queryDocumentSnapshots.size());

                    if (queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "No documents found in query");
                        Toast.makeText(this, "No expenses found for " + category + ".", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Log all documents for debugging
                    int docCount = 0;
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Log.d(TAG, "Document " + docCount + " ID: " + doc.getId());
                        Log.d(TAG, "Document " + docCount + " Data: " + doc.getData());
                        docCount++;
                    }

                    // Filter manually to get only last 4 months
                    List<DocumentSnapshot> allDocs = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Date timestamp = doc.getDate("timestamp");
                        if (timestamp == null) {
                            // Try to get timestamp as Long
                            Object timestampObj = doc.get("timestamp");
                            if (timestampObj instanceof Long) {
                                timestamp = new Date((Long) timestampObj);
                                Log.d(TAG, "Converted Long timestamp to Date: " + timestamp);
                            }
                        }

                        if (timestamp != null) {
                            Log.d(TAG, "Document timestamp: " + debugFormat.format(timestamp) + ", after cutoff: " + timestamp.after(fourMonthsAgo));
                            if (timestamp.after(fourMonthsAgo)) {
                                allDocs.add(doc);
                            }
                        } else {
                            Log.w(TAG, "Document has no timestamp: " + doc.getId());
                        }
                    }

                    Log.d(TAG, "Documents after date filtering: " + allDocs.size());

                    if (allDocs.isEmpty()) {
                        Toast.makeText(this, "No " + category + " expenses found in the last 4 months.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Group expenses by month
                    Map<String, List<String>> monthlyExpenses = new TreeMap<>(Collections.reverseOrder());
                    SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
                    SimpleDateFormat detailFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

                    for (DocumentSnapshot document : allDocs) {
                        Date timestamp = document.getDate("timestamp");
                        if (timestamp == null) {
                            // Try to get timestamp as Long
                            Object timestampObj = document.get("timestamp");
                            if (timestampObj instanceof Long) {
                                timestamp = new Date((Long) timestampObj);
                            }
                        }

                        if (timestamp != null) {
                            String monthKey = monthFormat.format(timestamp);

                            String description = document.getString("description");
                            Double amountDouble = document.getDouble("amount");

                            if (description == null || description.isEmpty()) {
                                description = "Unknown Store";
                            }
                            if (amountDouble == null) {
                                amountDouble = 0.0;
                                Log.w(TAG, "Document has null amount, using 0.0");
                            }

                            String expenseDetails = String.format(Locale.US, "%s\nAmount: RM%.2f\nDate: %s",
                                    description, amountDouble, detailFormat.format(timestamp));

                            if (!monthlyExpenses.containsKey(monthKey)) {
                                monthlyExpenses.put(monthKey, new ArrayList<>());
                                Log.d(TAG, "Created new month group: " + monthKey);
                            }
                            List<String> monthExpenses = monthlyExpenses.get(monthKey);
                            if (monthExpenses != null) {
                                monthExpenses.add(expenseDetails);
                            }
                            Log.d(TAG, "Added expense to month " + monthKey + ": " + description + " - RM" + amountDouble);
                        }
                    }

                    listDataHeader = new ArrayList<>(monthlyExpenses.keySet());
                    listDataChild = monthlyExpenses;

                    Log.d(TAG, "Final data - Headers: " + listDataHeader.size() + ", Total expenses: " + allDocs.size());

                    adapter = new ExpenseAdapter(this, listDataHeader, listDataChild);
                    expandableListView.setAdapter(adapter);

                    // Expand all groups by default
                    for (int i = 0; i < adapter.getGroupCount(); i++) {
                        expandableListView.expandGroup(i);
                    }

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Query failed: " + e.getMessage(), e);
                    Toast.makeText(this, "Failed to load expenses: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private static class ExpenseAdapter extends BaseExpandableListAdapter {

        private final Context context;
        private final List<String> listDataHeader;
        private final Map<String, List<String>> listDataChild;

        public ExpenseAdapter(Context context, List<String> listDataHeader, Map<String, List<String>> listChildData) {
            this.context = context;
            this.listDataHeader = listDataHeader;
            this.listDataChild = listChildData;
            Log.d(TAG, "ExpenseAdapter created with " + listDataHeader.size() + " groups");
        }

        @Override
        public Object getChild(int groupPosition, int childPosititon) {
            if (listDataHeader == null || listDataChild == null) {
                return null;
            }
            String groupKey = listDataHeader.get(groupPosition);
            List<String> children = listDataChild.get(groupKey);
            if (children != null && childPosititon < children.size()) {
                return children.get(childPosititon);
            }
            return null;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public View getChildView(int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {

            final String childText = (String) getChild(groupPosition, childPosition);

            if (convertView == null) {
                LayoutInflater infalInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = infalInflater.inflate(R.layout.list_item, parent, false);
            }

            TextView txtListChild = convertView.findViewById(R.id.lblListItem);
            txtListChild.setText(Objects.requireNonNullElse(childText, context.getString(R.string.no_data_available)));
            return convertView;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            if (listDataHeader == null || listDataChild == null) {
                return 0;
            }
            String groupKey = listDataHeader.get(groupPosition);
            List<String> children = listDataChild.get(groupKey);
            return children != null ? children.size() : 0;
        }

        @Override
        public Object getGroup(int groupPosition) {
            if (listDataHeader == null) {
                return null;
            }
            return listDataHeader.get(groupPosition);
        }

        @Override
        public int getGroupCount() {
            return listDataHeader != null ? listDataHeader.size() : 0;
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            String headerTitle = (String) getGroup(groupPosition);
            if (convertView == null) {
                LayoutInflater infalInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = infalInflater.inflate(R.layout.list_group, parent, false);
            }

            TextView lblListHeader = convertView.findViewById(R.id.lblListHeader);
            lblListHeader.setText(Objects.requireNonNullElse(headerTitle, context.getString(R.string.unknown_month)));

            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "=== MonitoredExpenseDetailActivity Destroyed ===");
    }
}