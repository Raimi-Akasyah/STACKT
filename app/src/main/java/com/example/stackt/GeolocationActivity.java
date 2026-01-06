package com.example.stackt;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GeolocationActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private FirebaseFirestore db;
    private String userId;
    private double userTotalBalance = 0.0; // total balance from main page

    private final Map<String, Store> stores = new HashMap<>();
    private final Set<String> promptedStores = new HashSet<>(); // Stores the user has been prompted for
    private boolean isDialogShowing = false;

    private TextView balanceFoodBeverage, balanceEssentialGoods, balanceEducation, balanceTechnology,
            balanceClothing, balanceHealthPersonalCare;

    private final Map<String, Double> categoryAmounts = new HashMap<>(); // store current category values

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_geolocation);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        NavigationManager.setup(this, bottomNavigationView, R.id.navigation_geo);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) userId = currentUser.getUid();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        initializeViews();
        fetchBudgetAndCategoryBalances(); // fetch total balance + categories

        Button editExpensesBtn = findViewById(R.id.edit_expenses_btn);
        editExpensesBtn.setOnClickListener(v -> showEditCategoryDialog());

    }

    private void initializeViews() {
        balanceFoodBeverage = findViewById(R.id.balance_food_beverage);
        balanceEssentialGoods = findViewById(R.id.balance_essential_goods);
        balanceEducation = findViewById(R.id.balance_education);
        balanceTechnology = findViewById(R.id.balance_technology);
        balanceClothing = findViewById(R.id.balance_clothing);
        balanceHealthPersonalCare = findViewById(R.id.balance_health_personal_care);
    }

    private void fetchBudgetAndCategoryBalances() {
        if (userId == null) return;
        DocumentReference budgetRef = db.collection("budget").document(userId);
        budgetRef.addSnapshotListener((documentSnapshot, e) -> {
            if (e != null) {
                Log.e("Firestore", "Error fetching budget", e);
                return;
            }
            if (documentSnapshot != null && documentSnapshot.exists()) {
                Double totalBalance = documentSnapshot.getDouble("balance");
                userTotalBalance = totalBalance != null ? totalBalance : 0.0;

                updateCategoryFromSnapshot(documentSnapshot, "Food & Beverage", "food_beverage");
                updateCategoryFromSnapshot(documentSnapshot, "Essential Goods", "essential_goods");
                updateCategoryFromSnapshot(documentSnapshot, "Education", "education");
                updateCategoryFromSnapshot(documentSnapshot, "Technology", "technology");
                updateCategoryFromSnapshot(documentSnapshot, "Clothing", "clothing");
                updateCategoryFromSnapshot(documentSnapshot, "Health & Personal Care", "health_personal_care");
            }
        });
    }

    private void updateCategoryFromSnapshot(DocumentSnapshot snapshot, String categoryName, String fieldName) {
        Double amount = snapshot.getDouble(fieldName);
        categoryAmounts.put(categoryName, amount != null ? amount : 0.0);
        updateCategoryBalance(categoryName, amount);
    }

    private void showEditCategoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Category Expense");

        final String[] categories = {"Food & Beverage", "Essential Goods", "Education", "Technology",
                "Clothing", "Health & Personal Care"};
        final Spinner categorySpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, categories);
        categorySpinner.setAdapter(adapter);

        final EditText amountInput = new EditText(this);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setHint("Enter amount");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(categorySpinner);
        layout.addView(amountInput);
        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String selectedCategory = (String) categorySpinner.getSelectedItem();
            String amountString = amountInput.getText().toString();
            if (amountString.isEmpty()) {
                Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show();
                return;
            }

            double newAmount = Double.parseDouble(amountString);

            double total = newAmount;
            for (String cat : categoryAmounts.keySet()) {
                if (!cat.equals(selectedCategory)) {
                    total += categoryAmounts.get(cat);
                }
            }

            if (total > userTotalBalance) {
                Toast.makeText(this, String.format(Locale.US,
                        "Total categories exceed your balance RM %.2f. Please adjust.", userTotalBalance), Toast.LENGTH_LONG).show();
                return;
            }

            updateCategoryBalanceInFirestore(selectedCategory, newAmount);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void updateCategoryBalanceInFirestore(String category, double amount) {
        if (userId == null) return;
        DocumentReference budgetRef = db.collection("budget").document(userId);
        Map<String, Object> data = new HashMap<>();
        data.put(getCategoryFieldName(category), amount);

        budgetRef.set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Balance updated", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show());
    }

    private void updateCategoryBalance(String category, Double amount) {
        if (amount == null) amount = 0.0;
        String balanceText = String.format(Locale.US, "Balance: RM %.2f", amount);
        switch (category) {
            case "Food & Beverage" -> balanceFoodBeverage.setText(balanceText);
            case "Essential Goods" -> balanceEssentialGoods.setText(balanceText);
            case "Education" -> balanceEducation.setText(balanceText);
            case "Technology" -> balanceTechnology.setText(balanceText);
            case "Clothing" -> balanceClothing.setText(balanceText);
            case "Health & Personal Care" -> balanceHealthPersonalCare.setText(balanceText);
        }
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

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        checkLocationPermissionAndEnable();
    }

    private void fetchLocationsFromFirestore() {
        db.collection("locations").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                stores.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    String placeName = document.getString("placeName");
                    String category = document.getString("category");
                    Double lat = document.getDouble("latitude");
                    Double lng = document.getDouble("longitude");

                    if (placeName != null && category != null && lat != null && lng != null) {
                        LatLng latLng = new LatLng(lat, lng);
                        stores.put(placeName, new Store(placeName, category, latLng));

                        mMap.addMarker(new MarkerOptions().position(latLng).title(placeName).snippet(category));
                        mMap.addCircle(new CircleOptions().center(latLng).radius(30).strokeColor(Color.BLUE).fillColor(Color.argb(70, 0, 0, 255)));
                    } else {
                        Log.w("Firestore", "Skipping location with missing data: " + document.getId());
                    }
                }

                if (stores.isEmpty()) {
                    Toast.makeText(this, "No locations found.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Loaded " + stores.size() + " store markers!", Toast.LENGTH_SHORT).show();
                    startLocationUpdates(); // Start listening for user's location
                }
            } else {
                Log.w("Firestore", "Error getting locations.", task.getException());
            }
        });
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000); // 10 seconds
        locationRequest.setFastestInterval(5000); // 5 seconds
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        checkProximityToStores(location);
                    }
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void checkProximityToStores(Location userLocation) {
        LatLng currentUserLatLng = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
        List<Store> nearbyStores = new ArrayList<>();

        for (Store store : stores.values()) {
            float[] distance = new float[1];
            Location.distanceBetween(currentUserLatLng.latitude, currentUserLatLng.longitude,
                    store.latLng.latitude, store.latLng.longitude, distance);

            if (distance[0] <= 30) { // User is within 30 meters
                if (!promptedStores.contains(store.name)) {
                    nearbyStores.add(store);
                }
            } else {
                promptedStores.remove(store.name);
            }
        }

        if (!nearbyStores.isEmpty() && !isDialogShowing) {
            showStoreSelectionDialog(nearbyStores);
        }
    }

    private void showStoreSelectionDialog(List<Store> nearbyStores) {
        isDialogShowing = true;

        for (Store store : nearbyStores) {
            promptedStores.add(store.name);
        }

        if (nearbyStores.size() == 1) {
            Store store = nearbyStores.get(0);
            new AlertDialog.Builder(this)
                    .setTitle("Store Nearby")
                    .setMessage("Are you currently at " + store.name + "?")
                    .setPositiveButton("Yes", (dialog, which) -> showBudgetInfo(store))
                    .setNegativeButton("No", null)
                    .setOnDismissListener(dialog -> isDialogShowing = false)
                    .show();
        } else {
            String[] storeNames = new String[nearbyStores.size()];
            for (int i = 0; i < nearbyStores.size(); i++) {
                storeNames[i] = nearbyStores.get(i).name;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Which store did you enter?")
                    .setItems(storeNames, (dialog, which) -> {
                        Store selectedStore = nearbyStores.get(which);
                        showBudgetInfo(selectedStore);
                    })
                    .setOnDismissListener(dialog -> isDialogShowing = false)
                    .show();
        }
    }

    private void showBudgetInfo(Store store) {
        Double categoryBalance = categoryAmounts.get(store.category);
        if (categoryBalance == null) {
            categoryBalance = 0.0;
        }

        String message = String.format(Locale.US, "Your budget for %s is RM%.2f",
                store.category, categoryBalance);

        new AlertDialog.Builder(this)
                .setTitle(store.name)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void checkLocationPermissionAndEnable() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            enableMyLocation();
        }
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
                }
                fetchLocationsFromFirestore();
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                fetchLocationsFromFirestore();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private static class Store {
        String name;
        String category;
        LatLng latLng;

        Store(String name, String category, LatLng latLng) {
            this.name = name;
            this.category = category;
            this.latLng = latLng;
        }
    }
}
