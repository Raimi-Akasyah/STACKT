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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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

public class ExpenseHistoryDetailActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private ExpandableListView expandableListView;
    private ExpenseAdapter adapter;
    private List<String> listDataHeader;
    private Map<String, List<String>> listDataChild;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_history_detail);

        db = FirebaseFirestore.getInstance();
        expandableListView = findViewById(R.id.expenses_expandable_list_view);
        TextView categoryTitle = findViewById(R.id.category_title);
        ImageView backButton = findViewById(R.id.back_button);

        backButton.setOnClickListener(v -> finish());

        String category = getIntent().getStringExtra("CATEGORY");
        if (category != null) {
            categoryTitle.setText(getString(R.string.category_history, category));
            loadExpenses(category);
        } else {
            Toast.makeText(this, "Category not specified.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadExpenses(String category) {
        String currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        // Calculate the date 4 months ago
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -4);
        Date fourMonthsAgo = cal.getTime();

        // Use the existing composite index: userID + category + timestamp
        db.collection("expenses")
                .whereEqualTo("userID", currentUserId)
                .whereEqualTo("category", category)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "No expenses found for this category.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Filter manually to get only last 4 months
                    List<DocumentSnapshot> allDocs = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        com.google.firebase.Timestamp timestamp = doc.getTimestamp("timestamp");
                        if (timestamp != null && timestamp.toDate().after(fourMonthsAgo)) {
                            allDocs.add(doc);
                        }
                    }

                    if (allDocs.isEmpty()) {
                        Toast.makeText(this, "No expenses found for this category in the last 4 months.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Group expenses by month
                    Map<String, List<String>> monthlyExpenses = new TreeMap<>(Collections.reverseOrder());
                    SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
                    SimpleDateFormat detailFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

                    for (DocumentSnapshot document : allDocs) {
                        com.google.firebase.Timestamp timestamp = document.getTimestamp("timestamp");
                        if (timestamp != null) {
                            Date date = timestamp.toDate();
                            String monthKey = monthFormat.format(date);

                            String store = document.getString("description");
                            Double amountDouble = document.getDouble("amount");

                            if (store == null) store = "Unknown Store";
                            if (amountDouble == null) amountDouble = 0.0;

                            double amount = amountDouble;

                            String expenseDetails = String.format(Locale.US, "Store: %s\nAmount: RM%.2f\nDate: %s",
                                    store, amount, detailFormat.format(date));

                            if (!monthlyExpenses.containsKey(monthKey)) {
                                monthlyExpenses.put(monthKey, new ArrayList<>());
                            }
                            List<String> monthExpenses = monthlyExpenses.get(monthKey);
                            if (monthExpenses != null) {
                                monthExpenses.add(expenseDetails);
                            }
                        }
                    }

                    listDataHeader = new ArrayList<>(monthlyExpenses.keySet());
                    listDataChild = monthlyExpenses;

                    adapter = new ExpenseAdapter(this, listDataHeader, listDataChild);
                    expandableListView.setAdapter(adapter);

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load expenses: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("ExpenseHistory", "Error loading expenses: " + e.getMessage());
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
}
