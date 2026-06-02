package com.example.map;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import com.google.android.gms.maps.model.Marker;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.map.databinding.ActivityMapsBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationTokenSource;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    private FusedLocationProviderClient fusedLocationClient;

    private Marker userMarker;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final float DEFAULT_ZOOM = 16f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Toast.makeText(this, "Erreur : fragment map introuvable", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Vérifier si le GPS / localisation est activé
        if (!isLocationEnabled()) {
            buildAlertMessageNoGps();
        }

        // Vérifier les permissions
        if (hasLocationPermission()) {
            enableLocationAndMoveCamera();
        } else {
            requestLocationPermission();
        }
    }

    private boolean hasLocationPermission() {
        boolean finePermission =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;

        boolean coarsePermission =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;

        return finePermission || coarsePermission;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {

            if (hasLocationPermission()) {
                enableLocationAndMoveCamera();
            } else {
                Toast.makeText(
                        this,
                        "Permission de localisation refusée",
                        Toast.LENGTH_SHORT
                ).show();

                moveToDefaultLocation();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void enableLocationAndMoveCamera() {
        if (!hasLocationPermission()) {
            requestLocationPermission();
            return;
        }

        // Afficher le bouton bleu "ma position" sur Google Maps
        mMap.setMyLocationEnabled(true);

        // Essayer d'abord avec la dernière position connue
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        moveCameraToLocation(
                                location.getLatitude(),
                                location.getLongitude(),
                                "Vous êtes ici"
                        );
                    } else {
                        requestFreshLocation();
                    }
                })
                .addOnFailureListener(e -> requestFreshLocation());
    }

    @SuppressLint("MissingPermission")
    private void requestFreshLocation() {
        if (!hasLocationPermission()) {
            requestLocationPermission();
            return;
        }

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();

        fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationTokenSource.getToken()
                )
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        moveCameraToLocation(
                                location.getLatitude(),
                                location.getLongitude(),
                                "Position actuelle"
                        );
                    } else {
                        Toast.makeText(
                                this,
                                "Impossible de récupérer la position actuelle",
                                Toast.LENGTH_SHORT
                        ).show();

                        moveToDefaultLocation();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(
                            this,
                            "Erreur lors de la récupération de la position",
                            Toast.LENGTH_SHORT
                    ).show();

                    moveToDefaultLocation();
                });
    }

    private void moveCameraToLocation(double latitude, double longitude, String title) {
        LatLng position = new LatLng(latitude, longitude);

        if (userMarker == null) {
            userMarker = mMap.addMarker(
                    new MarkerOptions()
                            .position(position)
                            .title(title)
            );
        } else {
            userMarker.setPosition(position);
            userMarker.setTitle(title);
        }

        if (userMarker != null) {
            userMarker.showInfoWindow();
        }

        mMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(position, DEFAULT_ZOOM)
        );
    }

    private void moveToDefaultLocation() {
        moveCameraToLocation(
                31.6295,
                -7.9811,
                "Position par défaut : Marrakech"
        );
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        boolean gpsEnabled = false;
        boolean networkEnabled = false;

        try {
            gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {
        }

        try {
            networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {
        }

        return gpsEnabled || networkEnabled;
    }

    private void buildAlertMessageNoGps() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setMessage("Votre GPS semble désactivé. Voulez-vous l'activer ?")
                .setCancelable(false)
                .setPositiveButton("Oui", (dialog, id) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Non", (dialog, id) -> {
                    dialog.cancel();
                    moveToDefaultLocation();
                });

        AlertDialog alert = builder.create();
        alert.show();
    }
}