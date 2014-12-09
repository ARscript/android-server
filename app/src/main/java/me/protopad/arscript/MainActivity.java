package me.protopad.arscript;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.gson.Gson;

import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.ArrayList;


public class MainActivity extends ActionBarActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String sTranslationFormat = "Translation: %f, %f, %f";
    private static final String sRotationFormat = "Rotation: %f, %f, %f, %f";


    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsTangoServiceConnected;
    private Gson gson;


    DataSocketServer mServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gson = new Gson();
        // Instantiate Tango client
        mTango = new Tango(this);

        // Set up Tango configuration for motion tracking
        // If you want to use other APIs, add more appropriate to the config
        // like: mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true)
        mConfig = new TangoConfig();
        mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);

        Button start = (Button)  findViewById(R.id.start);
        Button end = (Button)  findViewById(R.id.stop);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsTangoServiceConnected) {
                    startActivityForResult(
                        Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_MOTION_TRACKING),
                        Tango.TANGO_INTENT_ACTIVITYCODE);
                }
            }
        });

        end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mTango.disconnect();
                    mIsTangoServiceConnected = false;
                } catch (TangoErrorException e) {
                    Toast.makeText(getApplicationContext(), "Tango Error!",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == Tango.TANGO_INTENT_ACTIVITYCODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this,
                        "This app requires Motion Tracking permission!",
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            try {
                setTangoListeners();
            } catch (TangoErrorException e) {
                Toast.makeText(this, "Tango Error! Restart the app!",
                        Toast.LENGTH_SHORT).show();
            }
            try {
                mTango.connect(mConfig);
                mIsTangoServiceConnected = true;
            } catch (TangoOutOfDateException e) {
                Toast.makeText(getApplicationContext(),
                        "Tango Service out of date!", Toast.LENGTH_SHORT)
                        .show();
            } catch (TangoErrorException e) {
                Toast.makeText(getApplicationContext(),
                        "Tango Error! Restart the app!", Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void setTangoListeners() {
        // Select coordinate frame pairs
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));


        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                String host = "localhost";
                int port = 8887;
                mServer = new DataSocketServer(new InetSocketAddress(host, port));
                mServer.run();
            }
        };
        new Thread(runnable).start();

        // Add a listener for Tango pose data
        mTango.connectListener(framePairs, new Tango.OnTangoUpdateListener() {

            @SuppressLint("DefaultLocale")
            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                // Format Translation and Rotation data
                final String translationMsg = String.format(sTranslationFormat,
                        pose.translation[0], pose.translation[1],
                        pose.translation[2]);
                final String rotationMsg = String.format(sRotationFormat,
                        pose.rotation[0], pose.rotation[1], pose.rotation[2],
                        pose.rotation[3]);

                // Output to LogCat
                String logMsg = translationMsg + " | " + rotationMsg;
                Log.i(TAG, logMsg);
                String jsonPose = gson.toJson(pose);

                // Send data to browser
                if(mServer.currConn != null)
                    mServer.currConn.send(jsonPose);

            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData arg0) {
                Log.i(TAG, "Xyz Available");
                String jsonXYZ = gson.toJson(arg0);
                if(mServer.currConn != null)
                    mServer.currConn.send(jsonXYZ);
            }

            @Override
            public void onTangoEvent(TangoEvent arg0) {
                // Ignoring TangoEvents
            }

        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
}
