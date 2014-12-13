package me.protopad.arscript.models;

import android.os.ParcelFileDescriptor;

public class XyzIj {
    int xyzCount;
    byte[] buffer;
    Pose poseAtTime;


    public XyzIj(int xyzCount, byte[] buffer, Pose poseAtTime) {
        this.xyzCount = xyzCount;
        this.buffer = buffer;
        this.poseAtTime = poseAtTime;
    }

}
