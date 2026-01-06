package com.example.stackt;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class VerificationActivity extends AppCompatActivity {

    private ImageView imgReceiptPreview;
    private TextView tvStoreName, tvDate, tvTotal, tvCategory;
    private Button btnBackToEdit, btnConfirmSave;

    private FirebaseFirestore db;
    private String userId;

    private String storeName, dateStr, totalStr, category, imageUriStr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verification);

        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
        } else {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        getIntentData();
        populateViews();
        setupClickListeners();
    }

    private void initializeViews() {
        imgReceiptPreview = findViewById(R.id.imgReceiptPreview);
        tvStoreName = findViewById(R.id.tvStoreName);
        tvDate = findViewById(R.id.tvDate);
        tvTotal = findViewById(R.id.tvTotal);
        tvCategory = findViewById(R.id.tvCategory);
        btnBackToEdit = findViewById(R.id.btnBackToEdit);
        btnConfirmSave = findViewById(R.id.btnConfirmSave);
    }

    private void getIntentData() {
        Intent intent = getIntent();
        storeName = intent.getStringExtra("STORE_NAME");
        dateStr = intent.getStringExtra("DATE");
        totalStr = intent.getStringExtra("TOTAL");
        category = intent.getStringExtra("CATEGORY");
        imageUriStr = intent.getStringExtra("IMAGE_URI");
    }

    private void populateViews() {
        tvStoreName.setText(storeName);
        tvDate.setText(dateStr);
        tvTotal.setText(String.format("RM%s", totalStr));
        tvCategory.setText(category);

        // Only show image if URI exists
        if (imageUriStr != null && !imageUriStr.isEmpty()) {
            imgReceiptPreview.setImageURI(Uri.parse(imageUriStr));
        } else {
            // Hide image or show placeholder if no photo was taken
            imgReceiptPreview.setImageResource(R.drawable.images); // Use default placeholder
        }
    }

    private void setupClickListeners() {
        btnBackToEdit.setOnClickListener(v -> {
            // Return to OCRActivity to edit
            setResult(RESULT_CANCELED);
            finish();
        });
        btnConfirmSave.setOnClickListener(v -> saveExpenseToFirebase());
    }

    private void saveExpenseToFirebase() {
        btnConfirmSave.setEnabled(false); // Prevent multiple clicks

        if (storeName == null || dateStr == null || totalStr == null || category == null ||
                storeName.isEmpty() || dateStr.isEmpty() || totalStr.isEmpty() || category.equals("Expense Category")) {
            Toast.makeText(this, "Some data is invalid, please go back and edit.", Toast.LENGTH_LONG).show();
            btnConfirmSave.setEnabled(true);
            return;
        }

        double totalAmount;
        try {
            String cleanTotal = totalStr.replaceAll("[^\\d.]", "");
            totalAmount = Double.parseDouble(cleanTotal);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid total amount format.", Toast.LENGTH_SHORT).show();
            btnConfirmSave.setEnabled(true);
            return;
        }

        Date parsedDate;
        try {
            // Accommodate different possible date formats from OCR
            if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) { // YYYY-MM-DD
                parsedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr);
            } else if (dateStr.matches("\\d{2}/\\d{2}/\\d{4}")) { // MM/DD/YYYY
                parsedDate = new SimpleDateFormat("MM/dd/yyyy", Locale.US).parse(dateStr);
            } else if (dateStr.matches("\\d{2}-\\d{2}-\\d{4}")) { // DD-MM-YYYY
                parsedDate = new SimpleDateFormat("dd-MM-yyyy", Locale.US).parse(dateStr);
            } else {
                // Try a more general format as a fallback
                parsedDate = new SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(dateStr);
            }
        } catch (ParseException e) {
            Toast.makeText(this, "Invalid date format. Please edit to yyyy-MM-dd.", Toast.LENGTH_LONG).show();
            btnConfirmSave.setEnabled(true);
            return;
        }

        Map<String, Object> expense = new HashMap<>();
        expense.put("userID", userId);
        expense.put("description", storeName);
        expense.put("amount", totalAmount);
        expense.put("category", category);
        expense.put("timestamp", new com.google.firebase.Timestamp(parsedDate));

        db.collection("expenses").add(expense)
                .addOnSuccessListener(documentReference -> {
                    updateUserBalanceAndSpending(totalAmount, category)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(VerificationActivity.this, "Receipt saved successfully!", Toast.LENGTH_SHORT).show();

                                // Signal success back to OCRActivity
                                setResult(RESULT_OK);

                                // Go to MainActivity
                                Intent intent = new Intent(VerificationActivity.this, MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(VerificationActivity.this, "Receipt saved, but failed to update budget.", Toast.LENGTH_LONG).show();
                                btnConfirmSave.setEnabled(true);
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(VerificationActivity.this, "Error saving receipt: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnConfirmSave.setEnabled(true);
                });
    }

    private Task<Void> updateUserBalanceAndSpending(double expenseAmount, String category) {
        DocumentReference budgetRef = db.collection("budget").document(userId);
        String categoryFieldName = getCategoryFieldName(category);

        // Atomically increment spending and decrement balance
        return budgetRef.update(
                "currentSpending", FieldValue.increment(expenseAmount),
                "balance", FieldValue.increment(-expenseAmount),
                categoryFieldName, FieldValue.increment(-expenseAmount)
        );
    }

    private String getCategoryFieldName(String category) {
        return switch (category) {
            case "Food & Beverage" -> "food_beverage";
            case "Essential Goods" -> "essential_goods";
            case "Education" -> "education";
            case "Technology" -> "technology";
            case "Clothing" -> "clothing";
            case "Health & Personal Care" -> "health_personal_care";
            default -> "";
        };
    }
}
