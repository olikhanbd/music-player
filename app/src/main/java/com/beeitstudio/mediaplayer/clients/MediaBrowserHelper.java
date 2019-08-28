package com.beeitstudio.mediaplayer.clients;

import android.content.ComponentName;
import android.content.Context;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media.MediaBrowserServiceCompat;

import java.util.ArrayList;
import java.util.List;

public class MediaBrowserHelper {

    private static final String TAG = "oli_" + MediaBrowserHelper.class.getSimpleName();

    private final Context mContext;
    private final Class<? extends MediaBrowserServiceCompat> mMediaBrowserServiceClass;
    private final List<MediaControllerCompat.Callback> mCallbackList = new ArrayList<>();
    private MediaBrowserCompat mMediaBrowser;
    private MediaControllerCompat mMediaController;
    private MediaBrowserConnectionCallback mMediaBrowserConnectionCallback;
    private MediaControllerCallback mMediaControllerCallback;
    private MediaBrowserSubscriptionCallback mMediaBrowserSubscriptionCallback;

    public MediaBrowserHelper(Context context,
                              Class<? extends MediaBrowserServiceCompat> mediaBrowserServiceClass) {
        mContext = context;
        mMediaBrowserServiceClass = mediaBrowserServiceClass;

        mMediaBrowserConnectionCallback = new MediaBrowserConnectionCallback();
        mMediaControllerCallback = new MediaControllerCallback();
        mMediaBrowserSubscriptionCallback = new MediaBrowserSubscriptionCallback();

    }

    public void onStart() {
        if (mMediaBrowser == null) {
            mMediaBrowser = new MediaBrowserCompat(
                    mContext,
                    new ComponentName(mContext, mMediaBrowserServiceClass),
                    mMediaBrowserConnectionCallback,
                    null);
            mMediaBrowser.connect();
        }

        Log.d(TAG, "onStart: MediaBrowser creating and connecting");
    }

    public void onStop() {
        if (mMediaController != null) {
            mMediaController.unregisterCallback(mMediaControllerCallback);
            mMediaController = null;
        }
        if (mMediaBrowser != null && mMediaBrowser.isConnected()) {
            mMediaBrowser.disconnect();
            mMediaBrowser = null;
        }
        resetState();
        Log.d(TAG, "onStop: Releasing MediaController, Disconnecting from MediaBrowser");
    }

    /**
     * The internal state of the app needs to revert to what it looks like when it started before
     * any connections to the {@link com.beeitstudio.mediaplayer.service.AudioService}
     * happens via the {@link MediaSessionCompat}.
     */
    private void resetState() {
        performOnAllCallbacks(new CallbackCommand() {
            @Override
            public void perform(@NonNull MediaControllerCompat.Callback callback) {
                callback.onPlaybackStateChanged(null);
            }
        });
    }

    private void performOnAllCallbacks(@NonNull CallbackCommand command) {
        for (MediaControllerCompat.Callback callback : mCallbackList) {
            if (callback != null) {
                command.perform(callback);
            }
        }
    }

    /**
     * Called after connecting with a {@link MediaBrowserServiceCompat}.
     * <p>
     * Override to perform processing after a connection is established.
     *
     * @param mediaController {@link MediaControllerCompat} associated with the connected
     *                        MediaSession.
     */
    protected void onConnected(@NonNull MediaControllerCompat mediaController) {
    }

    /**
     * Called after loading a browsable {@link MediaBrowserCompat.MediaItem}
     *
     * @param parentId The media ID of the parent item.
     * @param children List (possibly empty) of child items.
     */
    protected void onChildrenLoaded(@NonNull String parentId,
                                    @NonNull List<MediaBrowserCompat.MediaItem> children) {
    }

    /**
     * Called when the {@link MediaBrowserServiceCompat} connection is lost.
     */
    protected void onDisconnected() {
    }

    @NonNull
    protected final MediaControllerCompat getmMediaController() {
        if (mMediaController == null) {
            throw new IllegalStateException("Media controller is null");
        }

        return mMediaController;
    }

    public MediaControllerCompat.TransportControls getTransportControlls() {
        if (mMediaController == null) {
            Log.d(TAG, "getTransportControlls: mediacontroller is null");
            throw new IllegalStateException("Media controller is null");
        }

        return mMediaController.getTransportControls();
    }

    public void registerCallback(MediaControllerCompat.Callback callback) {

        if (callback != null) {
            mCallbackList.add(callback);

            //update with latest metadata/playback state
            if (mMediaController != null) {
                final MediaMetadataCompat metadata = mMediaController.getMetadata();
                if (metadata != null) {
                    callback.onMetadataChanged(metadata);
                }
                final PlaybackStateCompat playbackState = mMediaController.getPlaybackState();
                if (playbackState != null) {
                    callback.onPlaybackStateChanged(playbackState);
                }
            }
        }
    }

    /**
     * Helper for more easily performing operations on all listening clients.
     */
    private interface CallbackCommand {
        void perform(@NonNull MediaControllerCompat.Callback callback);
    }

    // Receives callbacks from the MediaBrowser when it has successfully connected to the
    // MediaBrowserService (AudioService).
    private class MediaBrowserConnectionCallback extends MediaBrowserCompat.ConnectionCallback {
        @Override
        public void onConnected() {
            try {

                mMediaController = new MediaControllerCompat(mContext, mMediaBrowser.getSessionToken());
                mMediaController.registerCallback(mMediaControllerCallback);

                // Sync existing MediaSession state to the UI.
                mMediaControllerCallback.onMetadataChanged(mMediaController.getMetadata());
                mMediaControllerCallback.onPlaybackStateChanged(mMediaController.getPlaybackState());

                MediaBrowserHelper.this.onConnected(mMediaController);

            } catch (Exception e) {
                Log.e(TAG, "onConnected: Exception " + e.getMessage());
                throw new RuntimeException("Exception in mediabrowser connection ", e);
            }

            mMediaBrowser.subscribe(mMediaBrowser.getRoot(), mMediaBrowserSubscriptionCallback);
        }
    }

    // Receives callbacks from the MediaController and updates the UI state,
    // i.e.: Which is the current item, whether it's playing or paused, etc.
    private class MediaControllerCallback extends MediaControllerCompat.Callback {

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            performOnAllCallbacks(new CallbackCommand() {
                @Override
                public void perform(@NonNull MediaControllerCompat.Callback callback) {
                    callback.onMetadataChanged(metadata);
                }
            });
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            performOnAllCallbacks(new CallbackCommand() {
                @Override
                public void perform(@NonNull MediaControllerCompat.Callback callback) {
                    callback.onPlaybackStateChanged(state);
                }
            });
        }

        // This might happen if the MusicService is killed while the Activity is in the
        // foreground and onStart() has been called (but not onStop()).
        @Override
        public void onSessionDestroyed() {
            resetState();
            onPlaybackStateChanged(null);
            MediaBrowserHelper.this.onDisconnected();
        }
    }

    // Receives callbacks from the MediaBrowser when the MediaBrowserService has loaded new media
    // that is ready for playback.
    private class MediaBrowserSubscriptionCallback extends MediaBrowserCompat.SubscriptionCallback {
        @Override
        public void onChildrenLoaded(@NonNull String parentId,
                                     @NonNull List<MediaBrowserCompat.MediaItem> children) {
            MediaBrowserHelper.this.onChildrenLoaded(parentId, children);
        }
    }
}
