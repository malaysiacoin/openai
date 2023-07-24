package com.example.locdet;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.android.PolyUtil;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class DistanceCalculatorActivity extends AppCompatActivity implements OnMapReadyCallback {

    // Constants
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int MAX_LOCATIONS = 5;
    private static final String API_KEY = "AIzaSyDZc-Y-Hn1TlIP6B-CafypFRkKecOQyxIk";
    private ImageButton btnDeleteInput;

    // Variables
    private PlacesClient placesClient;
    private GeoApiContext geoApiContext;
    private GoogleMap mMap;
    private Polyline routePolyline;
    private List<com.google.android.libraries.places.api.model.AutocompletePrediction> predictions;
    private List<ClearableAutoCompleteTextView> locationInputList;
    private List<ImageButton> clearInputButtonList;
    private LinearLayout locationInputsLayout;
    private Button btnAddLocation;
    private Button btnCalculateDistances;
    private Button btnCalculateDistance;
    private TextView tvRouteInfo;
    private ImageButton btnReorderLocations;
    private String firstLocationInputText;
    // Bounds for Places Autocomplete
    private LatLng southwestBounds = new LatLng(1.164922, 99.132537);
    private LatLng northeastBounds = new LatLng(7.189464, 119.638731);
    private Button btnClearAll;
    private MapView mapView;
    private LatLngBounds.Builder builder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_distance_calculator);

        // Mendapatkan instance dari SupportMapFragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapView);

        // Mendapatkan instance dari GoogleMap
        mapFragment.getMapAsync(this);  // `this` refers to the OnMapReadyCallback, which is implemented in this activity

        // Initialize the Places SDK
        Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        placesClient = Places.createClient(this);

        // Initialize the UI components
        locationInputsLayout = findViewById(R.id.locationInputsLayout);
        btnAddLocation = findViewById(R.id.btnAddLocation);
        btnReorderLocations = findViewById(R.id.btnReorderLocations);
        btnCalculateDistance = findViewById(R.id.btnCalculateDistance);
        btnClearAll = new Button(this);
        btnClearAll.setText("Clear All");
        tvRouteInfo = findViewById(R.id.tvRouteInfo);

        // Initialize the Maps SDK
        MapsInitializer.initialize(this);

        initializePlacesAndMaps(); // Initialize the Places API and Maps

        initializeViewElements(); // Initialize the view elements
        initializeButtonListeners(); // Set up the button listeners
        initializeLocationInputs(); // Set up the location input boxes

        // Contoh titik-titik yang akan ditambahkan ke dalam LatLngBounds.Builder
        LatLng point1 = new LatLng(37.7749, -122.4194);
        LatLng point2 = new LatLng(34.0522, -118.2437);

        // Buat LatLngBounds.Builder dan tambahkan titik-titik ke dalamnya
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(point1);
        builder.include(point2);

        // Bangun LatLngBounds dari builder yang sudah ditambahkan titik-titik
        LatLngBounds bounds = builder.build();
    }


    public boolean checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Tampilkan penjelasan kepada pengguna, jika perlu. Atau minta izin
            return false;
        } else {
            return true;
        }
    }
    private void initializePlacesAndMaps() {
        // Initialize the Places API with your API key
        Places.initialize(getApplicationContext(), API_KEY);
        placesClient = Places.createClient(this);
        geoApiContext = new GeoApiContext.Builder().apiKey(API_KEY).build();
    }
    private void initializeViewElements() {
        locationInputsLayout = findViewById(R.id.locationInputsLayout);
        locationInputList = new ArrayList<>(); // Inisialisasi list di sini
        clearInputButtonList = new ArrayList<>();
        btnAddLocation = findViewById(R.id.btnAddLocation); // This is a Button
        tvRouteInfo = findViewById(R.id.tvRouteInfo);
        btnReorderLocations = findViewById(R.id.btnReorderLocations); // This is an ImageButton
    }

    private void initializeLocationInputs() {
        // Initialize firstLocationInputText with the text of the first location input
        firstLocationInputText = locationInputList.get(0).getText().toString();

        // Set up the adapter for AutoCompleteTextViews
        PlacesAutoCompleteAdapter autoCompleteAdapter = new PlacesAutoCompleteAdapter(this, placesClient, southwestBounds, northeastBounds);

        // Assign the adapter to each AutoCompleteTextView
        for (ClearableAutoCompleteTextView locationInput : locationInputList) {
            locationInput.setAdapter(autoCompleteAdapter);
        }
    }

    private void initializeButtonListeners() {

        // Initialize the location input list with two Location AutoCompleteTextViews
        addNewLocationInput(false); // Location A
        addNewLocationInput(false); // Location B
        btnCalculateDistance.setOnClickListener(v -> {
            // Get the location inputs from user
            List<String> locations = getLocationInputs();

            // Check if the location inputs are valid and distinct
            if (areValidLocationInputs(locations) && areDistinctLocationInputs(locations)) {
                // Fetch the LatLng for these locations
                getLocationLatLng(locations, new OnLocationLatLngListener() {
                    @Override
                    public void onLocationsFound(List<LatLng> latLngs) {
                        // Calculate the route between these LatLngs
                        calculateRoute(latLngs);
                    }

                    @Override
                    public void onError(Exception e) {
                        // Handle error
                        Toast.makeText(DistanceCalculatorActivity.this, "Error fetching location: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                // Show an error message if the inputs are not valid
                Toast.makeText(DistanceCalculatorActivity.this, "Please enter valid and distinct locations.", Toast.LENGTH_LONG).show();
            }
        });

        btnAddLocation.setOnClickListener(v -> {
            // Check if the maximum number of additional locations is reached
            if (locationInputList.size() < MAX_LOCATIONS) {
                // Add a new AutoCompleteTextView for the additional location with the delete button visible
                addNewLocationInput(true);
            } else {
                // Display a message or disable the "Add Location" button if the limit is reached
                Toast.makeText(DistanceCalculatorActivity.this, "Maximum additional locations reached.", Toast.LENGTH_SHORT).show();
                btnAddLocation.setEnabled(false);
            }
        });
        btnReorderLocations.setOnClickListener(v -> showReorderDialog()); // ini baris 158
    }

    private void renameLocationInputs() {
        for (int i = 0; i < locationInputList.size(); i++) {
            locationInputList.get(i).setHint("Location " + (char) ('A' + i));
        }
    }

    private void addNewLocationInput(boolean showDeleteButton) {
        // Inflate the location_input_item layout
        View locationInputItem = LayoutInflater.from(this).inflate(R.layout.location_input_item, null);

        // Find the delete button in the inflated layout
        btnDeleteInput = locationInputItem.findViewById(R.id.deleteLocation);

        // Set the visibility of the delete button based on showDeleteButton
        if (showDeleteButton) {
            btnDeleteInput.setVisibility(View.VISIBLE);
        } else {
            btnDeleteInput.setVisibility(View.GONE);
        }

        ClearableAutoCompleteTextView locationInput = locationInputItem.findViewById(R.id.locationInput);

        // Set up the AutoCompleteTextView
        locationInput.setAdapter(new PlacesAutoCompleteAdapter(this, placesClient, southwestBounds, northeastBounds));
        locationInput.setOnItemClickListener((parent, view, position, id) -> {
            // Hide the dropdown after an item is selected
            locationInput.dismissDropDown();
        });

        // Set up the delete input button
        btnDeleteInput.setOnClickListener(v -> {
            // Only remove the input box if there are more than 2
            if (locationInputList.size() > 2) {
                // Remove the view from the parent layout
                locationInputsLayout.removeView(locationInputItem);

                // Remove the AutoCompleteTextView and the clear button from the lists
                locationInputList.remove(locationInput);
                // Rename the location inputs
                renameLocationInputs();
                // If there are only 2 location inputs left, hide the delete button for Location B
                if (locationInputList.size() == 2) {
                    ImageButton btnDeleteInputB = locationInputsLayout.getChildAt(1).findViewById(R.id.deleteLocation);
                    btnDeleteInputB.setVisibility(View.GONE);
                }
                // Re-enable the "Add Location" button if the total number of location input boxes is less than MAX_LOCATIONS
                if (locationInputList.size() < MAX_LOCATIONS) {
                    btnAddLocation.setEnabled(true);
                }
            }
        });

        // Hide the delete button if showDeleteButton is false
        if (!showDeleteButton) {
            btnDeleteInput.setVisibility(View.GONE);
        }

        // Add the new location input item to the layout and the lists
        locationInputsLayout.addView(locationInputItem);
        locationInputList.add(locationInput);

        // Added: Log the size of the list after adding the new location input
        Log.d("LocationInputListSize", "Size: " + locationInputList.size());
    }

    private List<String> getLocationInputs() {
        List<String> locations = new ArrayList<>();
        for (ClearableAutoCompleteTextView locationInput : locationInputList) {
            locations.add(locationInput.getText().toString());
        }
        return locations;
    }

    private boolean areValidLocationInputs(List<String> locations) {
        for (String location : locations) {
            if (TextUtils.isEmpty(location)) {
                return false;
            }
        }
        return true;
    }

    private boolean areDistinctLocationInputs(List<String> locations) {
        List<String> distinctLocations = new ArrayList<>();
        for (String location : locations) {
            if (!distinctLocations.contains(location)) {
                distinctLocations.add(location);
            }
        }
        return distinctLocations.size() == locations.size();
    }

    private String sanitizeLocationInput(String location) {
        return location.trim().toLowerCase();
    }

    private int getLocationIndex(String location) {
        location = sanitizeLocationInput(location);
        for (int i = 0; i < locationInputList.size(); i++) {
            if (sanitizeLocationInput(locationInputList.get(i).getText().toString()).equals(location)) {
                return i;
            }
        }
        return -1;
    }

    private void getLocationLatLng(List<String> locations, OnLocationLatLngListener listener) {
        // Create a list to store the LatLng objects for each location
        List<LatLng> latLngs = new ArrayList<>();

        // Use a counter to keep track of the number of successful responses
        AtomicInteger counter = new AtomicInteger(0);

        // Loop through each location and fetch its LatLng using Places API
        for (String location : locations) {
            FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                    .setQuery(location)
                    .setLocationBias(RectangularBounds.newInstance(southwestBounds, northeastBounds))
                    .build();

            placesClient.findAutocompletePredictions(request)
                    .addOnSuccessListener(response -> {
                        if (response != null && response.getAutocompletePredictions().size() > 0) {
                            // Get the first prediction and fetch the place details
                            com.google.android.libraries.places.api.model.AutocompletePrediction prediction = response.getAutocompletePredictions().get(0);
                            FetchPlaceRequest placeRequest = FetchPlaceRequest.builder(prediction.getPlaceId(), Arrays.asList(Place.Field.LAT_LNG)).build();
                            placesClient.fetchPlace(placeRequest)
                                    .addOnSuccessListener(placeResponse -> {
                                        Place place = placeResponse.getPlace();
                                        // Add the LatLng of the location to the list
                                        latLngs.add(new LatLng(place.getLatLng().latitude, place.getLatLng().longitude));
                                        // Increase the successful response counter
                                        int successCount = counter.incrementAndGet();
                                        // Check if all locations have been processed
                                        if (successCount == locations.size()) {
                                            // All locations have been fetched successfully, notify the listener
                                            listener.onLocationsFound(latLngs); // Pass all location's LatLng
                                        }
                                    })
                                    .addOnFailureListener(exception -> {
                                        // Handle failure, decrease the counter to indicate an error
                                        counter.decrementAndGet();
                                        // Check if all locations have been processed
                                        if (counter.get() == locations.size() - 1) {
                                            // At least one location failed to fetch, notify the listener
                                            listener.onError(exception);
                                        }
                                    });
                        } else {
                            // Handle empty response, decrease the counter to indicate an error
                            counter.decrementAndGet();
                            // Check if all locations have been processed
                            if (counter.get() == locations.size() - 1) {
                                // At least one location failed to fetch, notify the listener
                                listener.onError(new Exception("No predictions found."));
                            }
                        }
                    })
                    .addOnFailureListener(exception -> {
                        // Handle failure, decrease the counter to indicate an error
                        counter.decrementAndGet();
                        // Check if all locations have been processed
                        if (counter.get() == locations.size() - 1) {
                            // At least one location failed to fetch, notify the listener
                            listener.onError(exception);
                        }
                    });
        }
    }
    public interface OnLocationLatLngListener {
        void onLocationsFound(List<LatLng> latLngs);
        void onError(Exception e);
    }

    private void handleFetchPlaceSuccess(String location, FindAutocompletePredictionsResponse response, OnLocationLatLngListener listener, String placeId) {
        FetchPlaceRequest placeRequest = FetchPlaceRequest.builder(placeId, Arrays.asList(Place.Field.LAT_LNG)).build();
        if (response != null && response.getAutocompletePredictions().size() > 0) {
            predictions = response.getAutocompletePredictions(); // Store the predictions in the 'predictions' list

            // Fetch place details using FetchPlaceRequest
            placesClient.fetchPlace(placeRequest)
                    .addOnSuccessListener((placeResponse) -> {
                        Place place = placeResponse.getPlace();

                        if (listener != null) {
                            listener.onLocationsFound(Collections.singletonList(place.getLatLng()));
                        }

                        // Reset the text of the first input after fetching place details for other locations
                        if (getLocationIndex(location) == 0) {
                            locationInputList.get(0).setText(firstLocationInputText);
                        }
                    })
                    .addOnFailureListener((exception) -> {
                        if (listener != null) {
                            listener.onError(exception);
                        }

                        // Reset the text of the first input after fetching place details for other locations
                        if (getLocationIndex(location) == 0) {
                            locationInputList.get(0).setText(firstLocationInputText);
                        }
                    });
        } else {
            if (listener != null) {
                listener.onLocationsFound(Collections.emptyList());
            }

            // Reset the text of the first input after fetching place details for other locations
            if (getLocationIndex(location) == 0) {
                locationInputList.get(0).setText(firstLocationInputText);
            }
        }
    }

    private void handleFetchPlaceFailure(String location, OnLocationLatLngListener listener) {
        if (listener != null) {
            listener.onLocationsFound(Collections.emptyList()); // Update the method name here
        }

        // Reset the text of the first input after fetching place details for other locations
        if (getLocationIndex(location) == 0) {
            locationInputList.get(0).setText(firstLocationInputText);
        }
    }


    private void calculateRoute(List<LatLng> latLngs) {
        com.google.maps.model.LatLng[] waypoints = new com.google.maps.model.LatLng[latLngs.size() - 2]; // Exclude the start and end locations from the waypoints
        for (int i = 1; i < latLngs.size() - 1; i++) {
            waypoints[i - 1] = new com.google.maps.model.LatLng(latLngs.get(i).latitude, latLngs.get(i).longitude);
        }

        try {
            DirectionsResult directionsResult = DirectionsApi.newRequest(geoApiContext)
                    .origin(new com.google.maps.model.LatLng(latLngs.get(0).latitude, latLngs.get(0).longitude))
                    .destination(new com.google.maps.model.LatLng(latLngs.get(latLngs.size() - 1).latitude, latLngs.get(latLngs.size() - 1).longitude))
                    .waypoints(waypoints)
                    .await();

            if (directionsResult != null && directionsResult.routes.length > 0) {
                DirectionsRoute route = directionsResult.routes[0];

                // Draw the route on the map
                PolylineOptions polylineOptions = new PolylineOptions();
                polylineOptions.color(Color.RED);
                polylineOptions.width(10);
                polylineOptions.addAll(PolyUtil.decode(route.overviewPolyline.getEncodedPath()));
                routePolyline = mMap.addPolyline(polylineOptions);

                // Display route information
                long totalDistance = 0;
                long totalDuration = 0;
                for (DirectionsLeg leg : route.legs) {
                    totalDistance += leg.distance.inMeters;
                    totalDuration += leg.duration.inSeconds;
                }

                setRouteInfoTextView(totalDistance, totalDuration);

                // Move the camera to show the route
                builder = new LatLngBounds.Builder();
                for (LatLng latLng : latLngs) {
                    builder.include(latLng);
                }
            }
        } catch (InterruptedException | IOException | ApiException e) {
            e.printStackTrace();
        }
    }

    private void setRouteInfoTextView(long totalDistance, long totalDuration) {
        // Convert the total duration to hours and minutes
        long hours = totalDuration / 3600;
        long minutes = (totalDuration % 3600) / 60;

        String routeInfo;
        if (hours > 0) {
            // If the total duration is more than or equal to 1 hour, display hours and minutes
            routeInfo = String.format(Locale.ENGLISH, "Total distance: %.2f km\nTotal duration: %d hrs %d mins",
                    totalDistance / 1000.0, hours, minutes);
        } else {
            // If the total duration is less than 1 hour, only display minutes
            routeInfo = String.format(Locale.ENGLISH, "Total distance: %.2f km\nTotal duration: %d mins",
                    totalDistance / 1000.0, minutes);
        }

        tvRouteInfo.setText(routeInfo);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Enable zoom controls
        googleMap.getUiSettings().setZoomControlsEnabled(true);

        // Enable compass
        googleMap.getUiSettings().setCompassEnabled(true);

        // Enable my location button
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Enable rotate gestures
        googleMap.getUiSettings().setRotateGesturesEnabled(true);

        // Enable tilt gestures
        googleMap.getUiSettings().setTiltGesturesEnabled(true);

        // Check if the app has access to fine location
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Request for the permission if it has not been granted yet
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Enable My Location layer and controls if the permission has been granted
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        }

        // Get the fragment's root view
        final View mapView = getSupportFragmentManager().findFragmentById(R.id.mapView).getView();

        // Inisialisasi builder di sini
        builder = new LatLngBounds.Builder();

        // Mendapatkan ViewTreeObserver dan menambahkan OnGlobalLayoutListener
        if (mapView.getViewTreeObserver().isAlive()) {
            mapView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    // At this point the layout is complete and the
                    // dimensions of map view are known.

                    if (mMap != null && builder != null) {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
                    }

                    // remove the listener... or we'll be getting this callback every layout pass
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    } else {
                        mapView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    }
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            // Check if the permission has been granted
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Enable My Location layer and controls
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    mMap.setMyLocationEnabled(true);
                    mMap.getUiSettings().setMyLocationButtonEnabled(true);
                }
            } else {
                // Display a toast message if the permission is not granted
                Toast.makeText(this, "Permission to access location was denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showReorderDialog() {
        // Inflate the reorder locations layout
        View reorderLocationsLayout = getLayoutInflater().inflate(R.layout.reorder_locations_layout, null);
        ListView reorderLocationsList = reorderLocationsLayout.findViewById(R.id.reorderLocationsList);

        // Create and set the adapter for the reorder locations list
        List<String> locationNames = new ArrayList<>();
        for (ClearableAutoCompleteTextView locationInput : locationInputList) {
            locationNames.add(locationInput.getText().toString());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, locationNames);
        reorderLocationsList.setAdapter(adapter);

        // Show the reorder locations dialog
        new AlertDialog.Builder(this)
                .setTitle("Reorder Locations")
                .setView(reorderLocationsLayout)
                .setPositiveButton("OK", (dialog, which) -> {
                    // Update the location inputs according to the reordered locations
                    List<String> reorderedLocations = new ArrayList<>();
                    for (int i = 0; i < adapter.getCount(); i++) {
                        reorderedLocations.add(adapter.getItem(i));
                    }

                    for (int i = 0; i < locationInputList.size(); i++) {
                        locationInputList.get(i).setText(reorderedLocations.get(i));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
