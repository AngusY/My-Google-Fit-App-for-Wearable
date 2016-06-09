package com.wearable.myfitappforwearable;

import android.content.Intent;
import android.content.IntentSender;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.Button;

import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Subscription;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.result.DataSourcesResult;
import com.google.android.gms.fitness.result.ListSubscriptionsResult;

import java.util.concurrent.TimeUnit;


public class MyFitnessActivity extends AppCompatActivity implements OnDataPointListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {


    private GoogleApiClient mFitClient;
    private boolean isAuthInProgress = false;
    private static final String AUTH_PENDING_STRING = "Auth_Pending";
    private static final int FIT_CLIENT_REQUEST_CODE = 1;

    private Button mCancelButton;
    private Button mShowButton;

    private ResultCallback<Status> mShowButtonResultCallback;
    private ResultCallback<Status> mCancelButtonResultCallback;
    private ResultCallback<ListSubscriptionsResult> mListSubscriptionsResultCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_fitness);

        if (savedInstanceState != null) {
            isAuthInProgress = savedInstanceState.getBoolean(AUTH_PENDING_STRING);
        }

        mShowButton = (Button) findViewById(R.id.show_button);
        mCancelButton = (Button) findViewById(R.id.cancel_button);

        mShowButton.setOnClickListener(this);
        mCancelButton.setOnClickListener((View.OnClickListener) this);

        mShowButtonResultCallback = new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                if (status.isSuccess()) {
                    if (status.getStatusCode() == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                        Log.d("Show Button", "Subscription already existed");
                    } else {
                        Log.d("Show Button", "Just subscribed.");
                    }
                }
            }
        };

        mCancelButtonResultCallback = new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                if (status.isSuccess()) {
                    Log.d("Cancel Button", "Subscription canceled.");
                } else {
                    Log.d("Cancel Button", "Failed to cancel subscription");
                }
            }
        };

        mListSubscriptionsResultCallback = new ResultCallback<ListSubscriptionsResult>() {
            @Override
            public void onResult(@NonNull ListSubscriptionsResult listSubscriptionsResult) {
                for (Subscription subscription : listSubscriptionsResult.getSubscriptions()) {
                    DataType dataType = subscription.getDataType();
                    Log.d("List Subscription", dataType.getName());
                    for (Field field : dataType.getFields()) {
                        Log.d("List Subscription", field.toString());
                    }
                }
            }
        };

        mFitClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.SENSORS_API)
                .addApi(Fitness.RECORDING_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }


        @Override
    protected void onStart() {
        super.onStart();
        mFitClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();

        Fitness.SensorsApi.remove( mFitClient, this )
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            mFitClient.disconnect();
                        }
                    }
                });
    }

    private void registerFitnessDataHelper(DataSource dataSource, DataType dataType) {

        SensorRequest request = new SensorRequest.Builder()
                .setDataSource( dataSource )
                .setDataType( dataType )
                .setSamplingRate( 3, TimeUnit.SECONDS )
                .build();

        Fitness.SensorsApi.add(mFitClient, request, this)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.d("GoogleFit", "Sensors API successfully registered");
                        } else {
                            Log.d("GoogleFit", "Status from Sensors API registration: "
                                    + status.getStatusMessage());
                        }
                    }
                });
    }

    @Override
    public void onConnected(Bundle bundle) {

        DataSourcesRequest dataSourceRequest = new DataSourcesRequest.Builder()
                .setDataTypes( DataType.TYPE_STEP_COUNT_CUMULATIVE )
                .setDataSourceTypes( DataSource.TYPE_RAW )
                .build();

        ResultCallback<DataSourcesResult> dataSourcesResultCallback = new ResultCallback<DataSourcesResult>() {
            @Override
            public void onResult(DataSourcesResult dataSourcesResult) {
                for( DataSource dataSource : dataSourcesResult.getDataSources() ) {
                    if( DataType.TYPE_STEP_COUNT_CUMULATIVE.equals( dataSource.getDataType() ) ) {
                        registerFitnessDataHelper(dataSource, DataType.TYPE_STEP_COUNT_CUMULATIVE);
                    }
                }
            }
        };

        Fitness.SensorsApi.findDataSources(mFitClient, dataSourceRequest)
                .setResultCallback(dataSourcesResultCallback);

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if( requestCode == FIT_CLIENT_REQUEST_CODE ) {
            isAuthInProgress = false;
            if( resultCode == RESULT_OK ) {
                Log.d( "GoogleFit", "User clicked on the Allow button." );
                if( !mFitClient.isConnecting() && !mFitClient.isConnected() ) {
                    mFitClient.connect();
                }
            } else if( resultCode == RESULT_CANCELED ) {
                Log.d( "GoogleFit", "User clicked on the deny button." );
            }
        } else {
            Log.d("GoogleFit", "Not our request code.");
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if( !isAuthInProgress ) {
            try {
                isAuthInProgress = true;
                connectionResult.startResolutionForResult(
                        MyFitnessActivity.this, FIT_CLIENT_REQUEST_CODE );
            } catch(IntentSender.SendIntentException e ) {
                Log.d( "GoogleFit", "Exception: " + e.getMessage() );
            }
        } else {
            Log.d( "GoogleFit", "Authorization is in progress." );
        }
    }

    @Override
    public void onDataPoint(DataPoint dataPoint) {

        for( final Field field : dataPoint.getDataType().getFields() ) {
            final Value value = dataPoint.getValue( field );
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Field: " + field.getName()
                            + " Value: " + value, Toast.LENGTH_SHORT).show();
                }
            });
        }

    }

    @Override
    public void onClick(View v) {

    }
}
