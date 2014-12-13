package me.protopad.arscript.models;

public class Pose {
    float[] translation;
    float[] rotation;

    public Pose(float[] translation, float[] rotation) {
        this.translation = translation;
        this.rotation = rotation;
    }
}
