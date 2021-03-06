package org.aaa.chain.activity;

import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.constraint.ConstraintLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.aaa.chain.ChainApplication;
import org.aaa.chain.JSInteraction;
import org.aaa.chain.R;
import org.aaa.chain.entities.ResumeRequestEntity;
import org.aaa.chain.entities.ResumeResponseEntity;
import org.aaa.chain.utils.FileUtils;
import org.aaa.chain.utils.HttpUtils;
import org.aaa.chain.utils.PBEUtils;
import org.aaa.chain.views.ProgressResponseBody;
import org.json.JSONException;
import org.json.JSONObject;

public class UploadResumeActivity extends BaseActivity implements ProgressResponseBody.ProgressListener {

    private ConstraintLayout layout1;
    private ConstraintLayout layout2;
    private ProgressBar progressBar;
    private TextView tvPercent;
    private EditText inputPrice;
    private ProgressDialog dialog;
    private Button btnResumeUpload;
    private Button btnUploadDone;
    private String fileName;
    private boolean onlyModifyInfo = false;
    private String hashId;
    private String price;

    private JSONObject object;
    private ResumeRequestEntity requestEntity = new ResumeRequestEntity();

    @Override public int initLayout() {
        return R.layout.activity_upload_resume;
    }

    @Override public void getViewById() {

        layout1 = $(R.id.cl_layout1);
        layout2 = $(R.id.cl_layout2);
        progressBar = $(R.id.progress);
        tvPercent = $(R.id.tv_percent);
        inputPrice = $(R.id.et_resume_price);
        btnUploadDone = $(R.id.btn_upload_done);
        btnResumeUpload = $(R.id.btn_resume_upload);
        $(R.id.btn_resume_upload).setOnClickListener(this);
        btnUploadDone.setOnClickListener(this);

        inputPrice.setText("1");
        inputPrice.setSelection(1);

        try {
            object = new JSONObject(getIntent().getExtras().getString("metadata"));
            fileName = object.getString("name");
            hashId = object.getString("hashId");
            price = object.getString("price");
            if (fileName != null && hashId != null) {
                onlyModifyInfo = true;
                btnResumeUpload.setText(getResources().getString(R.string.confirm_modify));
                inputPrice.setText(price);
                inputPrice.setSelection(price.length());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override public void onClick(View v) {

        switch (v.getId()) {

            case R.id.btn_resume_upload:
                if (onlyModifyInfo) {
                    try {
                        object.put("price", inputPrice.getText().toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    modifyInfo(hashId, object.toString());
                } else {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, 100);
                }
                break;

            case R.id.btn_upload_done:
                finish();
                break;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && data != null) {
            Uri imageUri;
            if (data != null) {
                String imagePath = null;
                imageUri = data.getData();
                if (DocumentsContract.isDocumentUri(UploadResumeActivity.this, imageUri)) {
                    String docId = DocumentsContract.getDocumentId(imageUri);
                    if ("com.android.providers.media.documents".equals(imageUri.getAuthority())) {
                        String id = docId.split(":")[1];
                        String selection = MediaStore.Images.Media._ID + "=" + id;
                        imagePath = getImagePath(UploadResumeActivity.this, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
                    } else if ("com.android.downloads.documents".equals(imageUri.getAuthority())) {
                        Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(docId));
                        imagePath = getImagePath(UploadResumeActivity.this, contentUri, null);
                    } else if ("com.android.externalstorage.documents".equals(imageUri.getAuthority())) {
                        final String[] split = docId.split(":");
                        final String type = split[0];
                        if ("primary".equalsIgnoreCase(type)) {
                            imagePath = Environment.getExternalStorageDirectory() + "/" + split[1];
                        }
                    }
                } else if ("content".equalsIgnoreCase(imageUri.getScheme())) {
                    imagePath = getImagePath(UploadResumeActivity.this, imageUri, null);
                } else if ("file".equalsIgnoreCase(imageUri.getScheme())) {
                    imagePath = imageUri.getPath();
                }

                if (FileUtils.getInstance().FormatFileSize(imagePath) > 10) {
                    Log.i("info", "file size:" + FileUtils.getInstance().FormatFileSize(imagePath));
                    Toast.makeText(this, "file is too large", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {

                    File file = PBEUtils.getInstance()
                            .encryptFile(UploadResumeActivity.this, imagePath, org.aaa.chain.Constant.getCurrentPrivateKey(),
                                    org.aaa.chain.Constant.getCurrentPublicKey().getBytes());
                    Log.i("info", "file path:" + file.getAbsolutePath());
                    requestEntity.setFilepath(file.getAbsolutePath());
                    requestEntity.setFilename(file.getName());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                uploadFile();
            }
        }
    }

    private String getImagePath(Context context, Uri uri, String selection) {
        String path = null;
        Cursor cursor = context.getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }

    private void modifyInfo(String hashId, String modifyContent) {
        ProgressDialog dialog = ProgressDialog.show(UploadResumeActivity.this, "waiting...", "modifying...");
        JSInteraction.getInstance().getSignature(modifyContent, org.aaa.chain.Constant.getCurrentPrivateKey(), new JSInteraction.JSCallBack() {
            @Override public void onSuccess(String... stringArray) {
                HttpUtils.getInstance().modifyCustomInfo(hashId, stringArray[0], modifyContent, new HttpUtils.ServerCallBack() {
                    @Override public void onFailure(Call call, IOException e) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                dialog.dismiss();
                                Toast.makeText(UploadResumeActivity.this, "modify failure", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override public void onResponse(Call call, Response response) {
                        try {
                            String body = response.body().string();
                            if (response.code() == 200) {
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        dialog.dismiss();
                                        ResumeResponseEntity resumeResponseEntity = new Gson().fromJson(body, ResumeResponseEntity.class);
                                        ChainApplication.getInstance().getBaseInfo().getDocs().get(0).setExtra(resumeResponseEntity.getExtra());
                                        Toast.makeText(UploadResumeActivity.this, "modify successful", Toast.LENGTH_SHORT).show();
                                        finish();
                                    }
                                });
                            } else {
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        dialog.dismiss();
                                        Toast.makeText(UploadResumeActivity.this, "modify failure" + body, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            @Override public void onProgress() {

            }

            @Override public void onError(String error) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        dialog.dismiss();
                        Toast.makeText(UploadResumeActivity.this, "modify failure", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void uploadFile() {
        dialog = ProgressDialog.show(UploadResumeActivity.this, "waiting....", "uploading...");
        if (TextUtils.isEmpty(inputPrice.getText().toString())) {
            Toast.makeText(UploadResumeActivity.this, getResources().getString(R.string.setting_price), Toast.LENGTH_SHORT).show();
        }
        try {
            JSONObject object1 = new JSONObject();
            object1.put("onlyHash", false);
            requestEntity.setOptions(object1.toString());

            object.put("name", requestEntity.getFilename());
            object.put("price", inputPrice.getText().toString());
            requestEntity.setMetadata(object.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        requestEntity.setAccount(org.aaa.chain.Constant.getCurrentAccount());

        JSInteraction.getInstance().getSignature(object.toString(), org.aaa.chain.Constant.getCurrentPrivateKey(), new JSInteraction.JSCallBack() {
            @Override public void onSuccess(String... stringArray) {
                requestEntity.setSignature(stringArray[0]);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        dialog.dismiss();
                        layout1.setVisibility(View.GONE);
                        layout2.setVisibility(View.VISIBLE);
                        btnUploadDone.setClickable(false);
                        btnUploadDone.setBackgroundColor(Color.GRAY);
                    }
                });

                HttpUtils.getInstance().addFileResource(UploadResumeActivity.this, requestEntity, new HttpUtils.ServerCallBack() {
                    @Override public void onFailure(Call call, IOException e) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                Toast.makeText(UploadResumeActivity.this, e.getMessage() + "please retry", Toast.LENGTH_SHORT).show();
                                layout1.setVisibility(View.VISIBLE);
                                layout2.setVisibility(View.GONE);
                            }
                        });
                    }

                    @Override public void onResponse(Call call, Response response) {
                        ResponseBody body = response.body();
                        if (response.code() == 200) {
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    btnUploadDone.setClickable(true);
                                    btnUploadDone.setBackgroundColor(getResources().getColor(R.color.view_button_bg));
                                    Toast.makeText(UploadResumeActivity.this, getResources().getString(R.string.upload_successful),
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            try {
                                String error = body.string();
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        Toast.makeText(UploadResumeActivity.this, error, Toast.LENGTH_SHORT).show();
                                        layout1.setVisibility(View.VISIBLE);
                                        layout2.setVisibility(View.GONE);
                                    }
                                });
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }

            @Override public void onProgress() {

            }

            @Override public void onError(String error) {

            }
        });
    }

    @Override public void update(long bytesRead, long contentLength, boolean done) {
        final int percent = (int) (100 * bytesRead / contentLength);
        runOnUiThread(new Runnable() {
            @Override public void run() {
                progressBar.setProgress(percent);
                tvPercent.setText(percent + "%");
            }
        });
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        JSInteraction.getInstance().removeListener();
    }
}
