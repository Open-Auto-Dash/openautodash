package com.openautodash.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.openautodash.LiveDataViewModel;
import com.openautodash.MainActivity;
import com.openautodash.R;
import com.openautodash.interfaces.OverpassAPICallback;
import com.openautodash.utilities.OverpassAPI;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.ErrorCallback;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.Artist;
import com.spotify.protocol.types.Capabilities;
import com.spotify.protocol.types.Image;
import com.spotify.protocol.types.LibraryState;
import com.spotify.protocol.types.PlayerContext;
import com.spotify.protocol.types.PlayerState;
import com.spotify.protocol.types.Repeat;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class TelemetryFragment extends Fragment implements OverpassAPICallback {
    private static final String TAG = "TelemetryFragment";
    private static final String ACCESS_TOKEN = "ACCESS_TOKEN";

    private static final String CLIENT_ID = "13bbd41117ca417980d841742d5b4ad2";
    private static final String REDIRECT_URI = "comopenautodash://callback";

    //ViewModel
    private LiveDataViewModel liveDataViewModel;

    //Views
    private TextView speed;
    private TextView alt;

    private TextView nice;

    private TextView maxSpeedView;
    private TextView speedLimitView;



    //Variables
    boolean metric = true;

    private OverpassAPICallback callback;

    private int locationUpdatesCount;


    // Spotify API
    TextView trackTitle;
    TextView trackArtist;
    Button mConnectButton;
    Button mSubscribeToPlayerContextButton;
    ImageView mCoverArtImageView;
    AppCompatTextView mImageScaleTypeLabel;
    ImageView trackLiked;
    ImageView mToggleShuffleButton;
    ImageView playerBack;
    ImageView mPlayPauseButton;
    ImageView playerForward;
    ImageView mToggleRepeatButton;
    TextView trackTimeLeft;
    TextView trackTimeRight;
    AppCompatSeekBar mSeekBar;

    private static SpotifyAppRemote mSpotifyAppRemote;
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    TrackProgressBar mTrackProgressBar;

    boolean isPodcast;
    boolean isLiked;
    String trackURI;


    Subscription<PlayerState> mPlayerStateSubscription;
    Subscription<PlayerContext> mPlayerContextSubscription;
    Subscription<Capabilities> mCapabilitiesSubscription;

    private final ErrorCallback mErrorCallback = this::logError;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        liveDataViewModel = ((MainActivity) requireActivity()).getViewModel();
        callback = this;


        SpotifyAppRemote.setDebugMode(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mSpotifyAppRemote == null || !mSpotifyAppRemote.isConnected()) {
            connect(false); // Attempt to reconnect without showing auth dialog
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        //Get location updates
        liveDataViewModel.getLocationData().observe(getViewLifecycleOwner(), new Observer<Location>() {
            @Override
            public void onChanged(Location location) {
                locationUpdatesCount++;
                float speedCalcVal = (float) 3.6;
                if(!metric){
                    speedCalcVal = (float) 2.236936;
                }
                speed.setText(String.valueOf((int)(location.getSpeed() * speedCalcVal)));
                alt.setText(String.valueOf((int)location.getAltitude()));

                if(locationUpdatesCount > 5 && location.getSpeed() > 5){
                    OverpassAPI overpassAPI = new OverpassAPI(getContext(), location, callback);
                    overpassAPI.getSpeedLimit();
                    locationUpdatesCount = 0;
                }
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSpotifyAppRemote != null) {
            SpotifyAppRemote.disconnect(mSpotifyAppRemote);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_telemetry, container, false);
        speed = view.findViewById(R.id.tv_m_speed);
        speedLimitView = view.findViewById(R.id.tv_tele_speed_limit);
        maxSpeedView = view.findViewById(R.id.tv_max_speed);
        alt = view.findViewById(R.id.tv_m_gear);
        nice = view.findViewById(R.id.tv_m_nice);

        trackLiked = view.findViewById(R.id.iv_home_like_track);
        trackTitle = view.findViewById(R.id.tv_home_song_title);
        trackArtist = view.findViewById(R.id.tv_home_song_artist);
        mConnectButton = view.findViewById(R.id.b_home_connect_spotify);
        mCoverArtImageView = view.findViewById(R.id.iv_home_album_art);
        trackTimeLeft = view.findViewById(R.id.tv_main_track_time_left);
        trackTimeRight = view.findViewById(R.id.tv_main_track_time_right);
        mSeekBar = view.findViewById(R.id.pb_home_song_progress);
        mSeekBar.getThumb().setTint(getResources().getColor(R.color.cat_medium_green));

        mToggleShuffleButton = view.findViewById(R.id.iv_home_shuffle);
        playerBack = view.findViewById(R.id.iv_home_previous_track);
        mPlayPauseButton = view.findViewById(R.id.iv_home_play);
        playerForward = view.findViewById(R.id.iv_home_next_track);
        mToggleRepeatButton = view.findViewById(R.id.iv_home_repeat);

        mTrackProgressBar = new TrackProgressBar(mSeekBar, trackTimeLeft);

        speed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                metric = !metric;
            }
        });

        mConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connect(true);
            }
        });

        mToggleShuffleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleShuffleButtonClicked(null);
            }
        });

        playerBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isPodcast){
                    onSeekBack(null);
                }
                else{
                    onSkipPreviousButtonClicked(null);
                }
            }
        });

        mPlayPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPlayPauseButtonClicked(null);
            }
        });

        playerForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isPodcast){
                    onSeekForward(null);
                }
                else{
                    onSkipNextButtonClicked(null);
                }
            }
        });

        mToggleRepeatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleRepeatButtonClicked(null);
            }
        });

        trackLiked.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(trackURI != null) {
                    if (isLiked) {
                        mSpotifyAppRemote.getUserApi().removeFromLibrary(trackURI);
                    } else {
                        mSpotifyAppRemote.getUserApi().addToLibrary(trackURI);
                    }
                }
            }
        });


        onDisconnected();
        onConnectAndAuthorizedClicked(null);
        return view;
    }

    @Override
    public void onComplete(String result) {

    }

    @Override
    public void speedLimitUpdated(String speedLimit) {
        speedLimit = speedLimit.replaceAll("[^0-9.]", "");
        speedLimitView.setText(speedLimit);
        // Do something with the max speed data
        int maxSpeedInt = Integer.parseInt(speedLimit);
        if(maxSpeedInt < 60){
            maxSpeedView.setText(String.valueOf(maxSpeedInt + 10));
        }
        else if(maxSpeedInt >  90){
            maxSpeedView.setText(String.valueOf(maxSpeedInt + 25));
        }
        else {
            maxSpeedView.setText(String.valueOf(maxSpeedInt + 20));
        }
    }


    ///////////   Spotify API    //////////////

    private final Subscription.EventCallback<PlayerContext> mPlayerContextEventCallback =
            new Subscription.EventCallback<PlayerContext>() {
                @Override
                public void onEvent(PlayerContext playerContext) {
//                    mPlayerContextButton.setText(
//                            String.format(Locale.US, "%s\n%s", playerContext.title, playerContext.subtitle));
//                    mPlayerContextButton.setTag(playerContext);
                }
            };

    private final Subscription.EventCallback<PlayerState> mPlayerStateEventCallback =
            new Subscription.EventCallback<PlayerState>() {
                @Override
                public void onEvent(PlayerState playerState) {

                    Drawable drawable =
                            ResourcesCompat.getDrawable(
                                    getResources(), R.drawable.mediaservice_shuffle, requireActivity().getTheme());
                    if (!playerState.playbackOptions.isShuffling) {
                        mToggleShuffleButton.setImageDrawable(drawable);
                        DrawableCompat.setTint(mToggleShuffleButton.getDrawable(), getResources().getColor(R.color.colorIconsDefault));
                    } else {
                        mToggleShuffleButton.setImageDrawable(drawable);
                        DrawableCompat.setTint(
                                mToggleShuffleButton.getDrawable(),
                                getResources().getColor(R.color.cat_medium_green));
                    }

                    if (playerState.playbackOptions.repeatMode == Repeat.ALL) {
                        mToggleRepeatButton.setImageResource(R.drawable.mediaservice_repeat_all);
                        DrawableCompat.setTint(
                                mToggleRepeatButton.getDrawable(),
                                getResources().getColor(R.color.cat_medium_green));
                    } else if (playerState.playbackOptions.repeatMode == Repeat.ONE) {
                        mToggleRepeatButton.setImageResource(R.drawable.mediaservice_repeat_one);
                        DrawableCompat.setTint(
                                mToggleRepeatButton.getDrawable(),
                                getResources().getColor(R.color.cat_medium_green));
                    } else {
                        mToggleRepeatButton.setImageResource(R.drawable.mediaservice_repeat_off);
                        DrawableCompat.setTint(mToggleRepeatButton.getDrawable(), getResources().getColor(R.color.colorIconsDefault));
                    }


                    if (playerState.track != null) {
                        trackURI = playerState.track.uri;
                        trackTitle.setText(playerState.track.name);
                        List<Artist> artists = playerState.track.artists;

                        StringBuilder sb = new StringBuilder();
                        for (Artist artist : artists) {
                            sb.append(artist.name);
                            sb.append(", ");
                        }
                        // Remove the trailing comma and space
                        if (artists.size() > 0) {
                            sb.setLength(sb.length() - 2);
                        }
                        trackArtist.setText(sb.toString());
                        if(playerState.track.isPodcast){
                            isPodcast = true;
                        }
                        else{
                            isPodcast = false;
                        }

                        // Check if the current track is in the user's library
                        mSpotifyAppRemote.getUserApi().getLibraryState(playerState.track.uri).setResultCallback(new CallResult.ResultCallback<LibraryState>() {
                            @Override
                            public void onResult(LibraryState data) {
                                Log.d(TAG, "onResult: Spotify " + data.isAdded);
                                if(data.isAdded) {
                                    isLiked = true;
                                    DrawableCompat.setTint(trackLiked.getDrawable(), getResources().getColor(R.color.cat_medium_green));
                                }
                                else{
                                    isLiked = false;
                                    DrawableCompat.setTint(trackLiked.getDrawable(), getResources().getColor(R.color.colorIconsDefault));

                                }
                            }
                        });

//                        mPlayerStateButton.setText(
//                                String.format(
//                                        Locale.US, "%s\n%s", playerState.track.name, playerState.track.artist.name));
//                        mPlayerStateButton.setTag(playerState);
                    }
                    // Update progressbar
                    if (playerState.playbackSpeed > 0) {
                        mTrackProgressBar.unpause();
                    } else {
                        mTrackProgressBar.pause();
                    }

                    // Invalidate play / pause
                    if (playerState.isPaused) {
                        mPlayPauseButton.setImageResource(R.drawable.ic_baseline_play);
                    } else {
                        mPlayPauseButton.setImageResource(R.drawable.ic_baseline_pause);
                    }
                    DrawableCompat.setTint(mPlayPauseButton.getDrawable(), getResources().getColor(R.color.colorIconsDefault));



                    if (playerState.track != null) {
                        // Get image from track
                        mSpotifyAppRemote
                                .getImagesApi()
                                .getImage(playerState.track.imageUri, Image.Dimension.LARGE)
                                .setResultCallback(
                                        bitmap -> {
                                            mCoverArtImageView.setImageBitmap(bitmap);

                                        });
                        // Invalidate seekbar length and position
                        mSeekBar.setMax((int) playerState.track.duration);
                        mTrackProgressBar.setDuration(playerState.track.duration);
                        mTrackProgressBar.update(playerState.playbackPosition);
                        // Timestamps
                        trackTimeLeft.setText(millisToStringStamp(playerState.playbackPosition));
                        trackTimeRight.setText(millisToStringStamp(playerState.track.duration));
                    }

                    mSeekBar.setEnabled(true);
                }
            };

    public void onConnectAndAuthorizedClicked(View view) {
        connect(true);
    }

    private void connect(boolean showAuthView) {

        SpotifyAppRemote.disconnect(mSpotifyAppRemote);

        SpotifyAppRemote.connect(
                getContext(),
                new ConnectionParams.Builder(CLIENT_ID)
                        .setRedirectUri(REDIRECT_URI)
                        .showAuthView(showAuthView)
                        .build(),
                new Connector.ConnectionListener() {
                    @Override
                    public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                        mSpotifyAppRemote = spotifyAppRemote;
                        TelemetryFragment.this.onConnected();
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        logError(error);
                        TelemetryFragment.this.onDisconnected();
                    }
                });
    }

    public void onSubscribedToPlayerStateButtonClicked(View view) {

        if (mPlayerStateSubscription != null && !mPlayerStateSubscription.isCanceled()) {
            mPlayerStateSubscription.cancel();
            mPlayerStateSubscription = null;
        }


        mPlayerStateSubscription =
                (Subscription<PlayerState>)
                        mSpotifyAppRemote
                                .getPlayerApi()
                                .subscribeToPlayerState()
                                .setEventCallback(mPlayerStateEventCallback)
                                .setLifecycleCallback(
                                        new Subscription.LifecycleCallback() {
                                            @Override
                                            public void onStart() {
                                                logMessage("Event: start");
                                            }

                                            @Override
                                            public void onStop() {
                                                logMessage("Event: end");
                                            }
                                        })
                                .setErrorCallback(
                                        this::logError);
    }

    private void onConnected() {
        Log.d(TAG, "Spotify onConnected");
        onSubscribedToPlayerStateButtonClicked(null);
        mConnectButton.setVisibility(View.GONE);
    }

    private void onDisconnected(){
        mConnectButton.setVisibility(View.VISIBLE);
        new Handler().postDelayed(() -> {
            if (getContext() != null) {  // Check if fragment is still attached
                connect(false);
            }
        }, 5000); // Wai
    }



    public void onToggleShuffleButtonClicked(View view) {
        mSpotifyAppRemote
                .getPlayerApi()
                .toggleShuffle()
                .setResultCallback(
                        empty -> logMessage(getString(R.string.command_feedback, "toggle shuffle")))
                .setErrorCallback(mErrorCallback);
    }

    public void onToggleRepeatButtonClicked(View view) {
        mSpotifyAppRemote
                .getPlayerApi()
                .toggleRepeat()
                .setResultCallback(
                        empty -> logMessage(getString(R.string.command_feedback, "toggle repeat")))
                .setErrorCallback(mErrorCallback);
    }

    public void onSkipPreviousButtonClicked(View view) {
        mSpotifyAppRemote
                .getPlayerApi()
                .skipPrevious()
                .setResultCallback(
                        empty -> logMessage(getString(R.string.command_feedback, "skip previous")))
                .setErrorCallback(mErrorCallback);
    }

    public void onPlayPauseButtonClicked(View view) {
        mSpotifyAppRemote
                .getPlayerApi()
                .getPlayerState()
                .setResultCallback(
                        playerState -> {
                            if (playerState.isPaused) {
                                mSpotifyAppRemote
                                        .getPlayerApi()
                                        .resume()
                                        .setResultCallback(
                                                empty -> logMessage(getString(R.string.command_feedback, "play")))
                                        .setErrorCallback(mErrorCallback);
                            } else {
                                mSpotifyAppRemote
                                        .getPlayerApi()
                                        .pause()
                                        .setResultCallback(
                                                empty -> logMessage(getString(R.string.command_feedback, "pause")))
                                        .setErrorCallback(mErrorCallback);
                            }
                        });
    }

    public void onSkipNextButtonClicked(View view) {
        mSpotifyAppRemote
                .getPlayerApi()
                .skipNext()
                .setResultCallback(data -> logMessage(getString(R.string.command_feedback, "skip next")))
                .setErrorCallback(mErrorCallback);
    }

    public void onSeekBack(View view) {
        mSpotifyAppRemote
                .getPlayerApi()
                .seekToRelativePosition(-15000)
                .setResultCallback(data -> logMessage(getString(R.string.command_feedback, "seek back")))
                .setErrorCallback(mErrorCallback);
    }

    public void onSeekForward(View view) {
        mSpotifyAppRemote
                .getPlayerApi()
                .seekToRelativePosition(15000)
                .setResultCallback(data -> logMessage(getString(R.string.command_feedback, "seek fwd")))
                .setErrorCallback(mErrorCallback);
    }


    private void logError(Throwable throwable) {
        Context context = getContext();
        if(context != null){
            Toast.makeText(context, "Error occurred", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "", throwable);
        }
    }

    private void logMessage(String msg) {
//        logMessage(msg, Toast.LENGTH_SHORT);
    }

    private void logMessage(String msg, int duration) {
        Toast.makeText(requireContext(), msg, duration).show();
        Log.d(TAG, msg);
    }

    private class TrackProgressBar {

        private static final int LOOP_DURATION = 500;
        private final SeekBar mSeekBar;
        private final TextView trackProgress;
        private final Handler mHandler;

        private final SeekBar.OnSeekBarChangeListener mSeekBarChangeListener =
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        mSpotifyAppRemote
                                .getPlayerApi()
                                .seekTo(seekBar.getProgress())
                                .setErrorCallback(mErrorCallback);
                    }
                };

        private final Runnable mSeekRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        int progress = mSeekBar.getProgress();
                        mSeekBar.setProgress(progress + LOOP_DURATION);
                        trackProgress.setText(millisToStringStamp(progress));
                        mHandler.postDelayed(mSeekRunnable, LOOP_DURATION);
                    }
                };

        private TrackProgressBar(SeekBar seekBar, TextView trackProgress) {
            mSeekBar = seekBar;
            this.trackProgress = trackProgress;
            mSeekBar.setOnSeekBarChangeListener(mSeekBarChangeListener);
            mHandler = new Handler();
        }

        private void setDuration(long duration) {
            mSeekBar.setMax((int) duration);
        }

        private void update(long progress) {
            mSeekBar.setProgress((int) progress);
        }

        private void pause() {
            mHandler.removeCallbacks(mSeekRunnable);
        }

        private void unpause() {
            mHandler.removeCallbacks(mSeekRunnable);
            mHandler.postDelayed(mSeekRunnable, LOOP_DURATION);
        }
    }

    private String millisToStringStamp(long durationMs){
        // Calculate the duration in seconds
        int durationSeconds = (int) durationMs / 1000;

        // Calculate the number of minutes and seconds
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;

        // Format the duration string as "m:ss" or "mm:ss"
        return String.format("%d:%02d", minutes, seconds);
    }

}
