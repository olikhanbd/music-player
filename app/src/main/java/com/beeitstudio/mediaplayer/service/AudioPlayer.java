package com.beeitstudio.mediaplayer.service;

import android.content.Context;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;

import com.beeitstudio.mediaplayer.R;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

public class AudioPlayer extends PlayerAdapter {

    private static final String TAG = "oli_" + AudioPlayer.class.getSimpleName();

    private final Context mContext;
    private PlaybackInfoListener mPlaybackInfoListener;

    //exoplayer
    private SimpleExoPlayer mExoPlayer;
    private DataSource.Factory mDataSourceFactory;

    private int mState;
    private boolean mCurrentMediaPlayedToCompletion;
    private MediaMetadataCompat mCurrentMedia;


    public AudioPlayer(@NonNull Context context, PlaybackInfoListener playbackInfoListener) {
        super(context);
        mContext = context.getApplicationContext();
        mPlaybackInfoListener = playbackInfoListener;
    }

    private void initializeExoPlayer() {

        if (mExoPlayer == null) {

            mDataSourceFactory = new DefaultDataSourceFactory(
                    mContext,
                    Util.getUserAgent(mContext, mContext.getString(R.string.app_name)));

            mExoPlayer = ExoPlayerFactory.newSimpleInstance(
                    mContext,
                    new DefaultRenderersFactory(mContext),
                    new DefaultTrackSelector());
            mExoPlayer.addListener(new Player.EventListener() {
                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    Log.d(TAG, "onPlayerStateChanged: state: " + playbackState);
                    if (playbackState == Player.STATE_ENDED) {
                        mPlaybackInfoListener.onPlaybackCompleted();

                        // Set the state to "paused" because it most closely matches the state
                        // in MediaPlayer with regards to available state transitions compared
                        // to "stop".
                        // Paused allows: seekTo(), start(), pause(), stop()
                        // Stop allows: stop()
                        setNewState(PlaybackStateCompat.STATE_PAUSED);
                    }
                }
            });
        }
    }

    private void release() {
        if (mExoPlayer != null) {
            mExoPlayer.release();
            mExoPlayer = null;
        }
    }

    private void setNewState(@PlaybackStateCompat.State int newPlayState) {
        mState = newPlayState;

        // Whether playback goes to completion, or whether it is stopped, the
        // mCurrentMediaPlayedToCompletion is set to true.
        if (mState == PlaybackState.STATE_STOPPED) {
            mCurrentMediaPlayedToCompletion = true;
        }

        final long reportPosition = mExoPlayer == null ? 0 : mExoPlayer.getCurrentPosition();

        final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
        stateBuilder.setActions(getAvailableActions());
        stateBuilder.setState(
                mState,
                reportPosition,
                1.0f,
                SystemClock.elapsedRealtime());
        mPlaybackInfoListener.onPlaybackStateChange(stateBuilder.build());
    }

    private void playFile(MediaMetadataCompat metadata) {
        String mediaId = metadata.getDescription().getMediaId();
        boolean mediaChanged = (mCurrentMedia == null
                || !mediaId.equals(mCurrentMedia.getDescription().getMediaId()));
        if (mCurrentMediaPlayedToCompletion) {
            // Last audio file was played to completion, the resourceId hasn't changed, but the
            // player was released, so force a reload of the media file for playback.
            mediaChanged = true;
            mCurrentMediaPlayedToCompletion = false;
        }

        if (!mediaChanged) {
            if (!isPlaying()) {
                play();
            }
            return;
        } else {
            release();
        }

        mCurrentMedia = metadata;

        initializeExoPlayer();

        try {
            MediaSource audioSource = new ExtractorMediaSource.Factory(mDataSourceFactory)
                    .createMediaSource(Uri.parse(mCurrentMedia
                            .getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI)));
            mExoPlayer.prepare(audioSource);
        } catch (Exception e) {
            Log.e(TAG, "playFile: Exception: " + e.getMessage());
            throw new RuntimeException("Failed to play uri: "
                    + mCurrentMedia.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI), e);
        }

        play();

    }

    /**
     * Set the current capabilities available on this session. Note: If a capability is not
     * listed in the bitmask of capabilities then the MediaSession will not handle it. For
     * example, if you don't want ACTION_STOP to be handled by the MediaSession, then don't
     * included it in the bitmask that's returned.
     */
    @PlaybackStateCompat.Actions
    private long getAvailableActions() {
        long actions = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        switch (mState) {
            case PlaybackStateCompat.STATE_STOPPED:
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE;
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                actions |= PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_SEEK_TO;
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_STOP;
                break;
            default:
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_PAUSE;
        }
        return actions;
    }

    @Override
    public void playFromMedia(MediaMetadataCompat metadata) {
        playFile(metadata);
    }

    @Override
    public MediaMetadataCompat getCurrentMedia() {
        return mCurrentMedia;
    }

    @Override
    public boolean isPlaying() {
        return mExoPlayer != null && mExoPlayer.getPlayWhenReady();
    }

    @Override
    protected void onPlay() {
        if (mExoPlayer != null && !mExoPlayer.getPlayWhenReady()) {
            mExoPlayer.setPlayWhenReady(true);
            setNewState(PlaybackStateCompat.STATE_PLAYING);
        }
    }

    @Override
    protected void onPause() {
        if (mExoPlayer != null && mExoPlayer.getPlayWhenReady()) {
            mExoPlayer.setPlayWhenReady(false);
            setNewState(PlaybackStateCompat.STATE_PAUSED);
        }
    }

    @Override
    protected void onStop() {
        // Regardless of whether or not the MediaPlayer has been created / started, the state must
        // be updated, so that MediaNotificationManager can take down the notification.
        setNewState(PlaybackStateCompat.STATE_STOPPED);
        release();
    }

    @Override
    public void seekTo(long position) {
        if (mExoPlayer != null) {
            mExoPlayer.seekTo((int) position);

            // Set the state (to the current state) because the position changed and should
            // be reported to clients.
            setNewState(mState);
        }
    }

    @Override
    public void setVolume(float volume) {
        if (mExoPlayer != null) {
            mExoPlayer.setVolume(volume);
        }
    }
}
