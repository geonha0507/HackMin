package com.hackmin.connect.ui.notice;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.connect.R;
import com.hackmin.connect.ui.common.BaseActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 해킹커넥트 공지사항. 서버 없이 앱에 내장한 라이더 대상 공지를 목록·상세로 보여준다.
 * (해킹의 민족 공지 형식 참고. 항목 탭 → 전체 내용 다이얼로그)
 */
public class NoticeActivity extends BaseActivity {

    /** 공지 한 건. */
    static final class Notice {
        final String title, date, content;
        final boolean pinned;
        Notice(String title, String date, String content, boolean pinned) {
            this.title = title; this.date = date; this.content = content; this.pinned = pinned;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rv_notices);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new NoticeAdapter(notices(), this::showDetail));
    }

    private void showDetail(Notice n) {
        new AlertDialog.Builder(this)
                .setTitle(n.title)
                .setMessage(n.date + "\n\n" + n.content)
                .setPositiveButton("확인", null)
                .show();
    }

    /** 앱 내장 공지 목록(최신순). 고정(중요) 공지가 위로 온다. */
    private static List<Notice> notices() {
        List<Notice> list = new ArrayList<>();
        list.add(new Notice(
                "[중요] 개인정보보호 재안내",
                "2026.08.20",
                "배달 과정에서 알게 된 고객·가게의 이름, 연락처, 주소, 위치정보 등 모든 개인정보는 "
                + "배달 목적 외로 이용하거나 SNS·커뮤니티·메신저 등에 공유하면 안 됩니다.\n\n"
                + "개인정보를 유출하면 악의가 없더라도 5년 이하의 징역 또는 5천만원 이하의 벌금에 처할 수 "
                + "있어요. 라이더님과 고객 모두를 지키기 위해 꼭 지켜주세요.",
                true));
        list.add(new Notice(
                "[중요] 정산 안내 — 매주 수요일 지급",
                "2026.08.18",
                "배달 수입 정산은 매주 수요일에 등록하신 정산 계좌로 지급됩니다.\n\n"
                + "· 정산 대상: 지난주 월요일 00:00 ~ 일요일 24:00 완료 배달\n"
                + "· 계좌·예금주 정보가 정확하지 않으면 정산이 지연될 수 있어요. [내정보 > 배달 정보 등록]에서 "
                + "미리 확인해 주세요.",
                true));
        list.add(new Notice(
                "폭염·우천 시 안전 배달 가이드",
                "2026.08.15",
                "무더위와 비 오는 날에는 무리한 운행을 자제해 주세요.\n\n"
                + "· 미끄러운 노면에서는 감속 운행\n"
                + "· 헬멧과 우비 등 안전 장비 착용\n"
                + "· 30분마다 수분 섭취와 휴식\n"
                + "· 기상 특보 시에는 배차를 잠시 멈추고 안전한 곳에서 대기해 주세요.",
                false));
        list.add(new Notice(
                "배달 수수료 정책 안내",
                "2026.08.10",
                "기본 배달료는 건당 3,500원이며, 우천·심야·성수기에는 프로모션 할증이 추가될 수 있습니다.\n\n"
                + "적용되는 할증은 콜 카드와 배달 상세 화면에서 미리 확인할 수 있어요.",
                false));
        list.add(new Notice(
                "앱 업데이트 안내 (v1.1)",
                "2026.08.05",
                "해킹커넥트가 더 편해졌어요!\n\n"
                + "· 실시간 지도에서 내 위치 확인\n"
                + "· 배달 정보(정산 계좌·면허·차량·희망지역·배달수단) 등록 기능 추가\n"
                + "· 홈에서 해킹의 민족 인기 메뉴 바로 보기\n\n"
                + "최신 버전으로 업데이트해 이용해 주세요.",
                false));
        list.add(new Notice(
                "신규 라이더 웰컴 프로모션",
                "2026.08.01",
                "해킹커넥트에 처음 오신 라이더님을 환영합니다!\n\n"
                + "가입 후 첫 2주 동안 완료한 배달 10건마다 추가 보너스가 지급됩니다. "
                + "자세한 조건은 프로모션 페이지에서 확인해 주세요.",
                false));
        return list;
    }

    // ── 어댑터 ──────────────────────────────────────────────

    interface OnClick { void onClick(Notice n); }

    static final class NoticeAdapter extends RecyclerView.Adapter<NoticeAdapter.Holder> {
        private final List<Notice> items;
        private final OnClick listener;

        NoticeAdapter(List<Notice> items, OnClick listener) {
            this.items = items; this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notice, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Notice n = items.get(position);
            h.title.setText(n.title);
            h.date.setText(n.date);
            h.preview.setText(n.content);
            h.pinned.setVisibility(n.pinned ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> listener.onClick(n));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView title, date, preview, pinned;
            Holder(@NonNull View v) {
                super(v);
                title = v.findViewById(R.id.tv_notice_title);
                date = v.findViewById(R.id.tv_notice_date);
                preview = v.findViewById(R.id.tv_notice_preview);
                pinned = v.findViewById(R.id.tv_notice_pinned);
            }
        }
    }
}
