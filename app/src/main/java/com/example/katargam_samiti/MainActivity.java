package com.example.katargam_samiti;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    EditText name;
    Button btnCapture;
    ImageView imageView;
    private static final int CAMERA_REQUEST_CODE = 100;
    private StorageReference storageRef;
    private DatabaseReference databaseRef;
    private String userName;  // Store the name after entering it once
    private Bitmap imageBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        name = findViewById(R.id.editTextText);
        btnCapture = findViewById(R.id.button);
        imageView = findViewById(R.id.imageView);

        // Initialize Firebase
        storageRef = FirebaseStorage.getInstance().getReference();
        databaseRef = FirebaseDatabase.getInstance().getReference();

        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                userName = name.getText().toString();
                if (userName.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please enter your name", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Check if the user is "Rajubhai321"
                if (userName.equals("Rajubhai321")) {
                    // Launch ImageGalleryActivity
                    Intent intent = new Intent(MainActivity.this, ImageGalleryActivity.class);
                    intent.putExtra("userName", userName);
                    startActivity(intent);
                } else {
                    // Normal flow for capturing and uploading image
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(intent, CAMERA_REQUEST_CODE);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Capture image as Bitmap
            imageBitmap = (Bitmap) data.getExtras().get("data");

            // Show the image in ImageView
            imageView.setImageBitmap(imageBitmap);

            // Show confirmation dialog
            uploadImageToFirebase();
        }
    }

    private void uploadImageToFirebase() {
        // Convert Bitmap to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] data = baos.toByteArray();

        // Create a unique filename for the image
        String fileName = "image_" + System.currentTimeMillis() + ".jpg";

        // Set Firebase Storage path as user's name
        StorageReference userImageRef = storageRef.child("images/").child(userName).child(fileName);

        // Upload the image to the user's folder in Firebase Storage
        UploadTask uploadTask = userImageRef.putBytes(data);
        uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                // Get the download URL from the user's folder upload
                userImageRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        String downloadUrl = uri.toString();
                        incrementImageCountInDatabase();
                        // Also upload the image to "Rajubhai321" folder
                        uploadImageToRajubhai321Folder(fileName, data);

                        saveImageUrlToDatabase(downloadUrl);
                    }
                });
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveImageUrlToDatabase(String imageUrl) {
        // Store the image URL with a timestamp
        DatabaseReference userImagesRef = databaseRef.child("Users").child("Rajubhai321");

        // Generate a unique key for each image (e.g., image1, image2, etc.)
        String imageKey = userImagesRef.push().getKey();

        if (imageKey != null) {
            long timestamp = System.currentTimeMillis(); // Capture current timestamp
            HashMap<String, Object> imageData = new HashMap<>();
            imageData.put("url", imageUrl);
            imageData.put("timestamp", timestamp); // Save the timestamp

            userImagesRef.child("images").child(imageKey).setValue(imageData)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            updateImageCount();
//                            Toast.makeText(MainActivity.this, "Image successfully uploaded and saved!", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(MainActivity.this, "Failed to save image URL: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void updateImageCount() {
        // Reference to the user's count in the database
        DatabaseReference countRef = databaseRef.child("Users").child("Rajubhai321").child("imageCount");

        // Use a transaction to increment the count safely
        countRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Integer currentCount = mutableData.getValue(Integer.class);

                if (currentCount == null) {
                    // Initialize count to 1 if it doesn't exist
                    mutableData.setValue(1);
                } else {
                    // Increment the count by 1
                    mutableData.setValue(currentCount + 1);
                }
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean committed, @Nullable DataSnapshot dataSnapshot) {
                if (databaseError != null) {
                    Toast.makeText(MainActivity.this, "Failed to update image count: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // This function uploads the same image to the "Rajubhai321" folder
    private void uploadImageToRajubhai321Folder(String fileName, byte[] data) {
        // Set Firebase Storage path for "Rajubhai321"
        StorageReference rajubhai321ImageRef = storageRef.child("images/").child("Rajubhai321").child(fileName);

        // Upload the image to the "Rajubhai321" folder in Firebase Storage
        UploadTask uploadTask = rajubhai321ImageRef.putBytes(data);
        uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
//                Toast.makeText(MainActivity.this, "Image also uploaded to Rajubhai321 folder!", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "Failed to upload image to Rajubhai321 folder: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void incrementImageCountInDatabase() {
        // Reference to the user's node in the Realtime Database
        DatabaseReference userRef = databaseRef.child("Users").child(userName).child("imageCount");

        // Increment the image count for the user who captured the photo
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Get current image count; if null, initialize to 0
                long currentCount = dataSnapshot.exists() ? (long) dataSnapshot.getValue() : 0;

                // Increment the count for the user who captured the image
                userRef.setValue(currentCount + 1)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                Toast.makeText(MainActivity.this, "Image was uploaded successfully!", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(MainActivity.this, "Failed to update image count: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(MainActivity.this, "Failed to read image count: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
