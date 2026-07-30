package com.hackmin.app.ui.mypage;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hackmin.app.R;
import com.hackmin.app.data.model.inquiry.InquiryDto;
import com.hackmin.app.data.model.inquiry.InquiryImageDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.util.ImageLoader;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 1:1 문의 상세 화면.
 * - GET    /inquiries/{id}
 * - DELETE /inquiries/{id}
 */
public class InquiryDetailActivity extends AppCompatActivity {

    public static final String EXTRA_INQUIRY_ID = "inquiry_id";

    private long inquiryId;

    private TextView tvCategory, tvTitle, tvMeta, tvContent;
    private HorizontalScrollView hsvImages;
    private LinearLayout containerImages;
    private Button btnEdit;
    private LinearLayout containerAnswer;
    private TextView tvAnswerContent, tvAnswerMeta, tvNoAnswer;

    private InquiryDto current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inquiry_detail);

        inquiryId = getIntent().getLongExtra(EXTRA_INQUIRY_ID, -1L);
        if (inquiryId < 0) {
            Toast.makeText(this, "잘못된 접근입니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btnInquiryDetailBack);
        tvCategory = findViewById(R.id.tvDetailCategory);
        tvTitle = findViewById(R.id.tvDetailTitle);
        tvMeta = findViewById(R.id.tvDetailMeta);
        tvContent = findViewById(R.id.tvDetailContent);
        hsvImages = findViewById(R.id.hsvDetailImages);
        containerImages = findViewById(R.id.containerDetailImages);
        containerAnswer = findViewById(R.id.containerAnswer);
        tvAnswerContent = findViewById(R.id.tvAnswerContent);
        tvAnswerMeta = findViewById(R.id.tvAnswerMeta);
        tvNoAnswer = findViewById(R.id.tvNoAnswer);
        Button btnList = findViewById(R.id.btnDetailList);
        btnEdit = findViewById(R.id.btnDetailEdit);
        Button btnDelete = findViewById(R.id.btnDetailDelete);

        btnBack.setOnClickListener(v -> finish());
        btnList.setOnClickListener(v -> finish());
        btnDelete.setOnClickListener(v -> confirmDelete());
        btnEdit.setOnClickListener(v -> {
            if (current != null) {
                startActivity(InquiryWriteActivity.editIntent(this, current));
            }
        });

        btnEdit.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 수정 화면에서 돌아왔을 때 최신 내용을 다시 반영한다.
        loadInquiry();
    }

    private void loadInquiry() {
        ApiClient.inquiryApi(this).getInquiry(inquiryId).enqueue(new Callback<InquiryDto>() {
            @Override
            public void onResponse(Call<InquiryDto> call, Response<InquiryDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    bind(response.body());
                } else {
                    Toast.makeText(InquiryDetailActivity.this,
                            "문의를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onFailure(Call<InquiryDto> call, Throwable t) {
                Toast.makeText(InquiryDetailActivity.this,
                        "네트워크 연결 실패 (백엔드 서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bind(InquiryDto item) {
        current = item;
        btnEdit.setEnabled(true);

        String category = item.getCategoryDisplay();
        tvCategory.setText(category == null || category.isEmpty() ? "-" : category);
        tvTitle.setText(item.getTitle());
        tvMeta.setText(item.getAuthor() + " · " + formatDate(item.getCreatedAt()));
        tvContent.setText(item.getContent());

        // 관리자 답변 표시 (답변 있으면 답변 영역, 없으면 미답변 안내)
        boolean answered = item.isAnswered()
                && item.getAnswer() != null && !item.getAnswer().trim().isEmpty();
        if (answered) {
            containerAnswer.setVisibility(View.VISIBLE);
            tvNoAnswer.setVisibility(View.GONE);
            tvAnswerContent.setText(item.getAnswer());
            StringBuilder meta = new StringBuilder();
            String by = item.getAnsweredByName();
            String at = formatDate(item.getAnsweredAt());
            if (by != null && !by.isEmpty()) meta.append(by);
            if (at != null && !at.isEmpty()) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append(at);
            }
            tvAnswerMeta.setText(meta.toString());
            tvAnswerMeta.setVisibility(meta.length() > 0 ? View.VISIBLE : View.GONE);
        } else {
            containerAnswer.setVisibility(View.GONE);
            tvNoAnswer.setVisibility(View.VISIBLE);
        }

        List<InquiryImageDto> images = item.getImages();
        containerImages.removeAllViews();
        if (images == null || images.isEmpty()) {
            hsvImages.setVisibility(View.GONE);
        } else {
            hsvImages.setVisibility(View.VISIBLE);
            int sizePx = dp(100);
            int marginPx = dp(8);
            for (InquiryImageDto image : images) {
                ImageView iv = new ImageView(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
                lp.setMarginEnd(marginPx);
                iv.setLayoutParams(lp);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ImageLoader.load(iv, image.getImage());
                containerImages.addView(iv);
            }
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("문의 삭제")
                .setMessage("이 문의를 삭제할까요?")
                .setPositiveButton("삭제", (d, w) -> deleteInquiry())
                .setNegativeButton("취소", null)
                .show();
    }

    private void deleteInquiry() {
        ApiClient.inquiryApi(this).deleteInquiry(inquiryId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(InquiryDetailActivity.this,
                            "문의가 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(InquiryDetailActivity.this,
                            "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(InquiryDetailActivity.this,
                        "네트워크 연결 실패 (백엔드 서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }

    /** "2026-07-23T04:12:00Z" 형식의 ISO 날짜를 "2026.07.23" 형식으로 변환한다. */
    private String formatDate(String iso) {
        if (iso == null || iso.length() < 10) {
            return "-";
        }
        return iso.substring(0, 10).replace("-", ".");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
