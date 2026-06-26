package com.mindspore.custommodel;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.launcher.ARouter;
import com.mindspore.common.utils.Utils;
import com.mindspore.customview.dialog.NoticeDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Route(path = "/custommodel/CustomModelMainActivity")
public class CustomModelMainActivity extends AppCompatActivity {
    private static final String TAG = "CustomModelMainActivity";
    private static final int RC_CHOOSE_PHOTO = 1;
    private static final int RC_CHOOSE_CAMERA = 2;
    private static final int RC_PICK_MODEL = 3;

    private NoticeDialog noticeDialog;
    private FrameLayout imagePreviewContainer;
    private ImageView imgPreview;
    private ImageView imgResult;
    private TextView tvImagePlaceholder;
    private ProgressBar progressBar;
    private Button btnSaveResult;
    private TextView tvOutput;

    private Uri imageUri;
    private Bitmap selectedBitmap;
    private Bitmap resultBitmap;
    private boolean isRunningModel = false;
    private CustomModelExecutor modelExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ARouter.getInstance().inject(this);
        setContentView(R.layout.activity_custom_model_main);
        init();
    }

    private void init() {
        Toolbar toolbar = findViewById(R.id.custom_model_toolbar);
        toolbar.setTitle(getString(R.string.custom_model_title));
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        imagePreviewContainer = findViewById(R.id.image_preview_container);
        imgPreview = findViewById(R.id.img_preview);
        imgResult = findViewById(R.id.img_result);
        tvImagePlaceholder = findViewById(R.id.tv_image_placeholder);
        progressBar = findViewById(R.id.progress);
        btnSaveResult = findViewById(R.id.btn_save_result);
        tvOutput = findViewById(R.id.tv_output);

        modelExecutor = new CustomModelExecutor(this);
        tvOutput.setText(getString(R.string.denoise_ready, modelExecutor.getBackendName()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_setting_app, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.item_help) {
            showHelpDialog();
        } else if (itemId == R.id.item_more) {
            Utils.openBrowser(this, "www.mindspore.cn");
        }
        return super.onOptionsItemSelected(item);
    }

    private void showHelpDialog() {
        noticeDialog = new NoticeDialog(this);
        noticeDialog.setTitleString(getString(R.string.explain_title));
        noticeDialog.setContentString(getString(R.string.explain_custom_model));
        noticeDialog.setYesOnclickListener(() -> noticeDialog.dismiss());
        noticeDialog.show();
    }

    public void onClickSelectModel(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, RC_PICK_MODEL);
    }

    public void onClickSelectPhoto(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK, null);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, RC_CHOOSE_PHOTO);
    }

    public void onClickTakePhoto(View view) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        if (photoDir != null && !photoDir.exists()) {
            photoDir.mkdirs();
        }
        File imageFile = new File(photoDir, "super_resolution_photo.jpeg");
        imageUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".fileprovider", imageFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, RC_CHOOSE_CAMERA);
    }

    public void onClickExecute(View view) {
        executeDenoise();
    }

    public void onClickSaveResult(View view) {
        saveResultImage();
    }

    private void executeDenoise() {
        if (selectedBitmap == null) {
            Toast.makeText(this, R.string.select_image_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (isRunningModel) {
            Toast.makeText(this, R.string.denoise_running, Toast.LENGTH_SHORT).show();
            return;
        }
        if (resultBitmap != null && !resultBitmap.isRecycled()) {
            Toast.makeText(this, R.string.result_already_generated, Toast.LENGTH_SHORT).show();
            tvOutput.setText(getString(
                    R.string.result_already_generated_detail,
                    selectedBitmap.getWidth(),
                    selectedBitmap.getHeight(),
                    resultBitmap.getWidth(),
                    resultBitmap.getHeight(),
                    modelExecutor.getBackendName()));
            return;
        }

        isRunningModel = true;
        progressBar.setVisibility(View.VISIBLE);
        tvOutput.setText(R.string.denoise_progress);

        new Thread(() -> {
            try {
                CustomModelExecutor.ModelExecutionResult result = modelExecutor.execute(selectedBitmap);
                runOnUiThread(() -> showDenoiseResult(result));
            } catch (Exception e) {
                Log.e(TAG, "Super resolution failed", e);
                runOnUiThread(() -> {
                    isRunningModel = false;
                    progressBar.setVisibility(View.INVISIBLE);
                    tvOutput.setText(getString(R.string.denoise_failed_with_error, e.getMessage()));
                });
            }
        }).start();
    }

    private void showDenoiseResult(CustomModelExecutor.ModelExecutionResult result) {
        isRunningModel = false;
        progressBar.setVisibility(View.INVISIBLE);

        if (result == null || result.getDenoisedBitmap() == null) {
            tvOutput.setText(R.string.denoise_failed);
            return;
        }

        if (resultBitmap != null && !resultBitmap.isRecycled()) {
            resultBitmap.recycle();
        }
        resultBitmap = result.getDenoisedBitmap();
        imgResult.setImageBitmap(resultBitmap);
        imgResult.setVisibility(View.VISIBLE);
        btnSaveResult.setVisibility(View.VISIBLE);
        tvOutput.setText(getString(
                R.string.denoise_result,
                result.getBackendName(),
                selectedBitmap.getWidth(),
                selectedBitmap.getHeight(),
                resultBitmap.getWidth(),
                resultBitmap.getHeight(),
                result.getExecutionTime(),
                result.getPreProcessTime(),
                result.getInferenceTime(),
                result.getPostProcessTime()));
    }

    private void showSelectedImage(Bitmap bitmap) {
        if (selectedBitmap != null && selectedBitmap != bitmap && !selectedBitmap.isRecycled()) {
            selectedBitmap.recycle();
        }
        selectedBitmap = bitmap;
        imgPreview.setImageBitmap(bitmap);
        imgResult.setImageDrawable(null);
        imgResult.setVisibility(View.INVISIBLE);
        btnSaveResult.setVisibility(View.GONE);
        tvImagePlaceholder.setVisibility(View.GONE);
        imagePreviewContainer.setVisibility(View.VISIBLE);
        tvOutput.setText(getString(R.string.denoise_image_loaded, bitmap.getWidth(), bitmap.getHeight(), modelExecutor.getBackendName()));
    }

    private void saveResultImage() {
        if (resultBitmap == null || resultBitmap.isRecycled()) {
            Toast.makeText(this, R.string.no_result_to_save, Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = "super_resolution_" + System.currentTimeMillis() + ".png";
        try {
            Uri imageUri = createImageUri(fileName);
            if (imageUri == null) {
                Toast.makeText(this, R.string.save_result_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            OutputStream outputStream = getContentResolver().openOutputStream(imageUri);
            if (outputStream == null) {
                Toast.makeText(this, R.string.save_result_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            boolean saved = resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.close();
            Toast.makeText(this, saved ? getString(R.string.save_result_success, fileName) : getString(R.string.save_result_failed), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Save result failed", e);
            Toast.makeText(this, getString(R.string.save_result_failed_with_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @Nullable
    private Uri createImageUri(String fileName) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MindSporeSuperResolution");
            return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        }

        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File targetDir = new File(picturesDir, "MindSporeSuperResolution");
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return null;
        }
        File imageFile = new File(targetDir, fileName);
        values.put(MediaStore.Images.Media.DATA, imageFile.getAbsolutePath());
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    private void showOriginImage() {
        try {
            if (imageUri == null) {
                Toast.makeText(this, R.string.image_uri_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                showSelectedImage(bitmap);
            } else {
                Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading image", e);
            Toast.makeText(this, getString(R.string.image_load_failed_with_error, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void showOriginCamera() {
        showOriginImage();
    }

    @Nullable
    private File copyUriToFile(Uri uri, String fileName) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return null;
            }

            File dst = new File(getCacheDir(), fileName);
            FileOutputStream outputStream = new FileOutputStream(dst);
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            inputStream.close();
            outputStream.close();
            return dst;
        } catch (IOException e) {
            Log.e(TAG, "Error copying file", e);
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == RC_CHOOSE_PHOTO) {
            if (data != null && data.getData() != null) {
                imageUri = data.getData();
                showOriginImage();
            }
        } else if (requestCode == RC_CHOOSE_CAMERA) {
            showOriginCamera();
        } else if (requestCode == RC_PICK_MODEL) {
            if (data != null && data.getData() != null) {
                File dst = copyUriToFile(data.getData(), "super_resolution.ms");
                if (dst == null) {
                    Toast.makeText(this, R.string.model_copy_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean success = modelExecutor.loadModel(dst.getAbsolutePath());
                if (success) {
                    Toast.makeText(this, getString(R.string.model_loaded, dst.getAbsolutePath()), Toast.LENGTH_SHORT).show();
                    tvOutput.setText(getString(R.string.denoise_ready, modelExecutor.getBackendName()));
                } else {
                    Toast.makeText(this, R.string.model_load_failed, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (modelExecutor != null) {
            modelExecutor.release();
        }
        if (selectedBitmap != null && !selectedBitmap.isRecycled()) {
            selectedBitmap.recycle();
        }
        if (resultBitmap != null && !resultBitmap.isRecycled()) {
            resultBitmap.recycle();
        }
    }
}
