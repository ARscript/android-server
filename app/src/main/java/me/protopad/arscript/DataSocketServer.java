package me.protopad.arscript;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class DataSocketServer extends WebSocketServer {
    public static final String TAG = DataSocketServer.class.getSimpleName();
    public WebSocket currConn;

    public DataSocketServer(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Log.i(TAG, "new connection to " + conn.getRemoteSocketAddress());
        currConn = conn;
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.i(TAG, "closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.i(TAG, "received message from " + conn.getRemoteSocketAddress() + ": " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e(TAG, "an error occured on connection " + conn.getRemoteSocketAddress() + ":" + ex);
    }

}
