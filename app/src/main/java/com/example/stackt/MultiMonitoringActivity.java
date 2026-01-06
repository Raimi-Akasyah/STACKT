package com.example.stackt;


import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MultiMonitoringActivity extends AppCompatActivity {

    private static final String TAG = "MultiMonitoringActivity";

    private FirebaseFirestore db;
    private String currentUserId;
    private String activeTargetId;

    private PieChart pieChart;
    private TextView expenseText;
    private TextView balanceText;
    private Spinner targetSpinner;

    private LatestTransactionsAdapter latestTransactionsAdapter;
    private List<Transaction> transactionList;

    private final Map<String, ListenerRegistration> listeners = new HashMap<>();
    private final List<String> targetUserIds = new ArrayList<>();
    private final List<String> targetUserNames = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "=== MultiMonitoringActivity onCreate ===");

        db = FirebaseFirestore.getInstance();

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.e(TAG, "No authenticated user found");
            goToLoginActivity();
            return;
        }
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Log.d(TAG, "Current User (Monitor) ID: " + currentUserId);

        // Check if we have any monitoring targets
        loadMonitoringTargets();

        // Handle back button press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goToMainActivity();
            }
        });
    }

    private void loadMonitoringTargets() {
        targetUserIds.clear();
        targetUserNames.clear();

        List<String> targets = MonitoringState.getMonitoringTargets(this);
        if (targets.isEmpty()) {
            Log.d(TAG, "No monitoring targets found, redirecting to login");
            Toast.makeText(this, "No monitoring targets. Please add someone to monitor.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, MonitoringLoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        targetUserIds.addAll(targets);
        for (String targetId : targetUserIds) {
            String name = MonitoringState.getTargetName(this, targetId);
            targetUserNames.add(name);
        }

        Log.d(TAG, "Loaded " + targetUserIds.size() + " monitoring targets");

        // Set content view and setup UI
        setContentView(R.layout.activity_monitoring);
        setupUI();

        // Get active target or set first one as active
        activeTargetId = MonitoringState.getActiveTargetId(this);
        if (activeTargetId == null || !targetUserIds.contains(activeTargetId)) {
            activeTargetId = targetUserIds.get(0);
            MonitoringState.setActiveTarget(this, activeTargetId);
        }

        // Setup spinner
        setupSpinner();

        // Verify and load data for active target
        verifyMonitoringSession();
    }

    private void setupUI() {
        try {
            Log.d(TAG, "--- Setting up UI components ---");

            BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
            if (bottomNavigationView != null) {
                NavigationManager.setup(this, bottomNavigationView, R.id.navigation_monitor);
                Log.d(TAG, "✓ Bottom navigation setup");
            }

            pieChart = findViewById(R.id.piechart);
            expenseText = findViewById(R.id.text_expenses);
            balanceText = findViewById(R.id.text_balance);
            TextView monitoringTitle = findViewById(R.id.monitoring_title);
            targetSpinner = findViewById(R.id.target_spinner);

            // Replace the existing title with a spinner
            if (monitoringTitle != null) {
                monitoringTitle.setVisibility(View.GONE);
            }
            if (targetSpinner != null) {
                targetSpinner.setVisibility(View.VISIBLE);
            }

            // Add person button
            ImageButton addButton = findViewById(R.id.btn_add_person);
            if (addButton != null) {
                addButton.setOnClickListener(v -> {
                    Intent intent = new Intent(MultiMonitoringActivity.this, MonitoringLoginActivity.class);
                    startActivity(intent);
                });
                Log.d(TAG, "✓ Add button setup");
            }

            Button quitButton = findViewById(R.id.btn_quit_monitoring);
            if (quitButton != null) {
                quitButton.setOnClickListener(v -> showQuitConfirmationDialog());
                Log.d(TAG, "✓ Quit button setup");
            }

            setupPieChart();
            setupCategoryClickListeners();

            RecyclerView latestTransactionsRecycler = findViewById(R.id.latest_transactions_recycler);
            if (latestTransactionsRecycler != null) {
                latestTransactionsRecycler.setLayoutManager(new LinearLayoutManager(this));
                transactionList = new ArrayList<>();
                latestTransactionsAdapter = new LatestTransactionsAdapter(transactionList);
                latestTransactionsRecycler.setAdapter(latestTransactionsAdapter);
                Log.d(TAG, "✓ Latest transactions RecyclerView setup");
            } else {
                Log.e(TAG, "✗ Latest transactions RecyclerView NOT FOUND in layout!");
            }

            Log.d(TAG, "✓ UI setup completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "✗ Error setting up UI", e);
            Toast.makeText(this, "Error loading interface", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupSpinner() {
        if (targetSpinner == null) {
            Log.e(TAG, "Target spinner is null!");
            return;
        }

        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, targetUserNames);

        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        targetSpinner.setAdapter(spinnerAdapter);

        // Set current selection
        int position = targetUserIds.indexOf(activeTargetId);
        if (position >= 0) {
            targetSpinner.setSelection(position);
        }

        targetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < targetUserIds.size()) {
                    String newTargetId = targetUserIds.get(position);
                    if (!newTargetId.equals(activeTargetId)) {
                        Log.d(TAG, "Switching to target: " + newTargetId);
                        activeTargetId = newTargetId;
                        MonitoringState.setActiveTarget(MultiMonitoringActivity.this, activeTargetId);
                        verifyMonitoringSession();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void verifyMonitoringSession() {
        String monitoringId = currentUserId + "_" + activeTargetId;
        Log.d(TAG, "Verifying monitoring session: " + monitoringId);

        db.collection("monitoring").document(monitoringId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Log.e(TAG, "Monitoring document doesn't exist in Firestore");
                        handleMonitoringSessionInvalid();
                    } else if (!"approved".equals(doc.getString("status"))) {
                        Log.e(TAG, "Monitoring session not approved. Status: " + doc.getString("status"));
                        handleMonitoringSessionInvalid();
                    } else {
                        Log.d(TAG, "✓ Monitoring session verified and approved");
                        loadMonitoringDataForTarget(activeTargetId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error verifying monitoring session", e);
                    handleMonitoringSessionInvalid();
                });
    }

    private void handleMonitoringSessionInvalid() {
        // Remove the invalid target
        MonitoringState.removeMonitoringTarget(this, activeTargetId);
        Toast.makeText(this, "Monitoring session not found or not approved. Target removed.", Toast.LENGTH_LONG).show();

        // Reload targets
        loadMonitoringTargets();
    }

    private void loadMonitoringDataForTarget(String targetId) {
        Log.d(TAG, "========================================");
        Log.d(TAG, "=== LOADING ALL DATA FOR TARGET ===");
        Log.d(TAG, "Target User ID: " + targetId);
        Log.d(TAG, "========================================");

        cleanupListeners();

        // Load target user's name
        loadTargetUserName(targetId);

        Log.d(TAG, "Setting up listeners for target ID: " + targetId);
        setupBudgetListener(targetId);
        setupExpensesListener(targetId);
        setupCategoryAmountListeners(targetId);
        fetchLatestTransactions(targetId);
    }

    private void loadTargetUserName(String targetId) {
        String targetName = MonitoringState.getTargetName(this, targetId);
        String targetEmail = MonitoringState.getTargetEmail(this, targetId);

        Log.d(TAG, "✓ Target user - Name: " + targetName + ", Email: " + targetEmail);

        // Update spinner selection text
        if (targetSpinner != null) {
            int position = targetUserIds.indexOf(targetId);
            if (position >= 0) {
                targetUserNames.set(position, targetName);
                spinnerAdapter.notifyDataSetChanged();
            }
        }
    }

    private void showQuitConfirmationDialog() {
        String targetName = MonitoringState.getTargetName(this, activeTargetId);

        new AlertDialog.Builder(this)
                .setTitle("Stop Monitoring")
                .setMessage("Are you sure you want to stop monitoring " + targetName + "?")
                .setPositiveButton("Yes, Stop", (dialog, which) -> quitMonitoringSession())
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void quitMonitoringSession() {
        String monitoringId = currentUserId + "_" + activeTargetId;

        db.collection("monitoring").document(monitoringId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Monitoring session document deleted successfully.");

                    // Remove from local storage
                    MonitoringState.removeMonitoringTarget(this, activeTargetId);

                    Toast.makeText(this, "Stopped monitoring.", Toast.LENGTH_SHORT).show();

                    // Reload targets
                    loadMonitoringTargets();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error deleting monitoring document", e);
                    Toast.makeText(this, "Error stopping monitoring.", Toast.LENGTH_SHORT).show();
                });
    }

    private void cleanupListeners() {
        Log.d(TAG, "Cleaning up " + listeners.size() + " listeners...");
        for (ListenerRegistration listener : listeners.values()) {
            if (listener != null) {
                listener.remove();
            }
        }
        listeners.clear();
        Log.d(TAG, "✓ All listeners cleaned up");
    }

    private void setupPieChart() {
        if (pieChart == null) {
            Log.e(TAG, "✗ PieChart is NULL!");
            return;
        }
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(true);
        pieChart.setDrawEntryLabels(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setTransparentCircleColor(Color.TRANSPARENT);
        pieChart.setHighlightPerTapEnabled(true);
        Log.d(TAG, "✓ PieChart configured");
    }

    private void setupBudgetListener(String targetId) {
        Log.d(TAG, "--- Setting up BUDGET listener for: " + targetId + " ---");

        ListenerRegistration listener = db.collection("budget").document(targetId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "✗ Error in budget listener for " + targetId, e);
                        setDefaultBudgetValues();
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        Double balance = getDoubleFromSnapshot(snapshot, "balance", "currentBalance", "totalBalance");
                        Double spending = getDoubleFromSnapshot(snapshot, "currentSpending", "spending", "totalSpending", "expenses");

                        double balanceValue = balance != null ? balance : 0.0;
                        double spendingValue = spending != null ? spending : 0.0;

                        Log.d(TAG, "✓ BUDGET DATA RECEIVED:");
                        Log.d(TAG, "  - Balance: " + balanceValue);
                        Log.d(TAG, "  - Current Spending: " + spendingValue);

                        updateBudgetUI(balanceValue, spendingValue);
                    } else {
                        Log.e(TAG, "✗ Budget document does NOT exist for user: " + targetId);
                        setDefaultBudgetValues();
                    }
                });

        listeners.put("budget_" + targetId, listener);
        Log.d(TAG, "✓ Budget listener registered");
    }

    private Double getDoubleFromSnapshot(DocumentSnapshot snapshot, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (snapshot.contains(fieldName)) {
                Object value = snapshot.get(fieldName);
                if (value instanceof Double) {
                    return (Double) value;
                } else if (value instanceof Long) {
                    return ((Long) value).doubleValue();
                } else if (value instanceof Integer) {
                    return ((Integer) value).doubleValue();
                } else if (value instanceof String) {
                    try {
                        return Double.parseDouble((String) value);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Cannot parse string to double: " + value);
                    }
                }
            }
        }
        return null;
    }

    private void setDefaultBudgetValues() {
        updateBudgetUI(0.0, 0.0);
    }

    private void updateBudgetUI(double balance, double spending) {
        runOnUiThread(() -> {
            if (balanceText != null) {
                balanceText.setText(String.format(Locale.US, "RM%.2f", balance));
                Log.d(TAG, "  ✓ Balance text updated to: RM" + balance);
            } else {
                Log.e(TAG, "  ✗ balanceText view is NULL!");
            }

            if (expenseText != null) {
                expenseText.setText(String.format(Locale.US, "RM%.2f", spending));
                Log.d(TAG, "  ✓ Expense text updated to: RM" + spending);
            } else {
                Log.e(TAG, "  ✗ expenseText view is NULL!");
            }
        });
    }

    private void setupExpensesListener(String targetId) {
        Log.d(TAG, "--- Setting up EXPENSES listener for: " + targetId + " ---");

        ListenerRegistration listener = db.collection("expenses")
                .whereEqualTo("userID", targetId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "✗ Error in expenses listener for " + targetId, error);
                        return;
                    }

                    if (snapshots != null && !snapshots.isEmpty()) {
                        Log.d(TAG, "✓ EXPENSES DATA RECEIVED:");
                        Log.d(TAG, "  - Total documents: " + snapshots.size());

                        updatePieChart(snapshots.getDocuments());
                    } else {
                        Log.d(TAG, "✓ No expenses found for user: " + targetId);
                        if (pieChart != null) {
                            runOnUiThread(() -> {
                                pieChart.clear();
                                pieChart.invalidate();
                            });
                        }
                    }
                });

        listeners.put("expenses_" + targetId, listener);
        Log.d(TAG, "✓ Expenses listener registered");
    }

    private void updatePieChart(List<DocumentSnapshot> documents) {
        Log.d(TAG, "--- Updating PieChart with " + documents.size() + " documents ---");

        if (pieChart == null) {
            Log.e(TAG, "✗ PieChart is NULL! Cannot update.");
            return;
        }

        Map<String, Float> categoryTotals = new HashMap<>();
        float totalExpenses = 0;

        for (DocumentSnapshot doc : documents) {
            try {
                String category = doc.getString("category");
                Double amount = doc.getDouble("amount");

                if (amount == null) {
                    amount = doc.getDouble("value");
                    if (amount == null) {
                        Object amountObj = doc.get("amount");
                        if (amountObj instanceof Long) {
                            amount = ((Long) amountObj).doubleValue();
                        } else if (amountObj instanceof Integer) {
                            amount = ((Integer) amountObj).doubleValue();
                        }
                    }
                }

                if (amount != null && category != null) {
                    float floatAmount = amount.floatValue();
                    categoryTotals.merge(category, floatAmount, Float::sum);
                    totalExpenses += floatAmount;
                    Log.d(TAG, "  - Processing: " + category + " - RM" + floatAmount);
                }
            } catch (Exception e) {
                Log.e(TAG, "✗ Error processing document: " + doc.getId(), e);
            }
        }

        Log.d(TAG, "Category totals calculated for pie chart (Total: RM" + totalExpenses + "):");

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (Map.Entry<String, Float> entry : categoryTotals.entrySet()) {
            if (entry.getValue() > 0) {
                entries.add(new PieEntry(entry.getValue(), entry.getKey()));
                colors.add(getColorForCategory(entry.getKey()));
            }
        }

        runOnUiThread(() -> {
            if (entries.isEmpty()) {
                Log.d(TAG, "No entries to display, clearing chart");
                pieChart.clear();
                pieChart.invalidate();
                return;
            }

            PieDataSet set = new PieDataSet(entries, "");
            set.setColors(colors);
            set.setValueTextColor(Color.BLACK);
            set.setValueTextSize(12f);
            PieData pieData = new PieData(set);
            pieData.setDrawValues(false);
            pieChart.setData(pieData);
            pieChart.invalidate();

            Log.d(TAG, "✓ PieChart updated with " + entries.size() + " categories");
        });
    }

    private void fetchLatestTransactions(String targetId) {
        Log.d(TAG, "--- Fetching LATEST TRANSACTIONS for: " + targetId + " ---");

        ListenerRegistration listener = db.collection("expenses")
                .whereEqualTo("userID", targetId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(5)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "✗ Error loading transactions for " + targetId, error);
                        return;
                    }

                    if (snapshots != null && transactionList != null && latestTransactionsAdapter != null) {
                        List<Transaction> newTransactions = new ArrayList<>();
                        Log.d(TAG, "✓ LATEST TRANSACTIONS DATA RECEIVED (" + snapshots.size() + " docs):");

                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            try {
                                String description = doc.getString("description");
                                String category = doc.getString("category");
                                Double amount = doc.getDouble("amount");
                                Date date = doc.getDate("timestamp");

                                if (date == null) {
                                    Object timestampObj = doc.get("timestamp");
                                    if (timestampObj instanceof Long) {
                                        date = new Date((Long) timestampObj);
                                    }
                                }

                                if (description != null && category != null && amount != null && date != null) {
                                    newTransactions.add(new Transaction(description, category, amount, date));
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "✗ Error processing transaction: " + doc.getId(), e);
                            }
                        }

                        runOnUiThread(() -> {
                            transactionList.clear();
                            transactionList.addAll(newTransactions);
                            latestTransactionsAdapter.notifyDataSetChanged();
                            Log.d(TAG, "  ✓ Adapter notified with " + transactionList.size() + " transactions");
                        });
                    }
                });
        listeners.put("latest_transactions_" + targetId, listener);
        Log.d(TAG, "✓ Latest transactions listener registered.");
    }

    private void setupCategoryAmountListeners(String targetId) {
        Log.d(TAG, "--- Setting up CATEGORY AMOUNT listeners for: " + targetId + " ---");
        String[] categories = {"Food & Beverage", "Essential Goods", "Education", "Technology", "Clothing", "Health & Personal Care"};
        int[] amountViewIds = {R.id.food_beverage_amount, R.id.essential_goods_amount, R.id.education_amount, R.id.technology_amount, R.id.clothing_amount, R.id.health_personal_care_amount};
        int[] dateViewIds = {R.id.food_beverage_date, R.id.essential_goods_date, R.id.education_date, R.id.technology_date, R.id.clothing_date, R.id.health_personal_care_date};

        for (int i = 0; i < categories.length; i++) {
            listenForCategoryUpdates(targetId, categories[i], amountViewIds[i], dateViewIds[i]);
        }
        Log.d(TAG, "✓ All category listeners setup complete");
    }

    private void listenForCategoryUpdates(String userId, String category, int amountViewId, int dateViewId) {
        TextView amountTextView = findViewById(amountViewId);
        TextView dateTextView = findViewById(dateViewId);

        if (amountTextView == null || dateTextView == null) {
            Log.w(TAG, "✗ Views not found for category: " + category);
            return;
        }

        ListenerRegistration listener = db.collection("expenses")
                .whereEqualTo("userID", userId)
                .whereEqualTo("category", category)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "✗ Error for category " + category + ": " + error.getMessage());
                        setDefaultCategoryValues(amountTextView, dateTextView);
                        return;
                    }

                    double latestAmount = 0.0;
                    Date latestDate = null;

                    if (snapshots != null && !snapshots.isEmpty()) {
                        DocumentSnapshot latestDoc = snapshots.getDocuments().get(0);
                        try {
                            Double amount = latestDoc.getDouble("amount");
                            if (amount == null) {
                                amount = latestDoc.getDouble("value");
                            }
                            if (amount != null) {
                                latestAmount = amount;
                            }
                            Date date = latestDoc.getDate("timestamp");
                            if (date == null) {
                                Object timestampObj = latestDoc.get("timestamp");
                                if (timestampObj instanceof Long) {
                                    date = new Date((Long) timestampObj);
                                }
                            }
                            latestDate = date;

                            Log.d(TAG, "✓ " + category + " updated - Latest Amount: RM" + latestAmount + ", Date: " + latestDate);
                        } catch (Exception e) {
                            Log.e(TAG, "✗ Error processing latest document in category " + category, e);
                        }
                    } else {
                        Log.d(TAG, "  " + category + " - No transactions found");
                    }

                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                    String dateStr = (latestDate != null) ? sdf.format(latestDate) : getString(R.string.no_transaction);

                    double finalLatestAmount = latestAmount;
                    runOnUiThread(() -> {
                        amountTextView.setText(String.format(Locale.US, "RM%.2f", finalLatestAmount));
                        dateTextView.setText(dateStr);
                    });
                });

        listeners.put("category_" + category + "_" + userId, listener);
    }

    private void setDefaultCategoryValues(TextView amountView, TextView dateView) {
        runOnUiThread(() -> {
            amountView.setText(R.string.rm0_00);
            dateView.setText(R.string.no_transaction);
        });
    }

    private void setupCategoryClickListeners() {
        findViewById(R.id.food_beverage_card).setOnClickListener(v -> openDetailActivity("Food & Beverage"));
        findViewById(R.id.essential_goods_card).setOnClickListener(v -> openDetailActivity("Essential Goods"));
        findViewById(R.id.education_card).setOnClickListener(v -> openDetailActivity("Education"));
        findViewById(R.id.technology_card).setOnClickListener(v -> openDetailActivity("Technology"));
        findViewById(R.id.clothing_card).setOnClickListener(v -> openDetailActivity("Clothing"));
        findViewById(R.id.health_personal_care_card).setOnClickListener(v -> openDetailActivity("Health & Personal Care"));
    }

    private void openDetailActivity(String category) {
        Intent intent = new Intent(this, MonitoredExpenseDetailActivity.class);
        intent.putExtra("CATEGORY", category);
        intent.putExtra("TARGET_USER_ID", activeTargetId);
        startActivity(intent);
    }

    private int getColorForCategory(String categoryName) {
        if (categoryName == null) return Color.parseColor("#BDBDBD");
        return switch (categoryName) {
            case "Food & Beverage" -> Color.parseColor("#FFA726");
            case "Essential Goods" -> Color.parseColor("#66BB6A");
            case "Education" -> Color.parseColor("#29B6F6");
            case "Technology" -> Color.parseColor("#EF5350");
            case "Clothing" -> Color.parseColor("#AB47BC");
            case "Health & Personal Care" -> Color.parseColor("#78909C");
            default -> Color.parseColor("#BDBDBD");
        };
    }

    private void goToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void goToLoginActivity() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh when returning from adding a new person
        if (targetUserIds.isEmpty() || activeTargetId == null) {
            loadMonitoringTargets();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupListeners();
        Log.d(TAG, "=== MultiMonitoringActivity onDestroy ===");
    }
}
