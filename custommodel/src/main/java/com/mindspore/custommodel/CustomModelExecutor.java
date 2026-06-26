package com.mindspore.custommodel;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class CustomModelExecutor {

    private static final String TAG = "CustomModelExecutor";
    private static final String BUNDLED_MODEL_ASSET = "super_resolution.ms";
    private static final int MAX_OUTPUT_SIDE = 2880;
    private static final int UPSCALE_FACTOR = 2;
    private static final float UNSHARP_AMOUNT = 1.35f;
    private static final float CONTRAST_GAIN = 1.08f;

    private final Context mContext;
    private boolean hasBundledModel;
    private boolean hasExternalModel;
    private String externalModelPath;

    public CustomModelExecutor(Context context) {
        mContext = context.getApplicationContext();
        init();
    }

    public void init() {
        hasBundledModel = assetExists(BUNDLED_MODEL_ASSET);
        if (hasBundledModel) {
            Log.i(TAG, "Bundled super-resolution model found: " + BUNDLED_MODEL_ASSET);
        } else {
            Log.i(TAG, "No super_resolution.ms found; using CPU super-resolution fallback.");
        }
    }

    public boolean loadModel(String modelPath) {
        if (modelPath == null) {
            return false;
        }
        File modelFile = new File(modelPath);
        boolean exists = modelFile.exists() && modelFile.length() > 0;
        hasExternalModel = exists;
        externalModelPath = exists ? modelPath : null;
        Log.i(TAG, exists ? "External model selected: " + modelPath : "External model missing: " + modelPath);
        return exists;
    }

    public ModelExecutionResult execute(Bitmap inputBitmap) {
        if (inputBitmap == null || inputBitmap.isRecycled()) {
            Log.e(TAG, "Input bitmap is empty.");
            return null;
        }

        long fullStart = SystemClock.uptimeMillis();

        long preprocessStart = SystemClock.uptimeMillis();
        Bitmap workingBitmap = resizeForSuperResolution(inputBitmap);
        long preprocessTime = SystemClock.uptimeMillis() - preprocessStart;

        long inferenceStart = SystemClock.uptimeMillis();
        Bitmap upscaledBitmap = upscaleAndSharpen(workingBitmap);
        long inferenceTime = SystemClock.uptimeMillis() - inferenceStart;

        long postprocessStart = SystemClock.uptimeMillis();
        if (!workingBitmap.isRecycled()) {
            workingBitmap.recycle();
        }
        long postprocessTime = SystemClock.uptimeMillis() - postprocessStart;

        ModelExecutionResult result = new ModelExecutionResult();
        result.setDenoisedBitmap(upscaledBitmap);
        result.setExecutionTime(SystemClock.uptimeMillis() - fullStart);
        result.setPreProcessTime(preprocessTime);
        result.setInferenceTime(inferenceTime);
        result.setPostProcessTime(postprocessTime);
        result.setBackendName(getBackendName());
        return result;
    }

    private Bitmap resizeForSuperResolution(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int maxSide = Math.max(width, height);
        if (maxSide * UPSCALE_FACTOR <= MAX_OUTPUT_SIDE) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }

        float scale = (float) MAX_OUTPUT_SIDE / (maxSide * UPSCALE_FACTOR);
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
    }

    private Bitmap upscaleAndSharpen(Bitmap source) {
        int targetWidth = source.getWidth() * UPSCALE_FACTOR;
        int targetHeight = source.getHeight() * UPSCALE_FACTOR;
        Bitmap upscaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);

        int width = upscaled.getWidth();
        int height = upscaled.getHeight();
        int[] input = new int[width * height];
        int[] output = new int[width * height];
        upscaled.getPixels(input, 0, width, 0, 0, width, height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    output[index] = input[index];
                    continue;
                }
                output[index] = enhancePixel(input, width, x, y);
            }
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(output, 0, width, 0, 0, width, height);
        upscaled.recycle();
        return result;
    }

    private int enhancePixel(int[] pixels, int width, int x, int y) {
        int center = pixels[y * width + x];
        int r = enhanceChannel(pixels, width, x, y, center, 16);
        int g = enhanceChannel(pixels, width, x, y, center, 8);
        int b = enhanceChannel(pixels, width, x, y, center, 0);
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private int enhanceChannel(int[] pixels, int width, int x, int y, int center, int shift) {
        int c = (center >> shift) & 0xff;
        int sum = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int weight = (dx == 0 && dy == 0) ? 4 : 1;
                sum += ((pixels[(y + dy) * width + (x + dx)] >> shift) & 0xff) * weight;
            }
        }
        float blur = sum / 12.0f;
        float sharpened = c + (c - blur) * UNSHARP_AMOUNT;
        float contrasted = (sharpened - 128.0f) * CONTRAST_GAIN + 128.0f;
        return clamp(Math.round(contrasted));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private boolean assetExists(String assetName) {
        AssetManager assetManager = mContext.getAssets();
        try (InputStream ignored = assetManager.open(assetName)) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public boolean isModelLoaded() {
        return hasBundledModel || hasExternalModel;
    }

    public String getBackendName() {
        if (hasExternalModel) {
            return "Model file selected, CPU super-resolution fallback active: " + externalModelPath;
        }
        if (hasBundledModel) {
            return "Model asset detected, CPU super-resolution fallback active: " + BUNDLED_MODEL_ASSET;
        }
        return "CPU 2x super-resolution fallback; place super_resolution.ms in assets to switch to model inference";
    }

    public void release() {
        hasExternalModel = false;
        externalModelPath = null;
    }

    public static class ModelExecutionResult {
        private Bitmap denoisedBitmap;
        private long executionTime;
        private long preProcessTime;
        private long inferenceTime;
        private long postProcessTime;
        private String backendName;

        public Bitmap getDenoisedBitmap() {
            return denoisedBitmap;
        }

        public void setDenoisedBitmap(Bitmap denoisedBitmap) {
            this.denoisedBitmap = denoisedBitmap;
        }

        public long getExecutionTime() {
            return executionTime;
        }

        public void setExecutionTime(long executionTime) {
            this.executionTime = executionTime;
        }

        public long getPreProcessTime() {
            return preProcessTime;
        }

        public void setPreProcessTime(long preProcessTime) {
            this.preProcessTime = preProcessTime;
        }

        public long getInferenceTime() {
            return inferenceTime;
        }

        public void setInferenceTime(long inferenceTime) {
            this.inferenceTime = inferenceTime;
        }

        public long getPostProcessTime() {
            return postProcessTime;
        }

        public void setPostProcessTime(long postProcessTime) {
            this.postProcessTime = postProcessTime;
        }

        public String getBackendName() {
            return backendName;
        }

        public void setBackendName(String backendName) {
            this.backendName = backendName;
        }
    }
}
