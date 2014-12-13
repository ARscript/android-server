package me.protopad.arscript;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
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


import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;

import me.protopad.arscript.models.Pose;
import me.protopad.arscript.models.XyzIj;


public class MainActivity extends ActionBarActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String sTranslationFormat = "Translation: %f, %f, %f";
    private static final String sRotationFormat = "Rotation: %f, %f, %f, %f";

    private static int SECS_TO_MILLI = 1000;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsTangoServiceConnected;
    private Gson gson;

    DataSocketServer mServer;

    private int count;
    private int mPreviousPoseStatus;
    private float mDeltaTime;
    private float mPosePreviousTimeStamp;
    private float mXyIjPreviousTimeStamp;
    private float mCurrentTimeStamp;
    private String mServiceVersion;
    NotificationManager mNotificationManager;

    int mId = 1;

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

       mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

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

                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(getApplicationContext())
                                .setSmallIcon(R.drawable.ic_launcher)
                                .setContentTitle("ARscript")
                                .setContentText("Service Running.")
                                .setOngoing(true);
                mNotificationManager.notify(mId, mBuilder.build());
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
                mNotificationManager.cancel(mId);
                try {
                    mServer.stop();
                } catch (InterruptedException e) {
                    Log.wtf(TAG, "Failed to stop server, Interrupted");
                } catch (IOException e) {
                    Log.wtf(TAG, "Failed to stop server, IO");
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

    private void SetUpExtrinsics() {
        // Set device to imu matrix in Model Matrix Calculator.
        TangoPoseData device2IMUPose = new TangoPoseData();
        TangoCoordinateFramePair framePair = new TangoCoordinateFramePair();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
        try {
            device2IMUPose = mTango.getPoseAtTime(0.0, framePair);
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError,
                    Toast.LENGTH_SHORT).show();
        }


        // TODO: send extrinsics over socket
    }


    // set up intrinsics?


    private void setTangoListeners() {
        // Select coordinate frame pairs
        final ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
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

            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                // pose -> json
                mDeltaTime = (float) (pose.timestamp - mPosePreviousTimeStamp)
                        * SECS_TO_MILLI;
                mPosePreviousTimeStamp = (float) pose.timestamp;
                if(mPreviousPoseStatus != pose.statusCode){
                    count = 0;
                }
                count++;
                mPreviousPoseStatus = pose.statusCode;

                float[] translation = pose.getTranslationAsFloats();
                float[] rotation = pose.getRotationAsFloats();
                Pose p = new Pose(translation, rotation);

                String poseJson = gson.toJson(p);

                Log.i(TAG, poseJson);

                if(mServer.currConn.isOpen())
                    mServer.currConn.send(poseJson);

            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // XYZij -> json
                // TODO: send ij data over socket
                mCurrentTimeStamp = (float) xyzIj.timestamp;
                final float frameDelta = (mCurrentTimeStamp - mXyIjPreviousTimeStamp)
                        * SECS_TO_MILLI;
                mXyIjPreviousTimeStamp = mCurrentTimeStamp;
                byte[] buffer = new byte[xyzIj.xyzCount * 3 * 4];
                FileInputStream fileStream = new FileInputStream(
                        xyzIj.xyzParcelFileDescriptor.getFileDescriptor());
                try {
                    fileStream.read(buffer,
                            xyzIj.xyzParcelFileDescriptorOffset, buffer.length);
                    fileStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // send buffer, count, poseAtTime
                TangoPoseData pointCloudPose = mTango.getPoseAtTime(
                        mCurrentTimeStamp, framePairs.get(0));

                Pose p = new Pose(pointCloudPose.getTranslationAsFloats(), pointCloudPose.getRotationAsFloats());
                XyzIj x = new XyzIj(xyzIj.xyzCount ,buffer, p);


                String xyzJson = gson.toJson(x);

                Log.i(TAG, xyzJson);

                if(mServer.currConn.isOpen())
                    mServer.currConn.send(xyzJson);
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                // send tango event
                String eventJson = gson.toJson(event);

                Log.i(TAG, eventJson);
                // Add this back later
                //if(mServer.currConn.isOpen())
                //    mServer.currConn.send(eventJson);
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
