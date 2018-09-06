package com.chengfu.fuplayer.player.sys;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.Surface;

import com.chengfu.fuplayer.FuLog;
import com.chengfu.fuplayer.MediaSource;
import com.chengfu.fuplayer.PlayerError;
import com.chengfu.fuplayer.player.AbsPlayer;

import java.io.IOException;

public final class SysPlayer extends AbsPlayer {

    public static final String TAG = "SysPlayer";

    private final Context mContext;
    private MediaPlayer mMediaPlayer;


    private Surface mSurface;
    private int mAudioSession;
    private boolean mLooping;
    private float mVolume = 1.0f;

    private MediaSource mMediaSource;

    private PlayerError mPlayerError;
    private int mCurrentBufferPercentage;
    private int mVideoWidth;
    private int mVideoHeight;
    private boolean mRenderedFirstFrame;

    private boolean mSeekable;
    private boolean mPlayWhenReady;

    private int mCurrentState = -1;

    private long mSeekWhenPrepared;  // recording the seek position while preparing
    private boolean mIsPreparing;

    public SysPlayer(Context context) {
        this(context, null);
    }

    public SysPlayer(Context context, SysPlayerOption option) {
        mContext = context;
        mVideoWidth = 0;
        mVideoHeight = 0;
        mPlayerError = null;
        mRenderedFirstFrame = false;
        mCurrentBufferPercentage = 0;
        setPlayerState(mPlayWhenReady, STATE_IDLE);
    }

    private MediaPlayer createPlayer() {
        MediaPlayer mediaPlayer = new MediaPlayer();

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        if (mAudioSession != 0) {
            mediaPlayer.setAudioSessionId(mAudioSession);
        } else {
            mAudioSession = mediaPlayer.getAudioSessionId();
        }

        mediaPlayer.setLooping(mLooping);
        mediaPlayer.setVolume(mVolume, mVolume);
        mediaPlayer.setSurface(mSurface);

        mediaPlayer.setOnPreparedListener(mPreparedListener);
        mediaPlayer.setOnVideoSizeChangedListener(mVideoSizeChangedListener);
        mediaPlayer.setOnCompletionListener(mCompletionListener);
        mediaPlayer.setOnSeekCompleteListener(mSeekCompleteListener);
        mediaPlayer.setOnErrorListener(mErrorListener);
        mediaPlayer.setOnInfoListener(mInfoListener);
        mediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
        return mediaPlayer;
    }

    public boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_IDLE
                && !mIsPreparing);
    }

    private void openMedia() {
        if (mMediaSource == null || (mMediaSource.getPath() == null && mMediaSource.getUri() == null)) {
            FuLog.w(TAG, "this mediaSource is null or path and uri both are empty", new NullPointerException("mediaSource is null"));
            mPlayerError = PlayerError.create(PlayerError.MEDIA_ERROR_IO);
            submitError(mPlayerError);
            setPlayerState(mPlayWhenReady, STATE_IDLE);
            return;
        }

        try {
            mMediaPlayer = createPlayer();
            if (mMediaSource.getPath() != null) {
                mMediaPlayer.setDataSource(mMediaSource.getPath());
            } else {
                if (mMediaSource.getHeaders() != null) {
                    mMediaPlayer.setDataSource(mContext, mMediaSource.getUri(), mMediaSource.getHeaders());
                } else {
                    mMediaPlayer.setDataSource(mContext, mMediaSource.getUri());
                }
            }
            mIsPreparing = true;
            mMediaPlayer.prepareAsync();
            setPlayerState(mPlayWhenReady, STATE_BUFFERING);
            FuLog.i(TAG, "Set media source for the player: source=" + mMediaSource.toString());
        } catch (IOException e) {
            e.printStackTrace();
            FuLog.e(TAG, "Unable to open content: " + mMediaSource.toString(), e);
            mPlayerError = PlayerError.create(PlayerError.MEDIA_ERROR_IO);
            submitError(mPlayerError);
            setPlayerState(mPlayWhenReady, STATE_IDLE);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            FuLog.e(TAG, "Unable to open content: " + mMediaSource.toString(), e);
            mPlayerError = PlayerError.create(PlayerError.MEDIA_ERROR_IO);
            submitError(mPlayerError);
            setPlayerState(mPlayWhenReady, STATE_IDLE);
            return;
        }
    }

    private void setPlayerState(boolean playWhenReady, int state) {
        if (mPlayWhenReady == playWhenReady && mCurrentState == state) {
            return;
        }
        if (mCurrentState == STATE_IDLE) {
            mVideoWidth = 0;
            mVideoHeight = 0;
            mRenderedFirstFrame = false;
        }
        mPlayWhenReady = playWhenReady;
        mCurrentState = state;
        submitStateChanged(playWhenReady, state);
    }

    @Override
    public PlayerError getPlayerError() {
        return mPlayerError;
    }

    @Override
    public int getPlayerState() {
        return mCurrentState;
    }

    @Override
    public int getAudioSessionId() {
        if (mAudioSession == 0) {
            MediaPlayer foo = new MediaPlayer();
            mAudioSession = foo.getAudioSessionId();
            foo.release();
        }
        return mAudioSession;
    }

    @Override
    public void setMediaSource(MediaSource mediaSource) {
        stop();
        mMediaSource = mediaSource;
        mVideoWidth = 0;
        mVideoHeight = 0;
        mPlayerError = null;
        mRenderedFirstFrame = false;
        mSeekable = false;
        mSeekWhenPrepared = 0;
        mCurrentBufferPercentage = 0;
        openMedia();
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
        if (mPlayWhenReady == playWhenReady) {
            return;
        }
        if (isInPlaybackState()) {
            if (playWhenReady) {
                mMediaPlayer.start();
            } else if (isPlaying()) {
                mMediaPlayer.pause();
            }
        }
        setPlayerState(playWhenReady, mCurrentState);
    }

    @Override
    public boolean getPlayWhenReady() {
        return mPlayWhenReady;
    }

    @Override
    public void setLooping(boolean looping) {
        if (mLooping == looping) {
            return;
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.setLooping(looping);
        }
        mLooping = looping;
    }

    @Override
    public boolean isLooping() {
        return mLooping;
    }

    @Override
    public void setVolume(float volume) {
        if (mVolume == volume) {
            return;
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume(volume, volume);
        }
        mVolume = volume;
    }

    @Override
    public float getVolume() {
        return mVolume;
    }

    @Override
    public int getBufferPercentage() {
        return mCurrentBufferPercentage;
    }

    @Override
    public boolean hasRenderedFirstFrame() {
        return mRenderedFirstFrame;
    }

    @Override
    public int getVideoWidth() {
        return mVideoWidth;
    }

    @Override
    public int getVideoHeight() {
        return mVideoHeight;
    }

    @Override
    public void setVideoSurface(Surface surface) {
        if (mSurface == surface) {
            return;
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.setSurface(surface);
        }
        mSurface = surface;
    }

    @Override
    public long getCurrentPosition() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;

    }

    @Override
    public long getDuration() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getDuration();
        }
        return -1;
    }

    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    @Override
    public boolean isSeekable() {
        return mSeekable;
    }

    @Override
    public void seekTo(long msec) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo((int) msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    @Override
    public void resume() {
        openMedia();
    }

    @Override
    public void stop() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mPlayerError = null;
            mIsPreparing = false;
            setPlayerState(mPlayWhenReady, STATE_IDLE);
        }
    }

    @Override
    public void release() {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mMediaSource = null;
            mPlayWhenReady = false;
            mSurface = null;
            mPlayerError = null;
            mIsPreparing = false;
            setPlayerState(mPlayWhenReady, STATE_IDLE);
        }
    }


    private int getErrorCode(int code) {
        switch (code) {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                return PlayerError.MEDIA_ERROR_UNKNOWN;
            case MediaPlayer.MEDIA_ERROR_IO:
                return PlayerError.MEDIA_ERROR_IO;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                return PlayerError.MEDIA_ERROR_SERVER_DIED;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                return PlayerError.MEDIA_ERROR_TIMED_OUT;
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                return PlayerError.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK;
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                return PlayerError.MEDIA_ERROR_MALFORMED;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                return PlayerError.MEDIA_ERROR_UNSUPPORTED;
            default:
                return PlayerError.MEDIA_ERROR_UNKNOWN;
        }
    }

    final MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer mp) {
            FuLog.d(TAG, "onPrepared...");
            mIsPreparing = false;
            long seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }
            setPlayerState(mPlayWhenReady, STATE_READY);
            if (mPlayWhenReady) {
                mMediaPlayer.start();
            }
        }
    };


    final MediaPlayer.OnVideoSizeChangedListener mVideoSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    FuLog.i(TAG, "onVideoSizeChanged : width=" + width + ",height=" + height);
                    mVideoWidth = width;
                    mVideoHeight = height;
                    submitVideoSizeChanged(width, height, 0, 0);
                }
            };

    final MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    FuLog.i(TAG, "onCompletion");
                    setPlayerState(mPlayWhenReady, STATE_ENDED);
                }
            };

    final MediaPlayer.OnInfoListener mInfoListener =
            new MediaPlayer.OnInfoListener() {
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    switch (what) {
                        case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                            FuLog.i(TAG, "onInfo : video_rendering_start");
                            mRenderedFirstFrame = true;
                            submitRenderedFirstFrame();
                            break;
                        case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                            FuLog.i(TAG, "onInfo : buffering_start");
                            setPlayerState(mPlayWhenReady, STATE_BUFFERING);
                            break;
                        case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                            FuLog.i(TAG, "onInfo : buffering_end");
                            setPlayerState(mPlayWhenReady, STATE_READY);
                            break;
                        case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                            mSeekable = false;
                            FuLog.i(TAG, "onInfo : not_seekable");
                            break;
                    }
                    return true;
                }
            };

    final MediaPlayer.OnSeekCompleteListener mSeekCompleteListener = new MediaPlayer.OnSeekCompleteListener() {
        @Override
        public void onSeekComplete(MediaPlayer mp) {
            FuLog.d(TAG, "EVENT_CODE_SEEK_COMPLETE");
            submitSeekComplete();
        }
    };

    final MediaPlayer.OnErrorListener mErrorListener =
            new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer mp, int framework_err, int impl_err) {
                    FuLog.d(TAG, "Error : code" + getErrorCode(framework_err));
                    mPlayerError = PlayerError.create(getErrorCode(framework_err));
                    submitError(mPlayerError);
                    setPlayerState(mPlayWhenReady, STATE_IDLE);
                    return true;
                }
            };

    final MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            new MediaPlayer.OnBufferingUpdateListener() {
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    mCurrentBufferPercentage = percent;
                    submitBufferingUpdate(percent);
                }
            };
}