package com.example.stackt;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String userId;
    private PieChart pieChart;

    private RecyclerView latestTransactionsRecycler;
    private LatestTransactionsAdapter latestTransactionsAdapter;
    private List<Transaction> transactionList;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted.
                } else {
                    Toast.makeText(this, "Notifications will not be shown.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        userId = currentUser.getUid();

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.navigation_home);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                // Already here
                return true;
            } else if (itemId == R.id.navigation_monitor) {
                if (MonitoringState.isMonitoring(this)) {
                    startActivity(new Intent(this, MultiMonitoringActivity.class));
                } else {
                    startActivity(new Intent(this, MonitoringLoginActivity.class));
                }
                finish();
                return true;
            } else if (itemId == R.id.navigation_geo) {
                startActivity(new Intent(this, GeolocationActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.navigation_scan) {
                startActivity(new Intent(this, OCRActivity.class));
                finish();
                return true;
            }
            return false;
        });

        pieChart = findViewById(R.id.piechart);
        TextView welcomeText = findViewById(R.id.welcome_text);
        setupPieChart();

        if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
            welcomeText.setText("Hi, " + currentUser.getDisplayName());
        } else {
            welcomeText.setText(getString(R.string.hi_user));
        }

        ImageView profileIcon = findViewById(R.id.profile_icon);
        ImageView editBalanceIcon = findViewById(R.id.edit_balance_icon);
        ImageView notificationBell = findViewById(R.id.notification_bell);

        profileIcon.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        editBalanceIcon.setOnClickListener(v -> showEditBalanceDialog(db.collection("budget").document(userId)));
        notificationBell.setOnClickListener(v -> startActivity(new Intent(this, NotificationActivity.class)));

        FloatingActionButton fabScanReceipt = findViewById(R.id.fab_scan_receipt);
        fabScanReceipt.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, OCRActivity.class);
            intent.putExtra("OPEN_CAMERA_IMMEDIATELY", true);
            startActivity(intent);
        });

        latestTransactionsRecycler = findViewById(R.id.latest_transactions_recycler);
        latestTransactionsRecycler.setLayoutManager(new LinearLayoutManager(this));
        transactionList = new ArrayList<>();
        latestTransactionsAdapter = new LatestTransactionsAdapter(transactionList);
        latestTransactionsRecycler.setAdapter(latestTransactionsAdapter);

        setupBudgetCurrentSpendingListeners(db.collection("budget").document(userId));
        setupCategoryAmountListeners();
        setupCategoryClickListeners();
        fetchLatestTransactions();

        askNotificationPermission();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
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
        Intent intent = new Intent(MainActivity.this, ExpenseHistoryDetailActivity.class);
        intent.putExtra("CATEGORY", category);
        startActivity(intent);
    }

    private void setupBudgetCurrentSpendingListeners(DocumentReference userBudgetRef) {
        TextView balanceText = findViewById(R.id.text_balance);
        TextView expenseText = findViewById(R.id.text_expenses);
        userBudgetRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                Double balance = snapshot.getDouble("balance");
                Double expense = snapshot.getDouble("currentSpending");
                balanceText.setText(String.format(Locale.US, "RM%.2f", (balance != null) ? balance : 0.0));
                expenseText.setText(String.format(Locale.US, "RM%.2f", (expense != null) ? expense : 0.0));
            } else {
                balanceText.setText(getString(R.string.rm0_00));
                expenseText.setText(getString(R.string.rm0_00));
            }
        });

        db.collection("expenses").whereEqualTo("userID", userId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) return;
                    if (snapshots != null) updatePieChart(snapshots.getDocuments());
                });
    }

    private void fetchLatestTransactions() {
        db.collection("expenses")
                .whereEqualTo("userID", userId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        android.util.Log.e("MainActivity", "Error loading transactions: " + error.getMessage());
                        return;
                    }
                    if (snapshots != null) {
                        transactionList.clear();

                        List<DocumentSnapshot> allDocs = new ArrayList<>(snapshots.getDocuments());

                        allDocs.sort((doc1, doc2) -> {
                            com.google.firebase.Timestamp ts1 = doc1.getTimestamp("timestamp");
                            com.google.firebase.Timestamp ts2 = doc2.getTimestamp("timestamp");
                            if (ts1 == null || ts2 == null) return 0;
                            return ts2.compareTo(ts1);
                        });

                        int limit = Math.min(5, allDocs.size());
                        for (int i = 0; i < limit; i++) {
                            DocumentSnapshot doc = allDocs.get(i);
                            String description = doc.getString("description");
                            String category = doc.getString("category");
                            Double amount = doc.getDouble("amount");
                            Date date = doc.getDate("timestamp");

                            if (description != null && category != null && amount != null && date != null) {
                                transactionList.add(new Transaction(description, category, amount, date));
                            }
                        }
                        latestTransactionsAdapter.notifyDataSetChanged();
                    }
                });
    }

    private void setupPieChart() {
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(true);
        pieChart.setDrawEntryLabels(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.TRANSPARENT);
    }

    private void updatePieChart(List<DocumentSnapshot> documents) {
        Map<String, Float> categoryTotals = new HashMap<>();
        for (DocumentSnapshot doc : documents) {
            String category = doc.getString("category");
            Double amount = doc.getDouble("amount");
            if (amount == null) continue;
            float floatAmount = amount.floatValue();
            categoryTotals.merge(category, floatAmount, Float::sum);
        }
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (Map.Entry<String, Float> entry : categoryTotals.entrySet()) {
            if (entry.getValue() > 0) {
                entries.add(new PieEntry(entry.getValue(), entry.getKey()));
                colors.add(getColorForCategory(entry.getKey()));
            }
        }
        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(colors);
        set.setValueTextColor(Color.BLACK);
        set.setValueTextSize(12f);
        PieData pieData = new PieData(set);
        pieData.setDrawValues(false);
        pieChart.setData(pieData);
        pieChart.invalidate();
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

    private void setupCategoryAmountListeners() {
        listenForLatestExpense("Food & Beverage", R.id.food_beverage_amount, R.id.food_beverage_date);
        listenForLatestExpense("Essential Goods", R.id.essential_goods_amount, R.id.essential_goods_date);
        listenForLatestExpense("Education", R.id.education_amount, R.id.education_date);
        listenForLatestExpense("Technology", R.id.technology_amount, R.id.technology_date);
        listenForLatestExpense("Clothing", R.id.clothing_amount, R.id.clothing_date);
        listenForLatestExpense("Health & Personal Care", R.id.health_personal_care_amount, R.id.health_personal_care_date);
    }

    private void listenForLatestExpense(String category, int amountViewId, int dateViewId) {
        TextView amountTextView = findViewById(amountViewId);
        TextView dateTextView = findViewById(dateViewId);
        db.collection("expenses")
                .whereEqualTo("userID", userId)
                .whereEqualTo("category", category)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        android.util.Log.e("MainActivity", "Error for category " + category + ": " + error.getMessage());
                        return;
                    }
                    if (snapshots != null && !snapshots.isEmpty()) {
                        DocumentSnapshot doc = snapshots.getDocuments().get(0);
                        Double amount = doc.getDouble("amount");
                        com.google.firebase.Timestamp timestamp = doc.getTimestamp("timestamp");
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                        String date = (timestamp != null) ? sdf.format(timestamp.toDate()) : getString(R.string.no_transaction);
                        amountTextView.setText(String.format(Locale.US, "RM%.2f", amount));
                        dateTextView.setText(date);
                    } else {
                        amountTextView.setText(getString(R.string.rm0_00));
                        dateTextView.setText(getString(R.string.no_transaction));
                    }
                });
    }

    private void showEditBalanceDialog(DocumentReference userBudgetRef) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Balance");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Enter new balance");
        builder.setView(input);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) {
                Toast.makeText(this, "Please enter a value", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                double newBalance = Double.parseDouble(value);
                Map<String, Object> updates = new HashMap<>();
                updates.put("balance", newBalance);
                updates.put("balanceDuration", FieldValue.serverTimestamp());
                userBudgetRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener(unused -> Toast.makeText(this, "Balance updated!", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(err -> Toast.makeText(this, "Failed to update: " + err.getMessage(), Toast.LENGTH_SHORT).show());
            } catch (NumberFormatException ex) {
                Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
