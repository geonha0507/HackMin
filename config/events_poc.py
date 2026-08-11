"""
이벤트/프로모션 상세 페이지 — 앱의 EventWebActivity 가 로드하는 서버 렌더 웹 페이지.

앱 홈의 이벤트 배너에서 진입하며 '베스트 리뷰 투표' 이벤트를 보여준다.

⚠️ 보안 랩(의도된 취약점): 개인화를 위해 HackminBridge.getAuthToken() 으로 세션 토큰을
   받아 리뷰 API 를 호출하고, 응답의 리뷰 본문(content)·작성자(author)를 innerHTML 로
   그대로 렌더한다(WEB-007). → 리뷰 content 에 심긴 <img onerror> 페이로드가 이벤트를
   여는 사용자의 WebView 에서 발화해 세션 토큰이 탈취될 수 있다(모의해킹 대상 finding).
"""
from django.http import HttpResponse

_EVENT_HTML = r"""<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>베스트 리뷰 투표 이벤트</title>
  <style>
    body { font-family: sans-serif; margin: 16px; }
    .card { border: 1px solid #ddd; border-radius: 8px; padding: 12px; margin: 10px 0; }
    .author { font-weight: bold; }
  </style>
</head>
<body>
  <h2>🏆 베스트 리뷰 투표 이벤트</h2>
  <p>마음에 드는 리뷰에 투표하고 쿠폰을 받아가세요!</p>
  <div id="list">리뷰 불러오는 중…</div>

  <script>
  (function () {
    // 개인화: 앱 세션 토큰을 브리지로 받아 로그인 상태로 리뷰 API 호출
    var token = "";
    try { token = window.HackminBridge ? HackminBridge.getAuthToken() : ""; } catch (e) {}

    var rid = new URLSearchParams(location.search).get('r') || '1';
    fetch('/api/v1/restaurants/' + rid + '/reviews', {
      headers: token ? { 'Authorization': 'Bearer ' + token } : {}
    })
    .then(function (res) { return res.json(); })
    .then(function (data) {
      var reviews = data.results || data || [];
      var box = document.getElementById('list');
      box.innerHTML = '';
      if (!reviews.length) { box.textContent = '아직 리뷰가 없습니다.'; return; }
      reviews.forEach(function (rv) {
        var card = document.createElement('div');
        card.className = 'card';
        // ⚠️ 취약점(WEB-007): 리뷰 작성자·본문을 원문 그대로 innerHTML 로 렌더.
        //    content 에 심긴 <img onerror> 페이로드가 여기서 파싱·발화된다.
        card.innerHTML =
          '<span class="author">' + rv.author + '</span> ' +
          '★' + rv.rating + '<br>' +
          rv.content +
          '<br><button>이 리뷰에 투표</button>';
        box.appendChild(card);
      });
    })
    .catch(function (e) {
      document.getElementById('list').textContent = '오류: ' + e;
    });
  })();
  </script>
</body>
</html>
"""


def events_page(_request):
    """/events/ — 취약 이벤트 페이지(PoC)."""
    return HttpResponse(_EVENT_HTML)
