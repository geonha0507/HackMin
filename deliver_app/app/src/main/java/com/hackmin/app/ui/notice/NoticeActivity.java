package com.hackmin.app.ui.notice;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.notice.NoticeDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.ui.common.BottomNav;
import com.hackmin.app.util.ImageLoader;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 공지사항 목록 화면.
 * - GET /notices : 목록
 * - 항목 클릭 → GET /notices/{id} 상세를 조회해 다이얼로그로 표시
 */
public class NoticeActivity extends AppCompatActivity {

    private RecyclerView rvNotices;
    private ProgressBar pbLoading;
    private TextView tvEmpty;
    private NoticeAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice);

        rvNotices = findViewById(R.id.rv_notices);
        pbLoading = findViewById(R.id.pb_notice_loading);
        tvEmpty = findViewById(R.id.tv_notice_empty);

        findViewById(R.id.btn_notice_back).setOnClickListener(v -> finish());
        BottomNav.setup(this, BottomNav.Tab.NONE);

        adapter = new NoticeAdapter(this::showDetail);
        rvNotices.setLayoutManager(new LinearLayoutManager(this));
        rvNotices.setAdapter(adapter);

        loadNotices();
    }

    private void loadNotices() {
        showLoading(true);
        ApiClient.noticeApi(this).getNotices().enqueue(new Callback<PagedResponse<NoticeDto>>() {
            @Override
            public void onResponse(Call<PagedResponse<NoticeDto>> call,
                                   Response<PagedResponse<NoticeDto>> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResults() != null) {
                    adapter.submit(response.body().getResults());
                    tvEmpty.setVisibility(response.body().getResults().isEmpty()
                            ? View.VISIBLE : View.GONE);
                } else {
                    tvEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(NoticeActivity.this,
                            "공지사항을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<PagedResponse<NoticeDto>> call, Throwable t) {
                showLoading(false);
                tvEmpty.setVisibility(View.VISIBLE);
                Toast.makeText(NoticeActivity.this,
                        "네트워크 연결 실패 (백엔드 서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }

    /** 항목 클릭 → 상세 조회 후 다이얼로그로 표시. */
    private void showDetail(NoticeDto fromList) {
        ApiClient.noticeApi(this).getNotice(fromList.getId()).enqueue(new Callback<NoticeDto>() {
            @Override
            public void onResponse(Call<NoticeDto> call, Response<NoticeDto> response) {
                NoticeDto detail = response.isSuccessful() && response.body() != null
                        ? response.body() : fromList; // 상세 실패 시 목록 데이터로 폴백
                showNoticeDialog(detail);
            }

            @Override
            public void onFailure(Call<NoticeDto> call, Throwable t) {
                showNoticeDialog(fromList); // 네트워크 오류 시에도 목록 데이터로 표시
            }
        });
    }

    private void showNoticeDialog(NoticeDto notice) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_notice_detail, null);
        ImageView ivImage = view.findViewById(R.id.iv_notice_detail_image);
        TextView tvContent = view.findViewById(R.id.tv_notice_detail_content);

        tvContent.setText(notice.getContent());

        String image = notice.getImage();
        if (image == null || image.trim().isEmpty()) {
            ivImage.setVisibility(View.GONE);
        } else {
            ivImage.setVisibility(View.VISIBLE);
            ImageLoader.load(ivImage, image);
        }

        new AlertDialog.Builder(this)
                .setTitle(notice.getTitle())
                .setView(view)
                .setPositiveButton("닫기", null)
                .show();
    }

    private void showLoading(boolean loading) {
        pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            tvEmpty.setVisibility(View.GONE);
        }
    }
}
