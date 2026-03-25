# rag-backend

이 프로젝트는 한국 구비문학대계 동화 데이터를 수집하고, 수집한 동화 본문을 청크/임베딩으로 적재한 뒤, 질문에 대해 RAG 방식으로 답변하는 Spring Boot 기반 백엔드입니다.

## 실행 전 확인

- 기본 서버 포트: `8080`
- 뷰 페이지 경로 설정: `src/main/resources/application.yml`
  - prefix: `/WEB-INF/views/`
  - suffix: `.jsp`
- 기본 Ollama 설정
  - 임베딩 모델: `nomic-embed-text:latest`
  - 답변 생성 모델: `qwen3:4b`
  - 임베딩 API: `http://127.0.0.1:11434/api/embeddings`
  - 생성 API: `http://127.0.0.1:11434/api/generate`
- 기본 RAG 설정
  - chunk size: `500`
  - chunk overlap: `50`
  - default topK: `10`

---

## 1. 동화 정보 적재

### 1-1. 특정 페이지 수집
- `GET /api/collect/nlcf/{pageNo}`

현재 컨트롤러는 조회 건수를 응답으로 반환하며, 전체 적재 로직은 서비스에서 사용됩니다.

응답 예시:
```json
{
  "success": true,
  "pageNo": 1,
  "savedCount": 100
}
```

### 1-2. 전체 페이지 재수집 및 DB 적재
- `GET /api/collect/nlcf/all`

1페이지부터 순차적으로 전체 데이터를 재수집합니다.
서비스 동작은 아래 순서로 진행됩니다.

1. 기존 수집 이력과 동화 데이터를 초기화
2. KCISA API를 1페이지부터 순차 호출
3. 페이지별 응답 이력 저장
4. 동화 데이터를 upsert 방식으로 저장
5. 더 이상 수집할 데이터가 없으면 종료

응답 예시:
```json
{
  "success": true,
  "savedCount": 1240
}
```

참고:
- 페이지 간 요청 사이에 약 `500ms` 대기합니다.(1초에 10번 이상 요청 시 차단당함.)
- 전체 수집 API를 먼저 수행해야 이후 임베딩/RAG 흐름에서 사용할 원천 데이터가 준비됩니다.
- 수집 대상 원천 테이블은 코드상 `tb_nlcf_story`를 기준으로 사용합니다.

---

## 2. 임베딩 및 RAG API

### 2-1. 전체 동화 임베딩 생성
- `POST /api/rag/embed/all-stories`

DB에 저장된 전체 동화(`tb_nlcf_story`)를 읽어서 텍스트 청크를 만들고, 각 청크에 대한 임베딩을 생성한 뒤 저장합니다.
실제 내부적으로는 인덱스 재생성 로직을 호출한 후 메시지만 별도로 내려줍니다.

처리 흐름:
1. 동화 전체 조회
2. 기존 청크/벡터 데이터 삭제
3. 동화별 청크 분리
4. 청크별 임베딩 생성
5. 청크/벡터 DB 저장

응답 예시:
```json
{
  "success": true,
  "message": "story DB 전체 조회 후 chunk 생성과 임베딩 저장을 완료했습니다.",
  "storyCount": 120,
  "chunkCount": 540,
  "vectorCount": 540
}
```

### 2-2. RAG 인덱스 재생성
- `POST /api/rag/index`

전체 동화를 다시 읽어서 청크와 벡터 인덱스를 처음부터 재생성합니다.
기존 청크/벡터 데이터는 삭제 후 다시 적재됩니다.

응답 예시:
```json
{
  "success": true,
  "message": "RAG 인덱스 재생성을 완료했습니다.",
  "storyCount": 120,
  "chunkCount": 540,
  "vectorCount": 540
}
```

### 2-3. 질문하기
- `POST /api/rag/ask`

질문을 임베딩한 뒤, 유사도가 높은 청크를 찾고, 해당 참조 청크를 기반으로 LLM 답변을 생성합니다.

요청 본문:
```json
{
  "question": "효녀 심청 이야기의 핵심 내용을 알려줘.",
  "topK": 5
}
```

요청 필드:
- `question`: 필수, 공백 불가
- `topK`: 선택, `1~20` 범위
  - 생략 시 `application.yml`의 `app.rag.top-k` 값을 사용

응답 필드:
- `success`: 성공 여부
- `question`: 사용자 질문
- `answer`: 핵심 답변 (`coreAnswer`와 동일)
- `rawAnswer`: LLM 원본 응답
- `thinkAnswer`: `</think>` 이전 사고 과정
- `coreAnswer`: 최종 핵심 답변
- `topK`: 실제 사용된 topK
- `matchedChunkCount`: 매칭된 청크 수
- `references`: 참조한 청크 목록

응답 예시:
```json
{
  "success": true,
  "question": "효녀 심청 이야기의 핵심 내용을 알려줘.",
  "answer": "심청은 아버지를 위해 자신을 희생하는 효의 상징적인 인물입니다.",
  "rawAnswer": "<think>...중간 추론...</think>심청은 아버지를 위해 자신을 희생하는 효의 상징적인 인물입니다.",
  "thinkAnswer": "...중간 추론...",
  "coreAnswer": "심청은 아버지를 위해 자신을 희생하는 효의 상징적인 인물입니다.",
  "topK": 5,
  "matchedChunkCount": 5,
  "references": [
    {
      "storyId": 10,
      "chunkId": 101,
      "chunkIndex": 0,
      "title": "효녀 심청",
      "chunkText": "title: 효녀 심청\nstoryTitle: 효녀 심청\ndescription: ...",
      "sourceUrl": "",
      "similarity": 0.9123
    }
  ]
}
```

참고:
- 검색 시 유사도는 코사인 유사도를 사용합니다.
- 참조 청크는 유사도 내림차순으로 상위 `topK`개를 반환합니다.
- 답변에 `</think>` 태그가 없으면 전체 응답이 핵심 답변으로 처리됩니다.

---

## 3. 테스트 페이지

### 3-1. RAG 검색 테스트 페이지
- `GET /rag/test`

브라우저에서 질문을 입력하고 AJAX로 `POST /api/rag/ask`를 호출할 수 있는 JSP 테스트 페이지입니다.
기본 `topK` 값은 `5`로 화면에 주입됩니다.

페이지에서 확인할 수 있는 항목:
- 질문 입력
- `topK` 입력 (`1~20`)
- 검색 요약
- 사고 과정 (`thinkAnswer`)
- 핵심 답변 (`coreAnswer`)
- 원본 응답 (`rawAnswer`)
- 참조 청크 목록 (`references`)

화면 특징:
- 사고 과정 / 핵심 답변 / 원본 응답 / 참조 청크를 접기/펼치기 가능
- 에러 응답과 로딩 상태를 별도 패널로 표시
- 참조 청크의 `sourceUrl`이 있으면 링크로 표시

---

## 기타 엔드포인트

### 헬스 체크
- `GET /health`

서버 기동 여부를 간단히 확인할 때 사용할 수 있습니다.

---

## 권장 사용 순서

1. `GET /api/collect/nlcf/all` 로 동화 데이터 적재
2. `POST /api/rag/embed/all-stories` 또는 `POST /api/rag/index` 로 청크/임베딩 생성
3. `GET /rag/test` 페이지 또는 `POST /api/rag/ask` API로 질문 테스트

---

## 빠른 실행 예시

```bat
ollama pull qwen3:4b
ollama pull nomic-embed-text:latest
C:\Users\LANDSOFT\git\rag_test\gradlew.bat bootRun
```

애플리케이션 실행 후 접속 예시:
- 테스트 페이지: `http://localhost:8080/rag/test`
- 헬스 체크: `http://localhost:8080/health`