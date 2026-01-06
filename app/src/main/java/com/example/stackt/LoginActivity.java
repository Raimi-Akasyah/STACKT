package com.example.stackt;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnSignIn, btnGoogle;
    private TextView tvSignUp;

    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn = findViewById(R.id.btnSignIn);
        btnGoogle = findViewById(R.id.btnGoogle);
        tvSignUp = findViewById(R.id.tvSignUp);

        mAuth = FirebaseAuth.getInstance();

        setupGoogleSignIn();

        btnSignIn.setOnClickListener(v -> loginWithEmailPassword());
        btnGoogle.setOnClickListener(v -> signInWithGoogle());
        tvSignUp.setOnClickListener(v -> showSignUpBottomSheet());
    }

    private void loginWithEmailPassword() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        updateUserInFirestore(user, null);
                    } else {
                        String msg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(this, "Login failed: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            if (account != null) firebaseAuthWithGoogle(account);
                        } catch (ApiException e) {
                            Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    private void signInWithGoogle() {
        googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
    }

    private void firebaseAuthWithGoogle(@NonNull GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                updateUserInFirestore(user, null);
            } else {
                String msg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                Toast.makeText(this, "Auth failed: " + msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUserInFirestore(FirebaseUser firebaseUser, String name) {
        if (firebaseUser == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = firebaseUser.getUid();

        Map<String, Object> userData = new HashMap<>();
        if (name != null && !name.isEmpty()) {
            userData.put("name", name);

            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();
            firebaseUser.updateProfile(profileUpdates);

        } else if (firebaseUser.getDisplayName() != null) {
            userData.put("name", firebaseUser.getDisplayName());
        }

        if (firebaseUser.getEmail() != null) {
            userData.put("email", firebaseUser.getEmail().toLowerCase());
        }

        db.collection("user").document(uid).set(userData, SetOptions.merge())
            .addOnSuccessListener(aVoid -> {
                goToMain();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(LoginActivity.this, "Failed to save user data.", Toast.LENGTH_SHORT).show();
                goToMain();
            });
    }

    private void showSignUpBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_signup, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        EditText etSignUpName = bottomSheetView.findViewById(R.id.etSignUpName);
        EditText etSignUpEmail = bottomSheetView.findViewById(R.id.etSignUpEmail);
        EditText etSignUpPassword = bottomSheetView.findViewById(R.id.etSignUpPassword);
        Button btnPerformSignUp = bottomSheetView.findViewById(R.id.btnPerformSignUp);

        btnPerformSignUp.setOnClickListener(v -> {
            String name = etSignUpName.getText().toString().trim();
            String email = etSignUpEmail.getText().toString().trim();
            String password = etSignUpPassword.getText().toString().trim();

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            updateUserInFirestore(user, name);
                            bottomSheetDialog.dismiss();
                        } else {
                            String msg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            Toast.makeText(this, "Sign up failed: " + msg, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        bottomSheetDialog.show();
    }


    private void goToMain() {
        // THIS IS THE DIAGNOSTIC CODE
        String projectId = FirebaseApp.getInstance().getOptions().getProjectId();
        Toast.makeText(this, "Connected to Project ID: " + projectId, Toast.LENGTH_LONG).show();

        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) goToMain();
    }
}
