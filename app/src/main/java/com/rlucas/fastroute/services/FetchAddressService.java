package com.rlucas.fastroute.services;

import android.app.IntentService;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.rlucas.fastroute.Constants;
import com.rlucas.fastroute.R;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class FetchAddressService extends IntentService {

    private ResultReceiver receiver;
    private Address address;

    public FetchAddressService() {
        super("");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i("FetchAddressService", "Starting service");
        Geocoder geocoder = new Geocoder(getBaseContext(), Locale.getDefault());
        LatLng location = intent.getParcelableExtra(Constants.EXTRA_MAP_LATLNG);
        receiver = intent.getParcelableExtra(Constants.EXTRA_MAP_RESULTRECEIVER);
        List<Address> addresses = null;
        String errorMessage = "";

        try {
            addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1);
        } catch (IOException ioException) {
            errorMessage = getString(R.string.error_service_unavailable);
        } catch (IllegalArgumentException illegalArgumentException) {
            errorMessage = getString(R.string.error_invalid_lat_long);
        }

        if(addresses == null) {
            returnResult(Constants.RESULTCODE_FAILURE, errorMessage);
        } else if (addresses.size() == 0) {
            returnResult(Constants.RESULTCODE_FAILURE, "No addresses found");
        }
        else {
            this.address = addresses.get(0);
            returnResult(Constants.RESULTCODE_SUCCESS, "Success");
        }
    }

    public void returnResult(int resultCode, String message){
        Log.i("FetchAddressService", "Returning result");
        Bundle bundle = new Bundle();
        bundle.putString(Constants.BUNDLE_RESULTMESSAGE, message);
        if(this.address != null) {
            bundle.putParcelable(Constants.BUNDLE_MAP_ADDRESS, this.address);
        }
        receiver.send(resultCode, bundle);
    }
}