package com.hackmin.app.ui.mypage;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.review.ReviewDto;
import com.hackmin.app.network.ApiClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 작성한 리뷰 목록/삭제 화면.
 * - GET    /me/reviews
 * - DELETE /reviews/{id}
 */
public class MyReviewsActivity extends com.hackmin.app.ui.common.BaseActivity {

    private RecyclerView rvReviews;
    private TextView tvEmpty;
    private ReviewAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_reviews);
        com.hackmin.app.ui.common.BottomNav.setup(this, com.hackmin.app.ui.common.BottomNav.Tab.NONE);

        ImageButton btnBack = findViewById(R.id.btnBack);
        rvReviews = findViewById(R.id.rvReviews);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new ReviewAdapter(this::confirmDelete);
        rvReviews.setLayoutManager(new LinearLayoutManager(this));
        rvReviews.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());

        loadReviews();
    }

    private void loadReviews() {
        ApiClient.reviewApi(this).getMyReviews(null)
                .enqueue(new Callback<PagedResponse<ReviewDto>>() {
                    @Override
                    public void onResponse(Call<PagedResponse<ReviewDto>> call,
                                           Response<PagedResponse<ReviewDto>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<ReviewDto> results = response.body().getResults();
                            adapter.submit(results);
                            tvEmpty.setVisibility(
                                    results == null || results.isEmpty() ? View.VISIBLE : View.GONE);
                        } else {
                            Toast.makeText(MyReviewsActivity.this,
                                    "리뷰를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PagedResponse<ReviewDto>> call, Throwable t) {
                        Toast.makeText(MyReviewsActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void confirmDelete(ReviewDto review) {
        new AlertDialog.Builder(this)
                .setTitle("리뷰 삭제")
                .setMessage("이 리뷰를 삭제할까요?")
                .setPositiveButton("삭제", (d, w) -> deleteReview(review))
                .setNegativeButton("취소", null)
                .show();
    }

    private void deleteReview(ReviewDto review) {
        ApiClient.reviewApi(this).deleteReview(review.getId())
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(MyReviewsActivity.this,
                                    "삭제되었습니다.", Toast.LENGTH_SHORT).show();
                            loadReviews();
                        } else {
                            Toast.makeText(MyReviewsActivity.this,
                                    "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(MyReviewsActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }
}
