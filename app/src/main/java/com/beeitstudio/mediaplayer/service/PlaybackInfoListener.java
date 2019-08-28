package com.beeitstudio.mediaplayer.service;

import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

/**
 * Listener to provide state updates from {@link AudioPlayer} (the media player)
 * to {@link AudioService} (the service that holds our {@link MediaSessionCompat}.
 */
public abstract class PlaybackInfoListener {

    public abstract void onPlaybackStateChange(PlaybackStateCompat state);

    public void onPlaybackCompleted() {
    }
}