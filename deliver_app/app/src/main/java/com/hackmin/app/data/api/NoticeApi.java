package com.hackmin.app.data.api;

import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.notice.NoticeDto;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * 공지사항 API (/api/v1/notices).
 * 로그인 필요(IsAuthenticated). 목록은 {"results":[...]} 형태로 온다.
 */
public interface NoticeApi {

    /** 공지 목록 조회 */
    @GET("notices")
    Call<PagedResponse<NoticeDto>> getNotices();

    /** 공지 상세 조회 */
    @GET("notices/{id}")
    Call<NoticeDto> getNotice(@Path("id") long id);
}
