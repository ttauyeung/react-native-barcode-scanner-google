/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ekreutz.barcodescanner.camera;

import android.Manifest;
import android.content.Context;
import android.content.res.Configuration;
import android.support.annotation.RequiresPermission;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.Detector;

import java.io.IOException;

public class CameraSourcePreview extends ViewGroup {
    private static final String TAG = "CameraSourcePreview";

    public static final int FILL_MODE_COVER = 0;
    public static final int FILL_MODE_FIT = 1;

    private Context mContext;
    private SurfaceView mSurfaceView;
    private boolean mStartRequested;
    private boolean mSurfaceAvailable;
    private CameraSource mCameraSource;
    private int mWidth = 0, mHeight = 0;
    private int fillMode = FILL_MODE_COVER;

    public CameraSourcePreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mStartRequested = false;
        mSurfaceAvailable = false;

        mSurfaceView = new SurfaceView(context);
        mSurfaceView.getHolder().addCallback(new SurfaceCallback());
        addView(mSurfaceView);
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    public void start(CameraSource cameraSource) throws IOException, SecurityException {
        if (cameraSource == null) {
            stop();
        }

        mCameraSource = cameraSource;

        if (mCameraSource != null) {
            mStartRequested = true;
            startIfReady();
        }
    }

    public void stop() {
        if (mCameraSource != null) {
            mCameraSource.stop();

        }
    }

    public void release() {
        if (mCameraSource != null) {
            mCameraSource.release();
            mCameraSource = null;
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private void startIfReady() throws IOException, SecurityException {
        if (mStartRequested && mSurfaceAvailable && mCameraSource != null) {
            mCameraSource.start(mSurfaceView.getHolder());
            mStartRequested = false;
        }
    }

    // Can be quite heavy, since it stops and restarts the camera
    @RequiresPermission(Manifest.permission.CAMERA)
    public void replaceBarcodeDetector(Detector<?> detector, boolean shouldResume) throws IOException, SecurityException {
        if (mCameraSource != null) {
            mCameraSource.release();
            mCameraSource.setDetector(detector);

            if (shouldResume && mSurfaceAvailable) {
                start(mCameraSource);
            }
        }
    }

    // Set the camera stream fill mode
    public void setFillMode(int fillMode) {
        if (fillMode != FILL_MODE_COVER && fillMode != FILL_MODE_FIT) return;
        this.fillMode = fillMode;
        previewLayout();
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder surface) {
            mSurfaceAvailable = true;
            try {
                startIfReady();
            } catch (SecurityException se) {
                Log.e(TAG,"Do not have permission to start the camera", se);
            } catch (IOException e) {
                Log.e(TAG, "Could not start camera source.", e);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surface) {
            mSurfaceAvailable = false;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            previewLayout();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mWidth = r - l;
        mHeight = b - t;
        previewLayout();
    }

    /* layout the surface that we draw the camera stream on */
    private void previewLayout() {
        if (mWidth == 0 || mHeight == 0) return;

        // Step 1: determine the size of the camera stream
        // --------------------------------

        int previewWidth = 800;
        int previewHeight = 800;

        if (mCameraSource != null) {
            mCameraSource.setRotation();
             Size size = mCameraSource.getPreviewSize();
             if (size != null) {
                 previewWidth = size.getWidth();
                 previewHeight = size.getHeight();
             }
        }

        // Swap width and height sizes when in portrait, since it will be rotated 90 degrees
        if (isPortraitMode()) {
            int tmp = previewWidth;
            previewWidth = previewHeight;
            previewHeight = tmp;
        }

        // Step 2. Determine how to scale the stream so that it fits square in this view
        // --------------------------------

        int childWidth;
        int childHeight;
        int childXOffset = 0;
        int childYOffset = 0;

        float widthRatio = (float) mWidth / (float) previewWidth;
        float heightRatio =  (float) mHeight / (float) previewHeight;

        if(widthRatio > heightRatio){
            childWidth = mWidth;
            childHeight = (int) ((float) previewHeight * widthRatio);
            childYOffset = (childHeight - mHeight) / 2;
        }else{
            childWidth = (int)((float) previewWidth * heightRatio);
            childHeight = mHeight;
            childXOffset = (childWidth - mWidth) / 2;
        }

        // apply the layout to the surface

        for (int i=0; i< getChildCount(); ++i) {
            getChildAt(i).layout(-1 * childXOffset, -1 * childYOffset, childWidth - childXOffset, childHeight - childYOffset);
        }

        // Step 3: try starting the stream again (if needed) after our modifications
        // --------------------------------

        try {
            startIfReady();
        } catch (SecurityException se) {
            Log.e(TAG,"Do not have permission to start the camera", se);
        } catch (IOException e) {
            Log.e(TAG, "Could not start camera source.", e);
        }
    }

    private boolean isPortraitMode() {
        int orientation = mContext.getResources().getConfiguration().orientation;
        return orientation == Configuration.ORIENTATION_PORTRAIT;
    }
}
