package at.alwinschuster.HttpServer;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Base64;
import java.io.ByteArrayInputStream;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import android.os.Build;
import android.util.Log;

public class Server extends NanoHTTPD {
    private static final String TAG = "HttpServer";
    private static final String SERVER_EVENT_ID = "httpServerResponseReceived";

    private ReactContext reactContext;
    private Map<String, Response> responses;

    public Server(ReactContext context, int port) {
        super(port);
        reactContext = context;
        responses = new HashMap<>();

        Log.d(TAG, "Server started");
    }

    @Override
    public Response serve(IHTTPSession session) {
        Log.d(TAG, "Request received!");

        Random rand = new Random();
        String requestId = String.format("%d:%d", System.currentTimeMillis(), rand.nextInt(1000000));

        WritableMap request;
        try {
            request = fillRequestMap(session, requestId);
        } catch (Exception e) {
            return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.getMessage()
            );
        }

        this.sendEvent(reactContext, SERVER_EVENT_ID, request);

        while (responses.get(requestId) == null) {
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                Log.d(TAG, "Exception while waiting: " + e);
            }
        }
        Response response = responses.get(requestId);
        responses.remove(requestId);
        return response;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void respond(String requestId, int code, String type, String body) {
        if(type.equalsIgnoreCase("application/pdf") ||
            type.equalsIgnoreCase("image/jpeg") ||
            type.equalsIgnoreCase("image/png") ||
            type.equalsIgnoreCase("font/ttf") ||
            type.equalsIgnoreCase("audio/mpeg") ||
            type.equalsIgnoreCase("audio/mp4") ||
            type.equalsIgnoreCase("font/woff") ||
            type.equalsIgnoreCase("font/woff2") ||
            type.equalsIgnoreCase("font/eot") ||
            type.equalsIgnoreCase("font/otf") ||
            type.equalsIgnoreCase("audio/wav")
        ){
            byte[] imageBytes = Base64.getDecoder().decode(body);
            responses.put(requestId,
                    newFixedLengthResponse(Status.lookup(code), type, new ByteArrayInputStream(imageBytes), imageBytes.length));
        }else{
        responses.put(requestId, newFixedLengthResponse(Status.lookup(code), type, body));
        }
    }

    private static WritableMap toWritableMap(Map<String, Object> map) {
      WritableMap writableMap = new WritableNativeMap();
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        Object value = entry.getValue();
        if (value instanceof String) {
          writableMap.putString(entry.getKey(), (String) value);
        } else if (value instanceof Integer) {
          writableMap.putInt(entry.getKey(), (Integer) value);
        } else if (value instanceof Double) {
          writableMap.putDouble(entry.getKey(), (Double) value);
        } else if (value instanceof Boolean) {
          writableMap.putBoolean(entry.getKey(), (Boolean) value);
        }
        // Ajouter d'autres types si nécessaire
      }
      return writableMap;
    }

    private WritableMap fillRequestMap(IHTTPSession session, String requestId) throws Exception {
        Method method = session.getMethod();
        WritableMap request = Arguments.createMap();

        // Ajouter l'URL, le type et l'ID de la requête
        request.putString("url", session.getUri());
        request.putString("type", method.name());
        request.putString("requestId", requestId);

        // Ajouter les paramètres GET
        request.putMap("getData", toWritableMap((Map<String, Object>) (Map) session.getParms()));

        // Ajouter les données POST, si disponibles
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        if (files.size() > 0) {
            request.putString("postData", files.get("postData"));
        }

        // Récupération des headers de la requête
        Map<String, String> headers = session.getHeaders();
        Log.d(TAG, headers.toString());
        WritableMap headersMap = Arguments.createMap();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          headersMap.putString(entry.getKey(), entry.getValue());
        }
        request.putMap("headers", headersMap); // Ajout des headers à la requête

        return request;
    }

    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
    }
}
