package com.hackmin.app.ui.mypage;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.inquiry.InquiryDto;
import com.hackmin.app.network.ApiClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 1:1 문의 목록 화면.
 * - GET /inquiries (본인 문의, 작성일 오름차순)
 */
public class InquiryListActivity extends AppCompatActivity {

    private RecyclerView rvInquiries;
    private ProgressBar pbLoading;
    private TextView tvEmpty;
    private InquiryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inquiry_list);

        ImageButton btnBack = findViewById(R.id.btnInquiryListBack);
        Button btnWrite = findViewById(R.id.btnInquiryWrite);
        rvInquiries = findViewById(R.id.rvInquiries);
        pbLoading = findViewById(R.id.pbInquiryListLoading);
        tvEmpty = findViewById(R.id.tvInquiryListEmpty);

        adapter = new InquiryAdapter(item -> {
            Intent i = new Intent(this, InquiryDetailActivity.class);
            i.putExtra(InquiryDetailActivity.EXTRA_INQUIRY_ID, item.getId());
            startActivity(i);
        });
        rvInquiries.setLayoutManager(new LinearLayoutManager(this));
        rvInquiries.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rvInquiries.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnWrite.setOnClickListener(v ->
                startActivity(new Intent(this, InquiryWriteActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 문의 작성 후 돌아왔을 때 새 글이 바로 보이도록 매번 새로고침한다.
        loadInquiries();
    }

    private void loadInquiries() {
        pbLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        ApiClient.inquiryApi(this).getInquiries().enqueue(new Callback<PagedResponse<InquiryDto>>() {
            @Override
            public void onResponse(Call<PagedResponse<InquiryDto>> call,
                                    Response<PagedResponse<InquiryDto>> response) {
                pbLoading.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    List<InquiryDto> results = response.body().getResults();
                    adapter.submit(results);
                    tvEmpty.setVisibility(
                            results == null || results.isEmpty() ? View.VISIBLE : View.GONE);
                } else {
                    Toast.makeText(InquiryListActivity.this,
                            "문의 목록을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<PagedResponse<InquiryDto>> call, Throwable t) {
                pbLoading.setVisibility(View.GONE);
                Toast.makeText(InquiryListActivity.this,
                        "네트워크 연결 실패 (백엔드 서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }
}
