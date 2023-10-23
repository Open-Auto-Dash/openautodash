package com.openautodash.utilities;

import android.content.Context;
import android.util.Log;
import android.util.Xml;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class ModemInfo {
    private static final String TAG = "ModemInfo";

    private Context context;
    private String response;

    private RequestQueue requestQueue;

    private String ConnectionStatus;
    private String SignalIcon;
    private String CurrentNetworkType;
    private String CurrentServiceDomain;
    private String RoamingStatus;
    private String simlockStatus;
    private String WanIPAddress;
    private String WanIPv6Address;
    private String SecondaryDns;
    private String PrimaryIPv6Dns;
    private String SecondaryIPv6Dns;
    private String CurrentWifiUser;
    private String TotalWifiUser;
    private String ServiceStatus;
    private String SimStatus;
    private String WifiStatus;


    public ModemInfo(Context context) {
        this.context = context;
        requestQueue = Volley.newRequestQueue(context);
    }

    private void setResponse(String response) {
        this.response = response;
    }

    public String getConnectionStatus() {
        return ConnectionStatus;
    }

    public String getSignalIcon() {
        return SignalIcon;
    }

    public String getCurrentNetworkType() {
        return CurrentNetworkType;
    }

    public String getCurrentServiceDomain() {
        return CurrentServiceDomain;
    }

    public String getRoamingStatus() {
        return RoamingStatus;
    }

    public String getSimlockStatus() {
        return simlockStatus;
    }

    public String getWanIPAddress() {
        return WanIPAddress;
    }

    public String getWanIPv6Address() {
        return WanIPv6Address;
    }

    public String getSecondaryDns() {
        return SecondaryDns;
    }

    public String getPrimaryIPv6Dns() {
        return PrimaryIPv6Dns;
    }

    public String getSecondaryIPv6Dns() {
        return SecondaryIPv6Dns;
    }

    public String getCurrentWifiUser() {
        return CurrentWifiUser;
    }

    public String getTotalWifiUser() {
        return TotalWifiUser;
    }

    public String getServiceStatus() {
        return ServiceStatus;
    }

    public String getSimStatus() {
        return SimStatus;
    }

    public String getWifiStatus() {
        return WifiStatus;
    }

    public void updateInfo(){
        // Make a request to the SesTokInfo endpoint to obtain the session ID and token
        String sesTokInfoUrl = "http://192.168.1.1/api/webserver/SesTokInfo";
        StringRequest sesTokInfoRequest = new StringRequest(Request.Method.GET, sesTokInfoUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            // Parse the response to obtain the session ID and token
                            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                            DocumentBuilder builder = factory.newDocumentBuilder();
                            Document doc = builder.parse(new InputSource(new StringReader(response)));
                            String sessionId = doc.getElementsByTagName("SesInfo").item(0).getTextContent();
                            String token = doc.getElementsByTagName("TokInfo").item(0).getTextContent();

                            // Use the session ID and token to make the actual API call
                            String url = "http://192.168.1.1/api/monitoring/status";
                            String url1 = "http://192.168.1.1/api/device/signal";
                            String url2 = "http://192.168.1.1/api/device/signal";
                            StringRequest signalStrengthRequest = new StringRequest(Request.Method.GET, url,
                                    new Response.Listener<String>() {
                                        @Override
                                        public void onResponse(String response) {
                                            try {
                                                setResponse(response);
                                                // Parse the XML response to obtain the signal strength
                                                XmlPullParser parser = Xml.newPullParser();
                                                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                                                parser.setInput(new StringReader(response));
                                                int eventType = parser.getEventType();
                                                while (eventType != XmlPullParser.END_DOCUMENT) {
                                                    if (eventType == XmlPullParser.START_TAG && parser.getName().equals("SignalIcon")) {
                                                        SignalIcon = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("ConnectionStatus")) {
                                                        ConnectionStatus = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("CurrentNetworkType")) {
                                                        CurrentNetworkType = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("CurrentServiceDomain")) {
                                                        CurrentServiceDomain = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("RoamingStatus")) {
                                                        RoamingStatus = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("simlockStatus")) {
                                                        simlockStatus = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("WanIPAddress")) {
                                                        WanIPAddress = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("WanIPv6Address")) {
                                                        WanIPv6Address = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("SecondaryDns")) {
                                                        SecondaryDns = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("PrimaryIPv6Dns")) {
                                                        PrimaryIPv6Dns = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("SecondaryIPv6Dns")) {
                                                        SecondaryIPv6Dns = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("CurrentWifiUser")) {
                                                        CurrentWifiUser = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("TotalWifiUser")) {
                                                        TotalWifiUser = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("ServiceStatus")) {
                                                        ServiceStatus = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("SimStatus")) {
                                                        SimStatus = parser.nextText();
                                                    }
                                                    else if (eventType == XmlPullParser.START_TAG && parser.getName().equals("WifiStatus")) {
                                                        WifiStatus = parser.nextText();
                                                    }
                                                    eventType = parser.next();
                                                }
                                                Log.d(TAG, "onResponse: " + response);
                                            } catch (XmlPullParserException | IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    },
                                    new Response.ErrorListener() {
                                        @Override
                                        public void onErrorResponse(VolleyError error) {
                                            Log.e("Error", "Error getting signal strength", error);
                                            XmlPullParser parser = Xml.newPullParser();
                                            try {
                                                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                                                parser.setInput(new StringReader(response));
                                                int eventType = parser.getEventType();
                                            } catch (XmlPullParserException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    }) {
                                @Override
                                public Map<String, String> getHeaders() throws AuthFailureError {
                                    Map<String, String> headers = new HashMap<>();
                                    headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3");
                                    headers.put("__RequestVerificationToken", token);
                                    headers.put("Cookie", sessionId);
                                    return headers;
                                }
                            };

                            // Add the signal strength request to the Volley request queue
                            requestQueue.add(signalStrengthRequest);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("Error", "Error obtaining session ID and token", error);
                    }
                });

// Add the SesTokInfo request to the Volley request queue
        requestQueue.add(sesTokInfoRequest);
    }

    public void updateInfoDialUp(){
        // Make a request to the SesTokInfo endpoint to obtain the session ID and token
        String sesTokInfoUrl = "http://192.168.1.1/api/webserver/SesTokInfo";
        StringRequest sesTokInfoRequest = new StringRequest(Request.Method.GET, sesTokInfoUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            // Parse the response to obtain the session ID and token
                            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                            DocumentBuilder builder = factory.newDocumentBuilder();
                            Document doc = builder.parse(new InputSource(new StringReader(response)));
                            String sessionId = doc.getElementsByTagName("SesInfo").item(0).getTextContent();
                            String token = doc.getElementsByTagName("TokInfo").item(0).getTextContent();

                            // Use the session ID and token to make the actual API call
                            String url = "http://192.168.1.1/api/monitoring/status";
                            String url1 = "http://192.168.1.1/api/device/signal";
                            String url2 = "http://192.168.1.1/api/device/signal";
                            String url3 = "http://192.168.1.1/api/dialup/connection";
                            StringRequest signalStrengthRequest = new StringRequest(Request.Method.GET, url3,
                                    new Response.Listener<String>() {
                                        @Override
                                        public void onResponse(String response) {
                                            Log.d(TAG, "onResponse: " + response);
                                        }
                                    },
                                    new Response.ErrorListener() {
                                        @Override
                                        public void onErrorResponse(VolleyError error) {
                                            Log.e("Error", "Error getting signal strength", error);
                                        }
                                    }) {
                                @Override
                                public Map<String, String> getHeaders() throws AuthFailureError {
                                    Map<String, String> headers = new HashMap<>();
                                    headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3");
                                    headers.put("__RequestVerificationToken", token);
                                    headers.put("Cookie", sessionId);
                                    return headers;
                                }
                            };

                            // Add the signal strength request to the Volley request queue
                            RequestQueue requestQueue = Volley.newRequestQueue(context);
                            requestQueue.add(signalStrengthRequest);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("Error", "Error obtaining session ID and token", error);
                    }
                });

// Add the SesTokInfo request to the Volley request queue
        RequestQueue requestQueue = Volley.newRequestQueue(context);
        requestQueue.add(sesTokInfoRequest);
    }
}
