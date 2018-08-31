package com.chengfu.fuplayer.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;

import java.util.concurrent.locks.ReentrantLock;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * 一个可以调整视频缩放比例及旋转角度的TextureView
 *
 * @author ChengFu
 */
final class VideoTextureView extends TextureView {

    private static final String TAG = "VideoTextureView";//TAG
    private static final int MAX_DEGREES = 360;//最大旋转角度
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    private static final int[] GL_CLEAR_CONFIG_ATTRIBUTES = {
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_RENDERABLE_TYPE, EGL10.EGL_WINDOW_BIT,
            EGL10.EGL_NONE, 0,
            EGL10.EGL_NONE
    };

    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    private static final int[] GL_CLEAR_CONTEXT_ATTRIBUTES = {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL10.EGL_NONE
    };


    @NonNull
    protected Point lastNotifiedSize = new Point(0, 0);
    @NonNull
    protected Point videoSize = new Point(0, 0);

    @NonNull
    protected MatrixManager matrixManager = new MatrixManager();

    @NonNull
    protected AttachedListener attachedListener = new AttachedListener();
    @NonNull
    protected GlobalLayoutMatrixListener globalLayoutMatrixListener = new GlobalLayoutMatrixListener();
    @NonNull
    protected final ReentrantLock globalLayoutMatrixListenerLock = new ReentrantLock(true);

    @IntRange(from = 0, to = 359)
    protected int requestedUserRotation = 0;
    @IntRange(from = 0, to = 359)
    protected int requestedConfigurationRotation = 0;

    protected boolean measureBasedOnAspectRatio;

    public VideoTextureView(Context context) {
        super(context);
    }

    public VideoTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VideoTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!measureBasedOnAspectRatio) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            notifyOnSizeChangeListener(getMeasuredWidth(), getMeasuredHeight());
            return;
        }

        int width = getDefaultSize(videoSize.x, widthMeasureSpec);
        int height = getDefaultSize(videoSize.y, heightMeasureSpec);

        if (videoSize.x <= 0 || videoSize.y <= 0) {
            setMeasuredDimension(width, height);
            notifyOnSizeChangeListener(width, height);
            return;
        }

        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
            width = widthSpecSize;
            height = heightSpecSize;

            // for compatibility, we adjust size based on aspect ratio
            if (videoSize.x * height < width * videoSize.y) {
                width = height * videoSize.x / videoSize.y;
            } else if (videoSize.x * height > width * videoSize.y) {
                height = width * videoSize.y / videoSize.x;
            }
        } else if (widthSpecMode == MeasureSpec.EXACTLY) {
            // only the width is fixed, adjust the height to match aspect ratio if possible
            width = widthSpecSize;
            height = width * videoSize.y / videoSize.x;
            if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                // couldn't match aspect ratio within the constraints
                height = heightSpecSize;
            }
        } else if (heightSpecMode == MeasureSpec.EXACTLY) {
            // only the height is fixed, adjust the width to match aspect ratio if possible
            height = heightSpecSize;
            width = height * videoSize.x / videoSize.y;
            if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                // couldn't match aspect ratio within the constraints
                width = widthSpecSize;
            }
        } else {
            // neither the width nor the height are fixed, try to use actual video size
            width = videoSize.x;
            height = videoSize.y;
            if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                // too tall, decrease both width and height
                height = heightSpecSize;
                width = height * videoSize.x / videoSize.y;
            }
            if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                // too wide, decrease both width and height
                width = widthSpecSize;
                height = width * videoSize.y / videoSize.x;
            }
        }

        setMeasuredDimension(width, height);
        notifyOnSizeChangeListener(width, height);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        updateMatrixOnLayout();
        super.onConfigurationChanged(newConfig);
    }

    public void clearSurface() {
        if (getSurfaceTexture() == null) {
            return;
        }

        try {
            EGL10 gl10 = (EGL10) EGLContext.getEGL();
            EGLDisplay display = gl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            gl10.eglInitialize(display, null);

            EGLConfig[] configs = new EGLConfig[1];
            gl10.eglChooseConfig(display, GL_CLEAR_CONFIG_ATTRIBUTES, configs, configs.length, new int[1]);
            EGLContext context = gl10.eglCreateContext(display, configs[0], EGL10.EGL_NO_CONTEXT, GL_CLEAR_CONTEXT_ATTRIBUTES);
            EGLSurface eglSurface = gl10.eglCreateWindowSurface(display, configs[0], getSurfaceTexture(), new int[]{EGL10.EGL_NONE});

            gl10.eglMakeCurrent(display, eglSurface, eglSurface, context);
            gl10.eglSwapBuffers(display, eglSurface);
            gl10.eglDestroySurface(display, eglSurface);
            gl10.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            gl10.eglDestroyContext(display, context);

            gl10.eglTerminate(display);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing surface", e);
        }
    }

    /**
     * Updates the stored videoSize and updates the default buffer size
     * in the backing texture view.
     *
     * @param width  The width for the video
     * @param height The height for the video
     * @return True if the surfaces DefaultBufferSize was updated
     */
    protected boolean updateVideoSize(int width, int height) {
        matrixManager.setIntrinsicVideoSize(width, height);
        updateMatrixOnLayout();

        videoSize.x = width;
        videoSize.y = height;

        if (width == 0 || height == 0) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            SurfaceTexture surfaceTexture = getSurfaceTexture();
            if (surfaceTexture != null) {
                surfaceTexture.setDefaultBufferSize(width, height);
            } else {
                return false;
            }
        }

        return true;
    }

    /**
     * Sets the scaling method to use for the video
     *
     * @param scaleType The scale type to use
     */
    public void setScaleType(@NonNull PlayerView.ScaleType scaleType) {
        matrixManager.scale(this, scaleType);
    }

    /**
     * Retrieves the current {@link PlayerView.ScaleType} being used
     *
     * @return The current {@link PlayerView.ScaleType} being used
     */
    @NonNull
    public PlayerView.ScaleType getScaleType() {
        return matrixManager.getCurrentScaleType();
    }

    /**
     * Specifies if the {@link #onMeasure(int, int)} should pay attention to the specified
     * aspect ratio for the video (determined from {@link #videoSize}.
     *
     * @param enabled True if {@link #onMeasure(int, int)} should pay attention to the videos aspect ratio
     */
    public void setMeasureBasedOnAspectRatioEnabled(boolean enabled) {
        this.measureBasedOnAspectRatio = enabled;
        requestLayout();
    }

    /**
     * Sets the rotation for the Video
     *
     * @param rotation The rotation to apply to the video
     * @param fromUser True if the rotation was requested by the user, false if it is from a video configuration
     */
    public void setVideoRotation(@IntRange(from = 0, to = 359) int rotation, boolean fromUser) {
        setVideoRotation(fromUser ? rotation : requestedUserRotation, !fromUser ? rotation : requestedConfigurationRotation);
    }

    /**
     * Specifies the rotation that should be applied to the video for both the user
     * requested value and the value specified in the videos configuration.
     *
     * @param userRotation          The rotation the user wants to apply
     * @param configurationRotation The rotation specified in the configuration for the video
     */
    public void setVideoRotation(@IntRange(from = 0, to = 359) int userRotation, @IntRange(from = 0, to = 359) int configurationRotation) {
        requestedUserRotation = userRotation;
        requestedConfigurationRotation = configurationRotation;

        matrixManager.rotate(this, (userRotation + configurationRotation) % MAX_DEGREES);
    }

    /**
     * Requests for the Matrix to be updated on layout changes.  This will
     * ensure that the scaling is correct and the rotation is not lost or
     * applied incorrectly.
     */
    protected void updateMatrixOnLayout() {
        globalLayoutMatrixListenerLock.lock();

        // if we're not attached defer adding the layout listener until we are
        if (getWindowToken() == null) {
            addOnAttachStateChangeListener(attachedListener);
        } else {
            getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutMatrixListener);
        }

        globalLayoutMatrixListenerLock.unlock();
    }

    /**
     * Performs the functionality to notify the listener that the
     * size of the surface has changed filtering out duplicate calls.
     *
     * @param width  The new width
     * @param height The new height
     */
    protected void notifyOnSizeChangeListener(int width, int height) {
        if (lastNotifiedSize.x == width && lastNotifiedSize.y == height) {
            return;
        }

        lastNotifiedSize.x = width;
        lastNotifiedSize.y = height;

        updateMatrixOnLayout();
    }

    /**
     * This is separated from the {@link VideoTextureView#onAttachedToWindow()}
     * so that we have control over when it is added and removed
     */
    private class AttachedListener implements OnAttachStateChangeListener {
        @Override
        public void onViewAttachedToWindow(View view) {
            globalLayoutMatrixListenerLock.lock();

            getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutMatrixListener);
            removeOnAttachStateChangeListener(this);

            globalLayoutMatrixListenerLock.unlock();
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            //Purposefully left blank
        }
    }

    /**
     * Listens to the global layout to reapply the scale
     */
    private class GlobalLayoutMatrixListener implements ViewTreeObserver.OnGlobalLayoutListener {
        @Override
        public void onGlobalLayout() {
            // Updates the scale to make sure one is applied
            setScaleType(matrixManager.getCurrentScaleType());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
            } else {
                //noinspection deprecation
                getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }
        }
    }
}