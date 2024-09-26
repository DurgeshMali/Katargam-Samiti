package com.example.katargam_samiti;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.annotations.Nullable;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImageGalleryActivity extends AppCompatActivity {
    private ViewPager2 viewPager2;
    private ImageAdapter imageAdapter;
    private List<String> imageUrls;
    private StorageReference storageRef;
    private DatabaseReference databaseRef;
    private int newImagesCount = 0; // For notifying new images
    private int lastViewedPosition = -1; // To track the last viewed position
    private boolean initialLoadCompleted = false; // Flag to check initial load
    private Set<String> seenImages = new HashSet<>(); // To track already seen images
    private List<String> unseenImages = new ArrayList<>(); // For tracking new unseen images
    String userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_gallery);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        viewPager2 = findViewById(R.id.viewPager);
        imageUrls = new ArrayList<>();
        imageAdapter = new ImageAdapter(imageUrls);
        viewPager2.setAdapter(imageAdapter);

        userName = getIntent().getStringExtra("userName");

        // Initialize Firebase storage and real-time database reference
        storageRef = FirebaseStorage.getInstance().getReference().child("images/Rajubhai321");
        databaseRef = FirebaseDatabase.getInstance().getReference().child("Users/Rajubhai321/images");

        // Fetch URLs from Firebase Realtime Database instead of Firebase Storage
        if (!initialLoadCompleted) {
            fetchExistingImages();
        }

        // Real-time listener for new image additions
        listenForNewImages();

        // Listen for page changes to track when user views a new images
        viewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                // When the user views a new image, remove it from unseenImages and decrease the count
                String viewedImage = imageUrls.get(position);
                if (unseenImages.contains(viewedImage)) {
                    unseenImages.remove(viewedImage);
                    newImagesCount = unseenImages.size(); // Update the count by 1
                    lastViewedPosition = position;
                    Log.d("ImageGalleryActivity", "Unseen images remaining: " + newImagesCount);
                }
            }
        });
    }

    private void fetchExistingImages() {
        databaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                imageUrls.clear(); // Clear to avoid duplicates
                unseenImages.clear(); // Clear unseen images list
                seenImages.clear(); // Clear the seenImages set

                List<ImageData> imageDataList = new ArrayList<>();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    String imageUrl = dataSnapshot.child("url").getValue(String.class);
                    long timestamp = dataSnapshot.child("timestamp").getValue(Long.class);
                    imageDataList.add(new ImageData(imageUrl, timestamp));
                    // Add image to seenImages set as these are already in the list
                    seenImages.add(imageUrl);
                }

                // Sort by timestamp
                Collections.sort(imageDataList, new Comparator<ImageData>() {
                    @Override
                    public int compare(ImageData o1, ImageData o2) {
                        return Long.compare(o1.timestamp, o2.timestamp);
                    }
                });

                // Add sorted URLs to imageUrls list
                for (ImageData imageData : imageDataList) {
                    imageUrls.add(imageData.url);
                }

                imageAdapter.notifyDataSetChanged(); // Notify adapter to update RecyclerView
                initialLoadCompleted = true; // Mark the initial load as completed
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ImageGalleryActivity.this, "Failed to load images: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Create an ImageData class to hold the URL and timestamp
    class ImageData {
        String url;
        long timestamp;

        public ImageData(String url, long timestamp) {
            this.url = url;
            this.timestamp = timestamp;
        }
    }

    private void listenForNewImages() {
        // Listen for new images being added in real-time
        databaseRef.addChildEventListener(new com.google.firebase.database.ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                String newImageUrl = dataSnapshot.child("url").getValue(String.class);

                if (newImageUrl != null) {
                    // If the image is new (not in the seenImages set), add it to the unseen list
                    if (!seenImages.contains(newImageUrl)) {
                        imageUrls.add(newImageUrl);
                        unseenImages.add(newImageUrl); // Add to unseen images
                        newImagesCount = unseenImages.size(); // Update the new images count
                        seenImages.add(newImageUrl); // Mark this image as seen
                    }
                }

                imageAdapter.notifyDataSetChanged(); // Notify adapter about new data

                // Only show the toast when new images are added after the initial load
                if (initialLoadCompleted && newImagesCount > 0) {
                    showNewImagesToast(); // Show toast summarizing new images
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {}

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
        });
    }

    private void showNewImagesToast() {
        // Display the toast message for the count of new images added
        if (userName.equals("Rajubhai321")) {
            Toast.makeText(ImageGalleryActivity.this, newImagesCount + " new photo(s) added", Toast.LENGTH_SHORT).show();
        }
    }
}
