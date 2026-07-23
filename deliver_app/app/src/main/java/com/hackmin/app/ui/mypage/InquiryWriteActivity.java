package com.hackmin.app.ui.mypage;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.hackmin.app.R;
import com.hackmin.app.data.model.inquiry.InquiryCreateRequest;
import com.hackmin.app.data.model.inquiry.InquiryDto;
import com.hackmin.app.data.model.inquiry.InquiryImageDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.util.ImageLoader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 1:1 문의 작성/수정 화면.
 * - POST /inquiries               (신규 작성)
 * - PUT  /inquiries/{id}          (수정)
 * - POST /inquiries/{id}/images   (사진 첨부, 선택)
 */
public class InquiryWriteActivity extends AppCompatActivity {

    private static final String EXTRA_INQUIRY_ID = "inquiry_id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_CATEGORY = "category";
    private static final String EXTRA_CONTENT = "content";

    /** 첨부 이미지 최대 장수(기존 첨부 + 신규 선택 합산). */
    private static final int MAX_IMAGES = 5;

    // 실제 카테고리 값(백엔드 Inquiry.Category)과 화면에 보여줄 라벨. "선택 안 함"은 없음(필수 항목).
    private static final String[] CATEGORY_VALUES = {"order", "delivery", "refund", "coupon", "account", "etc"};
    private static final String[] CATEGORY_LABELS = {"주문/결제", "배달", "환불/교환", "쿠폰/프로모션", "계정", "기타"};

    /** 문의 수정 화면으로 진입하는 인텐트를 만든다. */
    public static Intent editIntent(Context ctx, InquiryDto inquiry) {
        Intent i = new Intent(ctx, InquiryWriteActivity.class);
        i.putExtra(EXTRA_INQUIRY_ID, inquiry.getId());
        i.putExtra(EXTRA_TITLE, inquiry.getTitle());
        i.putExtra(EXTRA_CATEGORY, inquiry.getCategory());
        i.putExtra(EXTRA_CONTENT, inquiry.getContent());
        return i;
    }

    private long editingId = -1;

    private TextInputEditText etTitle;
    private TextInputEditText etContent;
    private Spinner spCategory;
    private LinearLayout containerImages;
    private Button btnAddImage;
    private Button btnSubmit;

    private final List<Uri> selectedImages = new ArrayList<>();
    private List<InquiryImageDto> existingImages = new ArrayList<>();
    private ActivityResultLauncher<String> imagePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inquiry_write);

        editingId = getIntent().getLongExtra(EXTRA_INQUIRY_ID, -1L);
        boolean editMode = editingId >= 0;

        ImageButton btnBack = findViewById(R.id.btnInquiryWriteBack);
        TextView tvTitle = findViewById(R.id.tvInquiryWriteTitle);
        btnSubmit = findViewById(R.id.btnInquirySubmit);
        etTitle = findViewById(R.id.etInquiryTitle);
        etContent = findViewById(R.id.etInquiryContent);
        spCategory = findViewById(R.id.spInquiryCategory);
        containerImages = findViewById(R.id.containerInquiryImages);
        btnAddImage = findViewById(R.id.btnInquiryAddImage);

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, CATEGORY_LABELS);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(categoryAdapter);

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris == null || uris.isEmpty()) return;
                    for (Uri uri : uris) {
                        if (selectedImages.size() >= MAX_IMAGES) {
                            Toast.makeText(this,
                                    "사진은 최대 " + MAX_IMAGES + "장까지 첨부할 수 있습니다.",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        }
                        selectedImages.add(uri);
                    }
                    renderImages();
                });

        btnBack.setOnClickListener(v -> finish());
        btnAddImage.setOnClickListener(v -> imagePicker.launch("image/*"));
        btnSubmit.setOnClickListener(v -> submit());

        if (editMode) {
            tvTitle.setText("문의 수정");
            btnSubmit.setText("수정하기");
            etTitle.setText(getIntent().getStringExtra(EXTRA_TITLE));
            etContent.setText(getIntent().getStringExtra(EXTRA_CONTENT));
            String category = getIntent().getStringExtra(EXTRA_CATEGORY);
            for (int i = 0; i < CATEGORY_VALUES.length; i++) {
                if (CATEGORY_VALUES[i].equals(category)) {
                    spCategory.setSelection(i);
                    break;
                }
            }
            loadExistingImages();
        }

        renderImages();
    }

    private void loadExistingImages() {
        ApiClient.inquiryApi(this).getInquiry(editingId).enqueue(new Callback<InquiryDto>() {
            @Override
            public void onResponse(Call<InquiryDto> call, Response<InquiryDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<InquiryImageDto> images = response.body().getImages();
                    if (images != null) {
                        renderExistingImages(images);
                    }
                }
            }

            @Override
            public void onFailure(Call<InquiryDto> call, Throwable t) {
                // 무시: 기존 이미지 미리보기 실패는 작성 흐름을 막지 않는다.
            }
        });
    }

    // ── 이미지 썸네일 렌더링 ──────────────────────────────

    private void renderExistingImages(List<InquiryImageDto> images) {
        existingImages = images;
        renderImages();
    }

    private void renderImages() {
        containerImages.removeAllViews();
        int sizePx = dp(80);
        int marginPx = dp(8);

        // 이미 서버에 업로드된 사진(수정 화면에서만, 읽기 전용).
        for (InquiryImageDto image : existingImages) {
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMarginEnd(marginPx);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ImageLoader.load(iv, image.getImage());
            containerImages.addView(iv);
        }

        // 새로 선택한 사진(탭하면 제거).
        for (int i = 0; i < selectedImages.size(); i++) {
            final Uri uri = selectedImages.get(i);
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMarginEnd(marginPx);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageURI(uri);
            iv.setOnClickListener(v -> {
                selectedImages.remove(uri);
                renderImages();
            });
            containerImages.addView(iv);
        }

        btnAddImage.setEnabled(existingImages.size() + selectedImages.size() < MAX_IMAGES);
    }

    // ── 전송 ─────────────────────────────────────────────

    private void submit() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String content = etContent.getText() != null ? etContent.getText().toString().trim() : "";
        String category = CATEGORY_VALUES[spCategory.getSelectedItemPosition()];

        if (title.length() < 2 || title.length() > 16) {
            Toast.makeText(this, "제목은 2자 이상 16자 이하로 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (content.length() < 5) {
            Toast.makeText(this, "내용은 5자 이상 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        setSubmitting(true);
        InquiryCreateRequest req = new InquiryCreateRequest(title, category, content);

        Callback<InquiryDto> callback = new Callback<InquiryDto>() {
            @Override
            public void onResponse(Call<InquiryDto> call, Response<InquiryDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    long inquiryId = response.body().getId();
                    if (selectedImages.isEmpty()) {
                        done();
                    } else {
                        uploadImages(inquiryId);
                    }
                } else {
                    setSubmitting(false);
                    Toast.makeText(InquiryWriteActivity.this,
                            "문의 등록에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<InquiryDto> call, Throwable t) {
                setSubmitting(false);
                Toast.makeText(InquiryWriteActivity.this,
                        "네트워크 연결 실패 (백엔드 서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        };

        if (editingId >= 0) {
            ApiClient.inquiryApi(this).updateInquiry(editingId, req).enqueue(callback);
        } else {
            ApiClient.inquiryApi(this).createInquiry(req).enqueue(callback);
        }
    }

    /** 문의 생성/수정 후 첨부 이미지를 순차 업로드한다. 개별 실패는 건너뛴다. */
    private void uploadImages(long inquiryId) {
        uploadNext(inquiryId, 0, 0);
    }

    private void uploadNext(long inquiryId, int index, int failed) {
        if (index >= selectedImages.size()) {
            if (failed > 0) {
                Toast.makeText(this, "사진 " + failed + "장은 업로드하지 못했습니다.", Toast.LENGTH_SHORT).show();
            }
            done();
            return;
        }

        MultipartBody.Part part;
        try {
            part = toPart(selectedImages.get(index));
        } catch (Exception e) {
            uploadNext(inquiryId, index + 1, failed + 1);
            return;
        }

        ApiClient.inquiryApi(this).uploadInquiryImage(inquiryId, part)
                .enqueue(new Callback<InquiryImageDto>() {
                    @Override
                    public void onResponse(Call<InquiryImageDto> call, Response<InquiryImageDto> response) {
                        uploadNext(inquiryId, index + 1, response.isSuccessful() ? failed : failed + 1);
                    }

                    @Override
                    public void onFailure(Call<InquiryImageDto> call, Throwable t) {
                        uploadNext(inquiryId, index + 1, failed + 1);
                    }
                });
    }

    private MultipartBody.Part toPart(Uri uri) throws Exception {
        String mime = getContentResolver().getType(uri);
        if (mime == null) mime = "image/*";

        byte[] bytes;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("이미지를 열 수 없습니다.");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
            bytes = buf.toByteArray();
        }

        RequestBody body = RequestBody.create(bytes, MediaType.parse(mime));
        String fileName = "inquiry_" + System.currentTimeMillis() + guessExt(mime);
        return MultipartBody.Part.createFormData("image", fileName, body);
    }

    private String guessExt(String mime) {
        if (mime.contains("png")) return ".png";
        if (mime.contains("webp")) return ".webp";
        return ".jpg";
    }

    private void done() {
        Toast.makeText(this, editingId >= 0 ? "문의가 수정되었습니다." : "문의가 등록되었습니다.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void setSubmitting(boolean submitting) {
        btnSubmit.setEnabled(!submitting);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
