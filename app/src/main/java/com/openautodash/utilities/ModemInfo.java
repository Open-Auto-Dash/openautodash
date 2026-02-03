package com.openautodash.utilities;

import android.content.Context;
import android.util.Log;
import android.util.Xml;
import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.openautodash.repositorys.VehicleRepository;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xmlpull.v1.XmlPullParser;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class ModemInfo {
    private static final String TAG = "ModemInfo";
    private final Context context;
    private final RequestQueue requestQueue;

    public ModemInfo(Context context) {
        this.context = context;
        this.requestQueue = Volley.newRequestQueue(context);
    }

    public void updateInfo() {
        String sesTokInfoUrl = "http://192.168.1.1/api/webserver/SesTokInfo";
        StringRequest sesTokInfoRequest = new StringRequest(Request.Method.GET, sesTokInfoUrl,
                response -> {
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document doc = builder.parse(new InputSource(new StringReader(response)));
                        String sessionId = doc.getElementsByTagName("SesInfo").item(0).getTextContent();
                        String token = doc.getElementsByTagName("TokInfo").item(0).getTextContent();

                        fetchSignalStatus(sessionId, token);
                    } catch (Exception e) {
                        Log.e(TAG, "Error obtaining session ID", e);
                    }
                },
                error -> Log.e(TAG, "SesTokInfo Request Failed", error));

        requestQueue.add(sesTokInfoRequest);
    }

    private void fetchSignalStatus(String sessionId, String token) {
        String url = "http://192.168.1.1/api/monitoring/status";
        StringRequest signalRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        XmlPullParser parser = Xml.newPullParser();
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                        parser.setInput(new StringReader(response));

                        String signalIcon = "0";
                        String networkType = "0";

                        int eventType = parser.getEventType();
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) {
                                String name = parser.getName();
                                if (name.equals("SignalIcon")) {
                                    signalIcon = parser.nextText();
                                } else if (name.equals("CurrentNetworkType")) {
                                    networkType = parser.nextText();
                                }
                            }
                            eventType = parser.next();
                        }

                        // Map and Push to Repository
                        int strength = Integer.parseInt(signalIcon);
                        String typeLabel = mapNetworkType(Integer.parseInt(networkType));
                        VehicleRepository.getInstance(context).updateNetworkStatus(strength, typeLabel, false);

                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing signal status", e);
                    }
                },
                error -> Log.e(TAG, "Status API Request Failed", error)) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "Mozilla/5.0");
                headers.put("__RequestVerificationToken", token);
                headers.put("Cookie", sessionId);
                return headers;
            }
        };
        requestQueue.add(signalRequest);
    }

    private String mapNetworkType(int type) {
        switch (type) {
            case 0: return "";
            case 1: case 2: return "2G";
            case 3: case 4: case 5: case 6: return "3G";
            case 7: return "3G+";
            case 8: case 9: return "4G";
            case 19: return "LTE";
            default: return "D" + type;
        }
    }
}