package com.hackmin.app.ui.review;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hackmin.app.R;
import com.hackmin.app.data.model.review.ReviewCreateRequest;
import com.hackmin.app.data.model.review.ReviewDto;
import com.hackmin.app.data.model.review.ReviewImageDto;
import com.hackmin.app.network.ApiClient;

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
 * 리뷰 작성 화면.
 * - POST /reviews                     (별점 + 내용)
 * - POST /reviews/{id}/images (선택)  (사진 여러 장)
 *
 * 진입: 주문내역/추적(배달완료 주문) 또는 음식점 상세.
 *   필수 extra: EXTRA_RESTAURANT_ID
 *   선택 extra: EXTRA_ORDER_ID (없으면 -1), EXTRA_RESTAURANT_NAME
 */
public class WriteReviewActivity extends AppCompatActivity {

    public static final String EXTRA_RESTAURANT_ID = "restaurant_id";
    public static final String EXTRA_ORDER_ID = "order_id";
    public static final String EXTRA_RESTAURANT_NAME = "restaurant_name";

    /** 첨부 이미지 최대 장수 */
    private static final int MAX_IMAGES = 5;

    private long restaurantId = -1;
    private long orderId = -1;

    private RatingBar rbRating;
    private EditText etContent;
    private LinearLayout containerImages;
    private Button btnAddImage;
    private Button btnSubmit;

    private final List<Uri> selectedImages = new ArrayList<>();
    private ActivityResultLauncher<String> imagePicker;

    /** 편의 진입 헬퍼. */
    public static Intent newIntent(Context ctx, long restaurantId, long orderId, String restaurantName) {
        Intent i = new Intent(ctx, WriteReviewActivity.class);
        i.putExtra(EXTRA_RESTAURANT_ID, restaurantId);
        i.putExtra(EXTRA_ORDER_ID, orderId);
        i.putExtra(EXTRA_RESTAURANT_NAME, restaurantName);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_review);

        restaurantId = getIntent().getLongExtra(EXTRA_RESTAURANT_ID, -1L);
        orderId = getIntent().getLongExtra(EXTRA_ORDER_ID, -1L);
        if (restaurantId < 0) {
            Toast.makeText(this, "잘못된 접근입니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btnBack);
        TextView tvTargetName = findViewById(R.id.tvTargetName);
        rbRating = findViewById(R.id.rbRating);
        etContent = findViewById(R.id.etContent);
        containerImages = findViewById(R.id.containerImages);
        btnAddImage = findViewById(R.id.btnAddImage);
        btnSubmit = findViewById(R.id.btnSubmit);

        String name = getIntent().getStringExtra(EXTRA_RESTAURANT_NAME);
        if (name != null && !name.isEmpty()) {
            tvTargetName.setText(name);
            tvTargetName.setVisibility(View.VISIBLE);
        }

        // 사진 선택기: 권한 없이 동작하는 시스템 문서 선택기 사용.
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
    }

    // ── 이미지 썸네일 렌더링 ──────────────────────────────

    private void renderImages() {
        containerImages.removeAllViews();
        int sizePx = dp(80);
        int marginPx = dp(8);
        for (int i = 0; i < selectedImages.size(); i++) {
            final Uri uri = selectedImages.get(i);
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMarginEnd(marginPx);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageURI(uri);
            // 탭하면 해당 사진 제거
            iv.setOnClickListener(v -> {
                selectedImages.remove(uri);
                renderImages();
            });
            containerImages.addView(iv);
        }
        btnAddImage.setEnabled(selectedImages.size() < MAX_IMAGES);
    }

    // ── 전송 ─────────────────────────────────────────────

    private void submit() {
        float ratingF = rbRating.getRating();
        if (ratingF < 0.5f) {
            Toast.makeText(this, "별점을 선택해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        String content = etContent.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "리뷰 내용을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        setSubmitting(true);

        // 정수 별점은 Integer(→"5"), 반칸 별점은 Double(→"4.5")로 전송.
        Number rating = (ratingF == Math.rint(ratingF))
                ? (Number) Integer.valueOf((int) ratingF)
                : (Number) Double.valueOf(ratingF);

        Long order = orderId > 0 ? orderId : null;
        ReviewCreateRequest req = new ReviewCreateRequest(restaurantId, order, rating, content);

        ApiClient.reviewApi(this).createReview(req).enqueue(new Callback<ReviewDto>() {
            @Override
            public void onResponse(Call<ReviewDto> call, Response<ReviewDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    long reviewId = response.body().getId();
                    if (selectedImages.isEmpty()) {
                        done();
                    } else {
                        uploadImages(reviewId);
                    }
                } else {
                    setSubmitting(false);
                    Toast.makeText(WriteReviewActivity.this,
                            errorMessage(response, "리뷰 등록에 실패했습니다."),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ReviewDto> call, Throwable t) {
                setSubmitting(false);
                Toast.makeText(WriteReviewActivity.this,
                        "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * 리뷰 생성 후 첨부 이미지를 순차 업로드한다.
     * 개별 이미지 실패는 리뷰 자체를 실패로 보지 않고 건너뛴다.
     */
    private void uploadImages(long reviewId) {
        uploadNext(reviewId, 0, 0);
    }

    private void uploadNext(long reviewId, int index, int failed) {
        if (index >= selectedImages.size()) {
            if (failed > 0) {
                Toast.makeText(this,
                        "사진 " + failed + "장은 업로드하지 못했습니다.", Toast.LENGTH_SHORT).show();
            }
            done();
            return;
        }

        MultipartBody.Part part;
        try {
            part = toPart(selectedImages.get(index));
        } catch (Exception e) {
            uploadNext(reviewId, index + 1, failed + 1);
            return;
        }

        ApiClient.reviewApi(this).uploadReviewImage(reviewId, part)
                .enqueue(new Callback<ReviewImageDto>() {
                    @Override
                    public void onResponse(Call<ReviewImageDto> call, Response<ReviewImageDto> response) {
                        uploadNext(reviewId, index + 1, response.isSuccessful() ? failed : failed + 1);
                    }

                    @Override
                    public void onFailure(Call<ReviewImageDto> call, Throwable t) {
                        uploadNext(reviewId, index + 1, failed + 1);
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
        String fileName = "review_" + System.currentTimeMillis() + guessExt(mime);
        return MultipartBody.Part.createFormData("image", fileName, body);
    }

    private String guessExt(String mime) {
        if (mime.contains("png")) return ".png";
        if (mime.contains("webp")) return ".webp";
        return ".jpg";
    }

    /** 에러 응답 바디의 message 필드를 사용자에게 그대로 노출한다(없으면 기본 문구). */
    private String errorMessage(Response<?> response, String fallback) {
        if (response.errorBody() == null) return fallback;
        try {
            String raw = response.errorBody().string();
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            if (json.has("message") && !json.get("message").isJsonNull()) {
                return json.get("message").getAsString();
            }
            if (json.has("detail") && !json.get("detail").isJsonNull()) {
                return json.get("detail").getAsString();
            }
        } catch (Exception ignored) {
            // 파싱 실패 시 기본 문구로 폴백
        }
        return fallback;
    }

    private void done() {
        Toast.makeText(this, "리뷰가 등록되었습니다.", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private void setSubmitting(boolean submitting) {
        btnSubmit.setEnabled(!submitting);
        btnSubmit.setText(submitting ? "등록 중…" : "리뷰 등록");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
