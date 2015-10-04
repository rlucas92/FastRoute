package com.rlucas.fastroute;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Address;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.google.android.gms.maps.model.LatLng;

public class EditPlaceActivity extends AppCompatActivity {

    private LatLng latLng;
    private Address address;

    @Override
    //Created by MapActivity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_place);

        //Get data from map activity
        Intent intent = getIntent();
        this.latLng = intent.getParcelableExtra(Constants.EXTRA_MAP_LATLNG);
        this.address = intent.getParcelableExtra(Constants.EXTRA_MAP_ADDRESS);

        //Fill in fields
        EditText addressET = (EditText)findViewById(R.id.editText_address);
        addressET.setText(address.getAddressLine(0));
        addressET.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus)
                    onAddressTextSelected();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        this.address = intent.getParcelableExtra(Constants.EXTRA_MAP_ADDRESS);
        EditText addressET = (EditText)findViewById(R.id.editText_address);
        addressET.setText(address.getAddressLine(0));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_edit_place, menu);
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
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onAddressTextSelected() {
        DialogFragment dialog = new EditPlaceAlertDialogFragment();
        dialog.show(getFragmentManager(), "confirmAddressUpdate");
    }

    public void updateAddress() {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra(Constants.EXTRA_MAP_LATLNG, this.latLng);
        startActivity(intent);
    }

    public static class EditPlaceAlertDialogFragment extends DialogFragment {

        public static EditPlaceAlertDialogFragment newInstance() {
            EditPlaceAlertDialogFragment frag = new EditPlaceAlertDialogFragment();
            return frag;
        }

        @Override
        public Dialog onCreateDialog(Bundle bundle) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getResources().getString(R.string.geneneral_confirm))
                    .setMessage(R.string.alertDialog_update_address)
                    .setPositiveButton(R.string.general_yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            ((EditPlaceActivity) getActivity()).updateAddress();
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
}
