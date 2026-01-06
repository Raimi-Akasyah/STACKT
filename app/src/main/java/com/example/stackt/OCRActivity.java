package com.example.stackt;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class OCRActivity extends AppCompatActivity {

    private ImageView imgReceipt;
    private EditText etStoreName, etDate, etTotal;
    private Spinner spinnerCategory;
    private Button btnSaveReceipt;
    private ImageView infoIcon;

    private String currentPhotoPath;
    private Uri photoURI;
    private static final int PERMISSION_REQUEST = 101;
    private static final int MAX_RETRY_COUNT = 12; // 12 retries * 5 seconds = 60 seconds max
    private int retryCount = 0;

    private ActivityResultLauncher<Uri> takePictureLauncher;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private final String TABSCANNER_API_KEY = "E2OrSnZLgsgzERAUsMcZThGAKgVGQIJU7hQ6MF5RjCaQ0x7YYp4pG7wIQYqpeKR6";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.navigation_scan);
        
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
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
                return true;
            }
            return false;
        });

        initializeViews();
        setupCameraLauncher();
        populateCategorySpinner();
        setupClickListeners();

        if (getIntent().getBooleanExtra("OPEN_CAMERA_IMMEDIATELY", false)) {
            checkPermissionsAndOpenCamera();
        }
    }

    private void initializeViews() {
        imgReceipt = findViewById(R.id.imgReceipt);
        etStoreName = findViewById(R.id.etStoreName);
        etDate = findViewById(R.id.etDate);
        etTotal = findViewById(R.id.etTotal);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        btnSaveReceipt = findViewById(R.id.btnSaveReceipt);
        infoIcon = findViewById(R.id.info_icon);
    }

    private void populateCategorySpinner() {
        String[] categories = {"Expense Category", "Food & Beverage", "Essential Goods", "Education", "Technology", "Clothing", "Health & Personal Care"};
        List<String> categoryList = Arrays.asList(categories);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, categoryList);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);
    }

    private void setupCameraLauncher() {
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                result -> {
                    if (result) {
                        File imageFile = new File(currentPhotoPath);
                        if (imageFile.exists()) {
                            Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);
                            imgReceipt.setImageBitmap(bitmap);
                            uploadImageToTabScanner(imageFile);
                        } else {
                            Toast.makeText(this, "Error: Image file not found.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Camera cancelled. You can retry or enter details manually.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkPermissionsAndOpenCamera() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= 33) {
            permissions = new String[]{Manifest.permission.CAMERA};
        } else {
            permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            openCamera();
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to scan receipts.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupClickListeners() {
        imgReceipt.setOnClickListener(v -> checkPermissionsAndOpenCamera());
        infoIcon.setOnClickListener(v -> showInfoPopup());
        btnSaveReceipt.setOnClickListener(v -> openVerificationScreen());
        etDate.setOnClickListener(v -> showDatePickerDialog());
    }

    private void showDatePickerDialog() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year1, monthOfYear, dayOfMonth);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    etDate.setText(sdf.format(selectedDate.getTime()));
                }, year, month, day);
        datePickerDialog.show();
    }


    private void openVerificationScreen() {
        String storeName = etStoreName.getText().toString().trim();
        String date = etDate.getText().toString().trim();
        String total = etTotal.getText().toString().trim();
        String category = spinnerCategory.getSelectedItem().toString();

        if (storeName.isEmpty() || date.isEmpty() || total.isEmpty() || category.equals("Expense Category")) {
            Toast.makeText(this, "Please fill all fields and select a category.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Intent intent = new Intent(this, VerificationActivity.class);
            intent.putExtra("STORE_NAME", storeName);
            intent.putExtra("DATE", date);
            intent.putExtra("TOTAL", total);
            intent.putExtra("CATEGORY", category);
            if (photoURI != null) {
                intent.putExtra("IMAGE_URI", photoURI.toString());
            }
            startActivity(intent);
        } catch (Exception e) {
            Log.e("OCRActivity", "Error starting VerificationActivity: " + e.getMessage(), e);
        }
    }

    private void showInfoPopup() {
        new AlertDialog.Builder(this)
                .setTitle("Category Information")
                .setMessage("Food & Beverage = KFC\n"
                        + "Essential Goods = MR DIY, AEON, and LOTUS\n"
                        + "Education = First Touch\n"
                        + "Technology = DELL, HP, AND ASUS\n"
                        + "Clothing = H&M\n"
                        + "Health & Personal Care = Pharmacy")
                .setPositiveButton("OK", null)
                .show();
    }

    private void openCamera() {
        try {
            File photoFile = createImageFile();
            photoURI = FileProvider.getUriForFile(this, "com.example.stackt.fileprovider", photoFile);
            takePictureLauncher.launch(photoURI);
        } catch (IOException e) {
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String imageFileName = "receipt_" + System.currentTimeMillis();
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void uploadImageToTabScanner(File imageFile) {
        retryCount = 0; 
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Processing receipt...", Toast.LENGTH_SHORT).show());

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("documentType", "receipt")
                .addFormDataPart("file", imageFile.getName(),
                        RequestBody.create(imageFile, MediaType.parse("image/jpeg")))
                .build();

        Request request = new Request.Builder()
                .url("https://api.tabscanner.com/api/2/process")
                .addHeader("apikey", TABSCANNER_API_KEY)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("OCRActivity", "Upload failed: " + e.getMessage(), e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody responseBody = response.body();
                if (responseBody == null) return;

                String responseString = responseBody.string();
                if (!response.isSuccessful()) return;

                try {
                    JSONObject json = new JSONObject(responseString);
                    boolean success = json.optBoolean("success", false);
                    if (!success) return;

                    if (json.has("token")) {
                        String token = json.getString("token");
                        new Handler(Looper.getMainLooper()).postDelayed(() -> getResultFromTabScanner(token), 5000);
                    }
                } catch (JSONException e) {
                    Log.e("OCRActivity", "JSON parsing error: " + e.getMessage(), e);
                }
            }
        });
    }

    private void getResultFromTabScanner(String token) {
        if (retryCount >= MAX_RETRY_COUNT) {
            runOnUiThread(() -> Toast.makeText(OCRActivity.this, "Processing timeout. Please try again.", Toast.LENGTH_LONG).show());
            return;
        }

        retryCount++;
        Request request = new Request.Builder()
                .url("https://api.tabscanner.com/api/result/" + token)
                .addHeader("apikey", TABSCANNER_API_KEY)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                 Log.e("OCRActivity", "Result fetch failed: " + e.getMessage(), e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody responseBody = response.body();
                if (responseBody == null) return;

                String responseString = responseBody.string();
                if (!response.isSuccessful()) return;

                try {
                    JSONObject json = new JSONObject(responseString);
                    String status = json.optString("status", "");

                    if ("done".equalsIgnoreCase(status) && json.has("result")) {
                        JSONObject result = json.getJSONObject("result");
                        runOnUiThread(() -> {
                            parseTabScannerResult(result);
                            retryCount = 0;
                        });
                    } else if ("pending".equalsIgnoreCase(status) || "processing".equalsIgnoreCase(status)) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> getResultFromTabScanner(token), 5000);
                    } 
                } catch (JSONException e) {
                    Log.e("OCRActivity", "Result JSON parsing error: " + e.getMessage(), e);
                }
            }
        });
    }

    private void parseTabScannerResult(JSONObject result) {
        try {
            String establishment = result.optString("establishment", "");
            String dateStr = result.optString("date", "");
            String totalStr = result.optString("total", "");

            if (!establishment.isEmpty()) etStoreName.setText(establishment);
            if (!dateStr.isEmpty()) {
                String[] dateParts = dateStr.split(" ");
                if (dateParts.length > 0) {
                    etDate.setText(dateParts[0]);
                } else {
                    etDate.setText(dateStr);
                }
            }
            if (!totalStr.isEmpty()) etTotal.setText(totalStr);

            Toast.makeText(this, "Receipt data extracted successfully!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e("OCRActivity", "Failed to parse result JSON", e);
        }
    }
}