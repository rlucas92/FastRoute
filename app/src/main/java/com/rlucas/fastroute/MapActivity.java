package com.rlucas.fastroute;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.IntentService;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AutoCompleteTextView;
import android.widget.SimpleAdapter;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.rlucas.fastroute.placeautocomplete.PlaceJSONParser;
import com.rlucas.fastroute.services.FetchAddressService;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        GoogleMap.OnMapClickListener, GoogleMap.OnMarkerClickListener {

    //Constants
    private static final int REQUEST_RESOLVE_ERROR = 1001;   // Request code to use when launching the resolution activity
    private static final String DIALOG_ERROR = "dialog_error";
    private static final String STATE_RESOLVING_ERROR = "resolving_error";
    private static final int INITIAL_CAMERA_ZOOM = 15;
    private static final String BROWSER_API_KEY = "AIzaSyA5jl1pCH79OSnw0Mg27tR0DW58Y5Nx9iw";

    //Other objects
    private GoogleApiClient apiClient;
    private boolean resolvingError = false;
    private LatLng selectedLatLng;
    private PlacesTask placesTask;
    private Marker selectedLoc;
    private boolean mapReady = false;
    private boolean apiConnected = false;

    //UI Objects
    private GoogleMap gMap;
    private AutoCompleteTextView atvPlaces;

    /***************************************************************************************
     *     Initialization
     **************************************************************************************/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("MapActivity", "onCreate called");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        resolvingError = savedInstanceState != null && savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false);
        selectedLatLng = getIntent().getParcelableExtra(Constants.EXTRA_MAP_LATLNG);

        buildGoogleApiClient();

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        setUpAutoComplete();
    }


    @Override
    public void onMapReady(GoogleMap map) {
        gMap = map;
        gMap.setOnMapClickListener(this);
        gMap.setOnMarkerClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(!resolvingError) {
            apiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        apiClient.disconnect();
        finish();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i("MapActivity", "Connected to Google API Client");
        gMap.setMyLocationEnabled(true);
        if(this.selectedLatLng != null) {
            selectedLoc = gMap.addMarker(new MarkerOptions()
                    .position(selectedLatLng)
                    .draggable(false)
                    .title(getResources().getString(R.string.map_marker_title)));
            selectedLoc.showInfoWindow();
            gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, INITIAL_CAMERA_ZOOM));
        }
        else {
            Location currentLocation = LocationServices.FusedLocationApi.getLastLocation(apiClient);
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, INITIAL_CAMERA_ZOOM));
        }
    }

    /***************************************************************************************
     *     General methods
     **************************************************************************************/

    protected synchronized void buildGoogleApiClient() {
        Log.i("MapActivity", "Building API Client");
        apiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private void setUpAutoComplete() {
        atvPlaces = (AutoCompleteTextView) findViewById(R.id.atv_places);
        atvPlaces.setThreshold(1);
        atvPlaces.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //Not implemented
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                placesTask = new PlacesTask();
                placesTask.execute(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                //Not implemented
            }
        });
    }

    private String downloadUrl(String strUrl) throws IOException{
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try{
            URL url = new URL(strUrl);

            // Creating an http connection to communicate with url
            urlConnection = (HttpURLConnection) url.openConnection();

            // Connecting to url
            urlConnection.connect();

            // Reading data from url
            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while( ( line = br.readLine()) != null){
                sb.append(line);
            }

            data = sb.toString();

            br.close();

        }catch(Exception e){
            Log.d("Exception getting url", e.toString());
        }finally{
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    public void confirmAdd() {
        Log.i("MapActivity", "Add confirmed");
        //Get Address from LatLng
        Intent geoIntent = new Intent(this, FetchAddressService.class);
        geoIntent.putExtra(Constants.EXTRA_MAP_LATLNG, selectedLatLng);
        geoIntent.putExtra(Constants.EXTRA_MAP_RESULTRECEIVER, new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                if (resultCode == Constants.RESULTCODE_SUCCESS) {
                    startEditPlaceActivity((Address) resultData.get(Constants.BUNDLE_MAP_ADDRESS));
                } else {
                    Log.e("MapActivity.confirmAdd", "Error returned from geocoder:\n"
                            + resultData.getString(Constants.BUNDLE_RESULTMESSAGE));
                }
            }
        });
        startService(geoIntent);
    }

    public void startEditPlaceActivity(Address address){
        Log.i("MapActivity", "About to start Edit Place activity");
        //Start Edit Place Activity
        Intent intent = new Intent(this, EditPlaceActivity.class);
        intent.putExtra(Constants.EXTRA_MAP_ADDRESS, address);
        intent.putExtra(Constants.EXTRA_MAP_LATLNG, selectedLatLng);
        startActivity(intent);
    }

    /***************************************************************************************
     *     Callbacks
     **************************************************************************************/

    @Override
    public void onConnectionSuspended(int i) {
        //Do nothing
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (resolvingError) {
            //Already attempting to resolve an error
            return;
        } else if (connectionResult.hasResolution()) {
            try {
                resolvingError = true;
                connectionResult.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                //There was an error with the resoultion intent. Try again.
                apiClient.connect();
            }
        } else {
            //Show dialog using GoogleApiAvailability.getErrorDialog()
            showErrorDialog(connectionResult.getErrorCode());
            resolvingError = true;
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        this.selectedLatLng = latLng;
        if(selectedLoc != null) {
            selectedLoc.remove();
        }
        selectedLoc = gMap.addMarker(new MarkerOptions()
                .position(latLng)
                .draggable(false)
                .title(getResources().getString(R.string.map_marker_title)));
        selectedLoc.showInfoWindow();
        gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, INITIAL_CAMERA_ZOOM));
    }

    public boolean onMarkerClick(Marker marker) {
        //return false to show this behavior with default behavior (show info window)
        DialogFragment dialog = new MapAlertDialogFragment();
        dialog.show(getFragmentManager(), "confirm add place");

        return false;
    }

    /**************************************************************************************
     *     Dialog
     **************************************************************************************/

    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getFragmentManager(), "errordialog");
    }

    public void onDialogDismissed() {
        resolvingError = false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_RESOLVING_ERROR, resolvingError);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_RESOLVE_ERROR) {
            resolvingError = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!apiClient.isConnecting() &&
                        !apiClient.isConnected()) {
                    apiClient.connect();
                }
            }
        }
    }

    /***************************************************************************************
     *     Classes
     **************************************************************************************/

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() { }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GoogleApiAvailability.getInstance().getErrorDialog(
                    this.getActivity(), errorCode, REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((MapActivity) getActivity()).onDialogDismissed();
        }
    }

    public static class MapAlertDialogFragment extends DialogFragment {

        public static MapAlertDialogFragment newInstance() {
            MapAlertDialogFragment frag = new MapAlertDialogFragment();
            return frag;
        }

        @Override
        public Dialog onCreateDialog(Bundle bundle) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getResources().getString(R.string.geneneral_confirm))
                    .setMessage(R.string.map_dialog_confirm)
                    .setPositiveButton(R.string.general_yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            ((MapActivity)getActivity()).confirmAdd();
                        }
                    })
                    .setNegativeButton(R.string.general_no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //Do nothing
                        }
                    });
            return builder.create();
        }
    }

    // Fetches all places from GooglePlaces AutoComplete Web Service
    private class PlacesTask extends AsyncTask<String, Void, String>{

        @Override
        protected String doInBackground(String... place) {
            // For storing data from web service
            String data = "";

            // Obtain browser key from https://code.google.com/apis/console
            String key = "key=" + BROWSER_API_KEY;

            String input="";

            try {
                input = "input=" + URLEncoder.encode(place[0], "utf-8");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
            }

            // place type to be searched
            String types = "types=geocode";

            // Sensor enabled
            String sensor = "sensor=false";

            // Building the parameters to the web service
            String parameters = input+"&"+types+"&"+sensor+"&"+key;

            // Output format
            String output = "json";

            // Building the url to the web service
            String url = "https://maps.googleapis.com/maps/api/place/autocomplete/"+output+"?"+parameters;

            try{
                // Fetching the data from we service
                data = downloadUrl(url);
            }catch(Exception e){
                Log.d("Background Task",e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            // Creating ParserTask
            ParserTask parserTask = new ParserTask();

            // Starting Parsing the JSON string returned by Web Service
            parserTask.execute(result);
        }
    }

    /** A class to parse the Google Places in JSON format */
    private class ParserTask extends AsyncTask<String, Integer, List<HashMap<String,String>>>{

        JSONObject jObject;

        @Override
        protected List<HashMap<String, String>> doInBackground(String... jsonData) {

            List<HashMap<String, String>> places = null;
            PlaceJSONParser placeJsonParser = new PlaceJSONParser();

            try{
                jObject = new JSONObject(jsonData[0]);

                // Getting the parsed data as a List construct
                places = placeJsonParser.parse(jObject);

            }catch(Exception e){
                Log.d("Exception", e.toString());
            }
            return places;
        }

        @Override
        protected void onPostExecute(List<HashMap<String, String>> result) {

            String[] from = new String[] { "description"};
            int[] to = new int[] { android.R.id.text1 };

            // Creating a SimpleAdapter for the AutoCompleteTextView
            SimpleAdapter adapter = new SimpleAdapter(getBaseContext(), result, android.R.layout.simple_list_item_1, from, to);

            // Setting the adapter
            atvPlaces.setAdapter(adapter);
        }
    }
}
