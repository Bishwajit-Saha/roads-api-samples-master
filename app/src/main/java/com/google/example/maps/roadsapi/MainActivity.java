/*
 * Copyright 2015 Google Inc. All rights reserved.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.google.example.maps.roadsapi;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.RoadsApi;
import com.google.maps.android.ui.IconGenerator;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import com.google.maps.model.SnappedPoint;
import com.google.maps.model.SpeedLimit;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Xml;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Roads API Demo App.
 *
 * Before you can start, you will need to obtain the relevant keys and add them to the api_keys.xml
 * file. The steps are detailed in the README file in the top level of this package.
 *
 * This app will load a map with 3 buttons. Press each of the buttons in sequence to demonstrate
 * various features of the Roads API and the supporting demo snippets.
 *
 * Find out more about the Roads API here: https://developers.google.com/maps/documentation/roads
 */
public class MainActivity extends ActionBarActivity implements OnMapReadyCallback {

    private Uri uri = null;
    InputStream inputStream;
    BufferedReader bufferedReader;
    LatLng temp;
    int flag = 0;
    /**
     * The map. It is initialized when the map has been fully loaded and is ready to be used.
     * @see #onMapReady(com.google.android.gms.maps.GoogleMap)
     */
    private GoogleMap mMap;

    /**
     * The API context used for the Roads and Geocoding web service APIs.
     */
    private GeoApiContext mContext;

    /**
     * The number of points allowed per API request. This is a fixed value.
     */
    private static final int PAGE_SIZE_LIMIT = 100;

    /**
     * Define the number of data points to re-send at the start of subsequent requests. This helps
     * to influence the API with prior data, so that paths can be inferred across multiple requests.
     * You should experiment with this value for your use-case.
     */
    private static final int PAGINATION_OVERLAP = 5;

    /**
     * Icon cache for {@link #generateSpeedLimitMarker}.
     */
    private LongSparseArray<BitmapDescriptor> mSpeedIcons = new LongSparseArray<>();
    private IconGenerator mIconGenerator;

    private ProgressBar mProgressBar;

    List<LatLng> mCapturedLocations;
    List<SnappedPoint> mSnappedPoints;
    Map<String, SpeedLimit> mPlaceSpeeds;
    List<List<LatLng>> snapperLocations;


    /**
     * Snaps the points to their most likely position on roads using the Roads API.
     */
    //done
    private List<List<SnappedPoint>> snapToRoads(GeoApiContext context) throws Exception {
        List<List<SnappedPoint>> listOfSnappedPoints = new ArrayList<List<SnappedPoint>>();
        int i = 0;
        for(;i<snapperLocations.size(); i++)
        {
            List<SnappedPoint> snappedPoints = new ArrayList<>();
            mCapturedLocations = snapperLocations.get(i);
            int offset = 0;
            while (offset < mCapturedLocations.size())
            {
                // Calculate which points to include in this request. We can't exceed the APIs
                // maximum and we want to ensure some overlap so the API can infer a good location for
                // the first few points in each request.
                if (offset > 0) {
                    offset -= PAGINATION_OVERLAP;   // Rewind to include some previous points
                }
                int lowerBound = offset;
                int upperBound = Math.min(offset + PAGE_SIZE_LIMIT, mCapturedLocations.size());

                // Grab the data we need for this page.
                LatLng[] page = mCapturedLocations
                        .subList(lowerBound, upperBound)
                        .toArray(new LatLng[upperBound - lowerBound]);

                // Perform the request. Because we have interpolate=true, we will get extra data points
                // between our originally requested path. To ensure we can concatenate these points, we
                // only start adding once we've hit the first new point (i.e. skip the overlap).
                SnappedPoint[] points = RoadsApi.snapToRoads(context, true, page).await();
                boolean passedOverlap = false;
                for (SnappedPoint point : points) {
                    if (offset == 0 || point.originalIndex >= PAGINATION_OVERLAP) {
                        passedOverlap = true;
                    }
                    if (passedOverlap) {
                        snappedPoints.add(point);
                    }
                }

                offset = upperBound;
            }
            listOfSnappedPoints.add(snappedPoints);
        }

        return listOfSnappedPoints;
    }

    //done
    AsyncTask<Void, Void, List<List<SnappedPoint>>> mTaskSnapToRoads =
            new AsyncTask<Void, Void, List<List<SnappedPoint>> >() {
        @Override
        protected void onPreExecute() {
            mProgressBar.setVisibility(View.VISIBLE);
            mProgressBar.setIndeterminate(true);
        }

        @Override
        protected List<List<SnappedPoint>> doInBackground(Void... params) {
            try {
                return snapToRoads(mContext);
            } catch (final Exception ex) {
                toastException(ex);
                ex.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<List<SnappedPoint>> snappedPointsList) {

            for(int j = 0; j < snappedPointsList.size(); j++ )
            {

                mSnappedPoints = snappedPointsList.get(j);
                mProgressBar.setVisibility(View.INVISIBLE);

                // findViewById(R.id.speed_limits).setEnabled(true);

                com.google.android.gms.maps.model.LatLng[] mapPoints =
                        new com.google.android.gms.maps.model.LatLng[mSnappedPoints.size()];
                int i = 0;
                LatLngBounds.Builder bounds = new LatLngBounds.Builder();
                for (SnappedPoint point : mSnappedPoints) {
                    mapPoints[i] = new com.google.android.gms.maps.model.LatLng(point.location.lat,
                            point.location.lng);
                    bounds.include(mapPoints[i]);
                    i += 1;
                }

                mMap.addPolyline(new PolylineOptions().add(mapPoints).color(Color.RED));
                //mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 0));
            }

           /* double distance = getDistance(snappedPointsList);
            String s = String.valueOf(distance);
            Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();*/

            snappedPointsToLogFile(snappedPointsList);

        }
    };

    private void snappedPointsToLogFile(List<List<SnappedPoint>> snappedPointsList) {

        String name = uri.toString();
        int z = name.length();
        name = name.substring(z-12);
        name = "snapped_" + name;
        File root = Environment.getExternalStorageDirectory();
        File dir = new File(root.getAbsolutePath() + "/SnappedLogFiles");
        if(!dir.exists())
            dir.mkdir();
        File file = new File(dir,name);
        FileOutputStream fos = null;
        try
        {
            fos = new FileOutputStream(file);
            List<SnappedPoint> list;
            int size = snappedPointsList.size();
            int count = 1;
            double lat, lng;
            SnappedPoint p;
            String data = "";
            String prev = "";
            String curr = "";
            for (int i = 0; i < size; i++)
            {
                list= snappedPointsList.get(i);
                int length = list.size();
                for(int j = 0; j < length; j++)
                {
                    p = list.get(j);
                    lat = p.location.lat;
                    lng = p.location.lng;
                    curr =  String.valueOf(lat) + "," + String.valueOf(lng);
                    if( !curr.equalsIgnoreCase(prev) ) {
                        data = String.valueOf(count) + "," + curr + "\n";
                        prev = curr;
                        fos.write(data.getBytes());
                        count++;
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            try {
                fos.close();
                Toast.makeText(this, "done", Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }


    public double getDistance(List<List<SnappedPoint>> snappedPointsList)
    {

        int lenght = snappedPointsList.size();
        double total_distance = 0;
        for(int i = 0 ; i < lenght; i++)
        {
            List<SnappedPoint> list = snappedPointsList.get(i);
            int size = list.size();
            double dist = distance(list.get(0), list.get(1));
            for(int j = 2; j < size; j++)
                dist += distance(list.get(j-1), list.get(j));
            total_distance += dist;
        }
        return total_distance;
    }

    private double distance(SnappedPoint sp1, SnappedPoint sp2)
    {

       final int R = 6371;
        double lat1, lat2, lon1, lon2;
        LatLng p1 = sp1.location;
        LatLng p2 = sp2.location;

        lat1 = p1.lat;
        lat2 = p2.lng;
        lon1 = p1.lat;
        lon2 = p2.lng;

        Double latDistance = Math.toRadians(lat2 - lat1);
        Double lonDistance = Math.toRadians(lon2 - lon1);
        Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        return distance;
    }


    private void snapAllpointstoRoad() {
        mTaskSnapToRoads.execute();
    }
    //done
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mContext = new GeoApiContext().setApiKey(getString(R.string.google_maps_web_services_key));
    }

    //done
    /**
     * Parses the waypoint (wpt tags) data into native objects from a GPX stream.
     */
    private List<LatLng> loadGpxData() throws IOException {
        List<LatLng> latLngs = new ArrayList<>();
        String line;
        int count = 0;

//        while(  (line = bufferedReader.readLine()) != null )
//        {
          /*  if(count == 0 && flag != 0)
            {
                latLngs.add(temp);
                count++;
                flag = 1;
                //temp = null;
            }*/
            /*int comma = line.indexOf(',');
            Double lat = Double.parseDouble(line.substring(0, comma));
            Double lng = Double.parseDouble(line.substring(comma + 1));
            latLngs.add(new LatLng(lat,lng));
            count++;*/

            int comma1, comma2;
            line = bufferedReader.readLine();
            while ( (line = bufferedReader.readLine()) != null) {
                if (line.length() == 0)
                    break;
                comma1 = line.indexOf(',');
                comma2 = line.indexOf(',', comma1 + 1);
                double lat = Double.parseDouble(line.substring(comma1 + 1, comma2));
                comma1 = comma2;
                comma2 = line.indexOf(',', comma2 + 1);
                double lng = Double.parseDouble(line.substring(comma1 + 1, comma2));
                if (lat != 0 && lng != 0)
                    latLngs.add(new LatLng(lat, lng));
            }

            /*if(count == 100)
                temp = latLngs.get(count - 1);
                break;*/

        return latLngs;
    }



   @Override
   public void onActivityResult(int requestCode, int resultCode, Intent resultData) {

        if (requestCode == 42 && resultCode == Activity.RESULT_OK) {
            if( resultData != null)
            {
                int count = 0;
                snapperLocations = new ArrayList<List<LatLng>>();
                uri = resultData.getData();
                try {
                    inputStream = getContentResolver().openInputStream(uri);
                    bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    //String s = bufferedReader.readLine();
                    //Toast.makeText(this, s, Toast.LENGTH_SHORT).show();

                    mCapturedLocations = loadGpxData();
                    while(mCapturedLocations.size() != 0)
                    {
                        snapperLocations.add(mCapturedLocations);
                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        PolylineOptions polyline = new PolylineOptions();
                        mCapturedLocations = snapperLocations.get(count);
                        for (LatLng ll : mCapturedLocations)
                        {
                            com.google.android.gms.maps.model.LatLng mapPoint =
                                    new com.google.android.gms.maps.model.LatLng(ll.lat, ll.lng);
                            builder.include(mapPoint);
                            polyline.add(mapPoint);
                            mMap.addPolyline(polyline.color(Color.BLUE));
                            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 0));
                      //      mTaskSnapToRoads.execute();

                        }
                        mCapturedLocations = loadGpxData();
                        count++;
                    }
                    findViewById(R.id.snap_to_roads).setEnabled(true);
//                    Toast.makeText(this, count, Toast.LENGTH_LONG).show();
                    //Toast.makeText(this, name , Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
             }
        }
    }
    /**
     * Handles the GPX button-click event, running the demo snippet {@link #loadGpxData}.
     */
    //done
    public void onGpxButtonClick(View view) {
       try {

           Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
           intent.setType("*/*");
           startActivityForResult(intent, 42);


        } catch ( Exception e) {
            e.printStackTrace();
            toastException(e);
        }
    }


    /**
     * Handles the Snap button-click event, running the demo snippet {@link #snapToRoads}.
     */
    //done
    public void onSnapToRoadsButtonClick(View view) {
        //mTaskSnapToRoads.execute();
        snapAllpointstoRoad();
    }



    /**
     * Retrieves speed limits for the previously-snapped points. This method is efficient in terms
     * of quota usage as it will only query for unique places.
     *
     * Note: Speed Limit data is only available with an enabled Maps for Work API key.
     */
    private Map<String, SpeedLimit> getSpeedLimits(GeoApiContext context, List<SnappedPoint> points)
            throws Exception {
        Map<String, SpeedLimit> placeSpeeds = new HashMap<>();

        // Pro tip: save on quota by filtering to unique place IDs
        for (SnappedPoint point : points) {
            placeSpeeds.put(point.placeId, null);
        }

        String[] uniquePlaceIds =
                placeSpeeds.keySet().toArray(new String[placeSpeeds.keySet().size()]);

        // Loop through the places, one page (API request) at a time.
        for (int i = 0; i < uniquePlaceIds.length; i += PAGE_SIZE_LIMIT) {
            String[] page = Arrays.copyOfRange(uniquePlaceIds, i,
                    Math.min(i + PAGE_SIZE_LIMIT, uniquePlaceIds.length));

            // Execute!
            SpeedLimit[] placeLimits = RoadsApi.speedLimits(context, page).await();
            for (SpeedLimit sl : placeLimits) {
                placeSpeeds.put(sl.placeId, sl);
            }
        }

        return placeSpeeds;
    }

    /**
     * Geocodes a Snapped Point using the Place ID.
     */
    private GeocodingResult geocodeSnappedPoint(GeoApiContext context, SnappedPoint point) throws Exception {
        GeocodingResult[] results = GeocodingApi.newRequest(context)
                .place(point.placeId)
                .await();

        if (results.length > 0) {
            return results[0];
        }
        return null;
    }

    /**
     * Handles the Speed Limit button-click event, running the demo snippets {@link #getSpeedLimits}
     * and {@link #geocodeSnappedPoint} behind a progress dialog.
     */
    //done
    public void onSpeedLimitButtonClick(View view) {
//        mTaskSpeedLimits.execute();
    }

    /**
     * Generates a marker that looks like a speed limit sign.
     */
    private MarkerOptions generateSpeedLimitMarker(double speed, SnappedPoint point,
            GeocodingResult geocode) {
        if (mIconGenerator == null) {
            mIconGenerator = new IconGenerator(getApplicationContext());
            mIconGenerator
                    .setContentView(getLayoutInflater().inflate(R.layout.speed_limit_view, null));
            mIconGenerator.setBackground(null);
        }

        // Cache icons.
        long speedLabel = Math.round(speed);
        BitmapDescriptor icon = mSpeedIcons.get(speedLabel);
        if (icon == null) {
            icon = BitmapDescriptorFactory
                    .fromBitmap(mIconGenerator.makeIcon(String.valueOf(speedLabel)));
            mSpeedIcons.put(speedLabel, icon);
        }

        return new MarkerOptions()
                .icon(icon)
                .position(new com.google.android.gms.maps.model.LatLng(
                        point.location.lat, point.location.lng))
                .flat(true)
                .title(geocode != null
                        ? geocode.formattedAddress
                        : point.placeId);
    }

    /** Helper for toasting exception messages on the UI thread. */
    private void toastException(final Exception ex) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }

    AsyncTask<Void, Integer, Map<String, SpeedLimit>> mTaskSpeedLimits =
            new AsyncTask<Void, Integer, Map<String, SpeedLimit>>() {
                private List<MarkerOptions> markers;

                @Override
                protected void onPreExecute() {
                    markers = new ArrayList<>();
                    mProgressBar.setIndeterminate(true);    // Just until we know how much to Geocode
                    mProgressBar.setProgress(0);
                    mProgressBar.setVisibility(View.VISIBLE);
                }

                @Override
                protected Map<String, SpeedLimit> doInBackground(Void... params) {
                    Map<String, SpeedLimit> placeSpeeds = null;
                    try {
                        placeSpeeds = getSpeedLimits(mContext, mSnappedPoints);
                        publishProgress(0, placeSpeeds.size());

                        // Generate speed limit icons, with geocoded labels.
                        Set<String> visitedPlaceIds = new HashSet<>();
                        for (SnappedPoint point : mSnappedPoints) {
                            if (!visitedPlaceIds.contains(point.placeId)) {
                                visitedPlaceIds.add(point.placeId);

                                GeocodingResult geocode = geocodeSnappedPoint(mContext, point);
                                publishProgress(visitedPlaceIds.size());

                                // As each place has been geocoded, we'll use the name of the place
                                // as the marker title, so tapping the marker will display the address.
                                markers.add(generateSpeedLimitMarker(
                                        placeSpeeds.get(point.placeId).speedLimit, point, geocode));
                            }
                        }
                    } catch (Exception ex) {
                        toastException(ex);
                        ex.printStackTrace();
                    }

                    return placeSpeeds;
                }

                @Override
                protected void onProgressUpdate(Integer... values) {
                    mProgressBar.setProgress(values[0]);
                    if (values.length > 1) {
                        mProgressBar.setIndeterminate(false);
                        mProgressBar.setMax(values[1]);
                    }
                }

                @Override
                protected void onPostExecute(Map<String, SpeedLimit> speeds) {
                    for (MarkerOptions marker : markers) {
                        mMap.addMarker(marker);
                    }
                    mProgressBar.setVisibility(View.INVISIBLE);
                    mPlaceSpeeds = speeds;
                }
            };


}
