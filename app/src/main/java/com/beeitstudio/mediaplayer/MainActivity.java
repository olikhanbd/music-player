package com.beeitstudio.mediaplayer;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.beeitstudio.mediaplayer.clients.MediaBrowserHelper;
import com.beeitstudio.mediaplayer.service.AudioLibrary;
import com.beeitstudio.mediaplayer.service.AudioService;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "oli_" + MainActivity.class.getSimpleName();

    private ImageView mAlbumArt;
    private TextView mTitleTextView;
    private TextView mArtistTextView;
    private ImageView mMediaControlsImage;
    private MediaSeekBar mSeekBarAudio;

    private MediaBrowserHelper mMediaBrowserHelper;

    private boolean mIsPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTitleTextView = findViewById(R.id.song_title);
        mArtistTextView = findViewById(R.id.song_artist);
        mAlbumArt = findViewById(R.id.album_art);
        mMediaControlsImage = findViewById(R.id.media_controls);
        mSeekBarAudio = findViewById(R.id.seekbar_audio);

        final ClickListener clickListener = new ClickListener();
        findViewById(R.id.button_previous).setOnClickListener(clickListener);
        findViewById(R.id.button_play).setOnClickListener(clickListener);
        findViewById(R.id.button_next).setOnClickListener(clickListener);

        mMediaBrowserHelper = new MediaBrowserConnection(this);
        mMediaBrowserHelper.registerCallback(new MediaBrowserListener());
    }

    @Override
    public void onStart() {
        super.onStart();
        mMediaBrowserHelper.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mSeekBarAudio.disconnectController();
        mMediaBrowserHelper.onStop();
    }

    /**
     * Customize the connection to our {@link androidx.media.MediaBrowserServiceCompat}
     * and implement our app specific desires.
     */
    private class MediaBrowserConnection extends MediaBrowserHelper {

        private MediaBrowserConnection(Context context) {
            super(context, AudioService.class);
        }

        @Override
        protected void onConnected(@NonNull MediaControllerCompat mediaController) {
            Log.d(TAG, "onConnected: CALLED");
            mSeekBarAudio.setMediaController(mediaController);
        }

        @Override
        protected void onChildrenLoaded(@NonNull String parentId,
                                        @NonNull List<MediaBrowserCompat.MediaItem> children) {
            super.onChildrenLoaded(parentId, children);

            final MediaControllerCompat mediaController = getmMediaController();

            // Queue up all media items for this simple sample.
            for (final MediaBrowserCompat.MediaItem mediaItem : children) {
                mediaController.addQueueItem(mediaItem.getDescription());
            }

            // Call prepare now so pressing play just works.
            mediaController.getTransportControls().prepare();
        }
    }

    /**
     * Implementation of the {@link MediaControllerCompat.Callback} methods we're interested in.
     * <p>
     * Here would also be where one could override
     * {@code onQueueChanged(List<MediaSessionCompat.QueueItem> queue)} to get informed when items
     * are added or removed from the queue. We don't do this here in order to keep the UI
     * simple.
     */

    private class MediaBrowserListener extends MediaControllerCompat.Callback {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            mIsPlaying = state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING;
            mMediaControlsImage.setPressed(mIsPlaying);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            Log.d(TAG, "onMetadataChanged: CALLED");
            if (metadata == null)
                return;

            mTitleTextView.setText(
                    metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
            mArtistTextView.setText(
                    metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
            mAlbumArt.setImageBitmap(AudioLibrary.getAlbumBitmap(
                    MainActivity.this,
                    metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)));
        }

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
        }

        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            super.onQueueChanged(queue);
        }
    }

    /**
     * Convenience class to collect the click listeners together.
     * <p>
     * In a larger app it's better to split the listeners out or to use your favorite
     * library.
     */
    private class ClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.button_previous:
                    mMediaBrowserHelper.getTransportControlls().skipToPrevious();
                    break;
                case R.id.button_play:
                    if (mIsPlaying) {
                        mMediaBrowserHelper.getTransportControlls().pause();
                    } else {
                        mMediaBrowserHelper.getTransportControlls().play();
                    }
                    break;
                case R.id.button_next:
                    mMediaBrowserHelper.getTransportControlls().skipToNext();
                    break;
            }
        }
    }

}
