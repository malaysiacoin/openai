package com.example.locdet;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.android.PolyUtil;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class DistanceCalculatorActivity extends AppCompatActivity implements OnMapReadyCallback {
    private AutoCompleteTextView locationInput1;
    private AutoCompleteTextView locationInput2;
    private TextView distanceResult;
    private ImageButton clearInput1; // Declare clearInput1
    private ImageButton clearInput2; // Declare clearInput2
    private static final int MAX_LOCATIONS = 10;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private LinearLayout locationInputsLayout;
    private List<ClearableAutoCompleteTextView> locationInputList;
    private List<ImageButton> clearInputButtonList;
    private GoogleMap mMap;
    private PlacesClient placesClient;
    private Button btnCalculateDistances;
    private Polyline routePolyline;
    private GeoApiContext geoApiContext;

    private int selectedLocationIndex = -1; // Initialize with an invalid value
    private class LocationInfo {
        String name;
        double latitude;
        double longitude;
    }
    private List<LocationInfo> locationsList;

    private int additionalLocationCount = 0;
    private TextView tvRouteInfo;
    private Button btnAddLocation;

    private ImageButton btnReorderLocations;
    private LatLng southwestBounds = new LatLng(6.2662, 99.7297); // Replace these values with the actual southwest coordinates of the desired bounds.
    private LatLng northeastBounds = new LatLng(6.4453, 99.8580); // Replace these values with the actual northeast coordinates of the desired bounds.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_distance_calculator);

        locationInputsLayout = findViewById(R.id.locationInputsLayout);
        Button btnAddLocation = findViewById(R.id.btnAddLocation);
        btnAddLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addLocationInput(null);
            }
        });

        btnCalculateDistances = findViewById(R.id.btnCalculateDistances);
        btnCalculateDistances.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calculateDistances();
            }
        });

        locationsList = new ArrayList<>();

        addLocationInput(null);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        mapFragment.getMapAsync(this);
    }

    private void showReorderDialog() {
        // Create a dialog to allow the user to reorder the locations
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reorder Locations");

        List<String> locations = getLocationInputs();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, locations);
        builder.setAdapter(adapter, (dialog, which) -> {
            // Swap the selected location with the first location
            if (which != 0) {
                String selectedLocation = locations.get(which);
                locations.remove(which);
                locations.add(0, selectedLocation);
                updateLocationInputs(locations);
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateLocationInputs(List<String> locations) {
        // Update the location inputs with the new order
        for (int i = 0; i < locations.size(); i++) {
            locationInputList.get(i).setText(locations.get(i));
        }
    }
    private boolean checkLocationPermissions() {
        // Check if the app has location permissions
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissions() {
        // Request location permissions if not granted
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Location permissions granted, proceed with the operation
                btnCalculateDistances.performClick();
            } else {
                Toast.makeText(this, "Location permissions required to calculate distances.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private List<String> getLocationInputs() {
        // Get all the location inputs from AutoCompleteTextViews
        List<String> locations = new ArrayList<>();
        for (AutoCompleteTextView locationInput : locationInputList) {
            String location = locationInput.getText().toString().trim();
            if (!TextUtils.isEmpty(location)) {
                locations.add(location);
            }
        }
        return locations;
    }

    private boolean areValidLocationInputs(List<String> locations) {
        // Check if at least two valid locations are provided
        if (locations.size() < 2) {
            Toast.makeText(this, "Please provide at least two valid locations.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private boolean areDistinctLocationInputs(List<String> locations) {
        // Check if all the locations are distinct from each other
        for (int i = 0; i < locations.size(); i++) {
            String locationA = locations.get(i);
            for (int j = i + 1; j < locations.size(); j++) {
                String locationB = locations.get(j);
                if (locationA.equalsIgnoreCase(locationB)) {
                    Toast.makeText(this, "Please provide distinct locations.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        }
        return true;
    }

    private String sanitizeLocationInput(String location) {
        // Perform any necessary sanitization on the location input before using it
        return location.trim();
    }

    private void getLocationLatLng(String location, OnLocationLatLngListener listener) {
        // Get latitude and longitude of the given location using Places API
        List<Place.Field> placeFields = Arrays.asList(Place.Field.LAT_LNG);
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setQuery(location)
                .setLocationBias(RectangularBounds.newInstance(new LatLng(6.2662, 99.7297), new LatLng(6.4453, 99.8580)))
                .build();

        // Store the text of the first input before fetching place details for other locations
        String firstLocationInputText = locationInputList.get(0).getText().toString();

        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener((response) -> {
                    if (response != null && response.getAutocompletePredictions().size() > 0) {
                        AutocompletePrediction prediction = response.getAutocompletePredictions().get(0);

                        // Fetch place details using FetchPlaceRequest
                        FetchPlaceRequest placeRequest = FetchPlaceRequest.builder(prediction.getPlaceId(), placeFields).build();
                        placesClient.fetchPlace(placeRequest)
                                .addOnSuccessListener((placeResponse) -> {
                                    Place place = placeResponse.getPlace();
                                    // Check if the provided location is in the locationInputList
                                    int locationIndex = getLocationIndex(location);
                                    if (locationIndex != -1) {
                                        // Set the selected place name into the appropriate AutoCompleteTextView input
                                        locationInputList.get(locationIndex).setText(place.getName());
                                    }
                                    if (listener != null) {
                                        listener.onLocationLatLngReceived(place.getLatLng());
                                    }

                                    // Reset the text of the first input after fetching place details for other locations
                                    if (locationIndex == 0) {
                                        locationInputList.get(0).setText(firstLocationInputText);
                                    }
                                })
                                .addOnFailureListener((exception) -> {
                                    if (listener != null) {
                                        listener.onLocationLatLngReceived(null);
                                    }

                                    // Reset the text of the first input after fetching place details for other locations
                                    if (getLocationIndex(location) == 0) {
                                        locationInputList.get(0).setText(firstLocationInputText);
                                    }
                                });
                    } else {
                        if (listener != null) {
                            listener.onLocationLatLngReceived(null);
                        }

                        // Reset the text of the first input after fetching place details for other locations
                        if (getLocationIndex(location) == 0) {
                            locationInputList.get(0).setText(firstLocationInputText);
                        }
                    }
                })
                .addOnFailureListener((exception) -> {
                    if (listener != null) {
                        listener.onLocationLatLngReceived(null);
                    }

                    // Reset the text of the first input after fetching place details for other locations
                    if (getLocationIndex(location) == 0) {
                        locationInputList.get(0).setText(firstLocationInputText);
                    }
                });
    }

    private int getLocationIndex(String location) {
        for (int i = 0; i < locationInputList.size(); i++) {
            AutoCompleteTextView locationInput = locationInputList.get(i);
            String inputLocation = locationInput.getText().toString().trim();
            if (inputLocation.equalsIgnoreCase(location)) {
                return i;
            }
        }
        return -1; // Location not found in the list
    }

    private void calculateDistances() {
        // Ensure that the locationsLatLng list is not empty and contains valid coordinates.
        if (locationsLatLng != null && !locationsLatLng.isEmpty()) {

            // Create LatLngBounds to include all the LatLng points.
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            for (LatLng latLng : locationsLatLng) {
                boundsBuilder.include(latLng);
            }
            LatLngBounds bounds = boundsBuilder.build();

            // Calculate padding to provide space around the bounds.
            int padding = 50; // Adjust the padding value as needed to provide space around the bounds.

            // Animate the camera to show all the LatLng points with padding.
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));

            try {
                LatLng origin = locationsLatLng.get(0);
                LatLng destination = locationsLatLng.get(locationsLatLng.size() - 1);
                List<LatLng> waypoints = locationsLatLng.subList(1, locationsLatLng.size() - 1);

                // Convert LatLng objects to com.google.maps.model.LatLng for the Directions API
                com.google.maps.model.LatLng originLatLng = new com.google.maps.model.LatLng(origin.latitude, origin.longitude);
                com.google.maps.model.LatLng destinationLatLng = new com.google.maps.model.LatLng(destination.latitude, destination.longitude);
                com.google.maps.model.LatLng[] waypointsLatLng = new com.google.maps.model.LatLng[waypoints.size()];
                for (int i = 0; i < waypoints.size(); i++) {
                    LatLng waypoint = waypoints.get(i);
                    waypointsLatLng[i] = new com.google.maps.model.LatLng(waypoint.latitude, waypoint.longitude);
                }

                DirectionsResult directionsResult = DirectionsApi.newRequest(geoApiContext)
                        .origin(originLatLng)
                        .destination(destinationLatLng)
                        .waypoints(waypointsLatLng)
                        .await(); // This line can throw ApiException, InterruptedException, or IOException

                if (directionsResult != null && directionsResult.routes.length > 0) {
                    DirectionsRoute route = directionsResult.routes[0];
                    List<com.google.android.gms.maps.model.LatLng> decodedPath = PolyUtil.decode(route.overviewPolyline.getEncodedPath());

                    PolylineOptions polylineOptions = new PolylineOptions()
                            .addAll(decodedPath)
                            .color(Color.BLUE)
                            .width(8f);

                    routePolyline = mMap.addPolyline(polylineOptions);

                    // Move camera to the bounds of the route
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), padding));
                    // Calculate and display the distance between the locations
                    double totalDistance = 0;
                    for (DirectionsLeg leg : route.legs) {
                        totalDistance += leg.distance.inMeters;
                    }
                    double totalDistanceInKm = totalDistance / 1000;
                    String distanceString = String.format(Locale.getDefault(), "%.2f km", totalDistanceInKm);
                    tvRouteInfo.setText("Total Distance: " + distanceString);
                } else {
                    Toast.makeText(this, "No route found.", Toast.LENGTH_SHORT).show();
                    tvRouteInfo.setText("");
                }
            } catch (com.google.maps.errors.ApiException | InterruptedException | IOException e) {
                e.printStackTrace();
                // Handle the API exception here
                Toast.makeText(this, "Error occurred: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                tvRouteInfo.setText("");
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.setMinZoomPreference(5.0f);
        mMap.setMaxZoomPreference(18.0f);

        LatLng defaultLocation = new LatLng(6.3500, 99.8000); // Default location (Langkawi coordinates)
        mMap.addMarker(new MarkerOptions().position(defaultLocation).title("Default Location"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(defaultLocation));
    }

    // Function to create a new AutoCompleteTextView input for location and add it to the LinearLayout
    private void createNewLocationInput() {
        if (locationInputList.size() < MAX_LOCATIONS) {
            View view = getLayoutInflater().inflate(R.layout.location_input_item, null);
            ClearableAutoCompleteTextView locationInput = view.findViewById(R.id.locationInput);


            locationInputList.add(locationInput);

            locationInputsLayout.addView(view);

            // Enable the "Add Location" button if it was previously disabled due to reaching the maximum limit
            btnAddLocation.setEnabled(true);
        }
    }

    private void addLocationInput(LocationInfo location) {
        if (locationInputList.size() < MAX_LOCATIONS) {
            View view = getLayoutInflater().inflate(R.layout.location_input_item, null);
            ClearableAutoCompleteTextView locationInput = view.findViewById(R.id.locationInput);
            ImageButton deleteLocationButton = view.findViewById(R.id.deleteLocation);

            // Set the adapter for the newly created ClearableAutoCompleteTextView
            PlacesAutoCompleteAdapter autoCompleteAdapter = new PlacesAutoCompleteAdapter(this, placesClient, southwestBounds, northeastBounds);
            locationInput.setAdapter(autoCompleteAdapter);


            // For inputs other than the default location
            deleteLocationButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int index = locationInputList.indexOf(locationInput);
                    if (index > 1) { // we don't want to delete the first two inputs
                        locationInputList.remove(index); // Remove the corresponding ClearableAutoCompleteTextView input from the list
                        clearInputButtonList.remove(index); // Remove the corresponding clear button from the list
                        locationInputsLayout.removeView(view); // Remove the entire view from the layout

                        // Enable the "Add Location" button after removing an input
                        btnAddLocation.setEnabled(true);
                    }
                }
            });

            // Hide the delete button for the first two inputs
            if (locationInputList.size() < 2) {
                deleteLocationButton.setVisibility(View.GONE);
            }

            locationInputList.add(locationInput);
            locationInputsLayout.addView(view);
        }
    }

    // Function to clear all AutoCompleteTextView inputs for locations
    public void clearAllLocationInputs() {
        for (AutoCompleteTextView locationInput : locationInputList) {
            locationInput.setText("");
        }
    }

    // Interface for receiving location latitude and longitude
    private interface OnLocationLatLngListener {
        void onLocationLatLngReceived(LatLng latLng);
    }
}
