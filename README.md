# rag-backend

`rag_test`는 한국 구비문학대계 동화 데이터를 수집하고, 수집한 본문을 청크/임베딩으로 적재한 뒤, 질문에 대해 RAG 방식으로 답변하는 Spring Boot 백엔드입니다.

## 프로젝트 개요

현재 프로젝트는 아래 기능을 제공합니다.

- KCISA/NLCF 원천 데이터 수집 및 DB 적재
- 동화 본문 청크 생성 및 임베딩 저장
- 질문 기반 RAG 검색/답변 생성
- 대표 참조 스토리 상세 조회
- JSP 기반 테스트 페이지 제공

기본 실행 정보
- 서버 포트: `8080`
- Java: `17`
- 빌드 도구: `Gradle Wrapper`
- View Resolver
  - prefix: `/WEB-INF/views/`
  - suffix: `.jsp`

---

## 기술 스택

- Spring Boot `3.5.12`
- Spring Web / Validation / JDBC
- MyBatis
- PostgreSQL
- JSP / JSTL
- Ollama API (임베딩, 답변 생성)

---

## 디렉터리 힌트

주요 구현 위치는 아래와 같습니다.

- 애플리케이션 시작점: `src/main/java/com/example/rag/RagBackendApplication.java`
- 수집 API: `src/main/java/com/example/rag/collect/controller/NlcfCollectController.java`
- RAG API: `src/main/java/com/example/rag/rag/controller/RagController.java`
- 스토리 상세 API: `src/main/java/com/example/rag/story/controller/StoryController.java`
- 테스트 페이지 컨트롤러: `src/main/java/com/example/rag/rag/controller/RagViewController.java`
- 공통 예외 처리: `src/main/java/com/example/rag/common/controller/ApiExceptionHandler.java`
- 설정 파일: `src/main/resources/application.yml`

---

## 실행 전 설정

`src/main/resources/application.yml` 기준 기본값은 아래와 같습니다.

### 1) 데이터베이스

- JDBC URL: `jdbc:postgresql://localhost:15432/appdb`
- Username: `appTest`
- Driver: `org.postgresql.Driver`

애플리케이션은 PostgreSQL 연결이 가능해야 정상 기동됩니다.

### 2) KCISA 수집 설정

- Base URL: `https://api.kcisa.kr`
- Endpoint: `/openapi/service/rest/meta14/getNLCF031801`
- Page size: `100`
- Response type: `json`

### 3) RAG 설정

- chunk size: `500`
- chunk overlap: `50`
- default topK: `10`

### 4) Ollama 설정

- 임베딩 모델: `nomic-embed-text:latest`
- 생성 모델: `qwen3:4b`
- 임베딩 API: `http://127.0.0.1:11434/api/embeddings`
- 생성 API: `http://127.0.0.1:11434/api/generate`

Ollama 서버가 떠 있지 않으면 임베딩 생성 및 질문 응답 API는 실패합니다.

---

## 실행 방법

### 1) 애플리케이션 실행

Windows 기준:

```bat
C:\Users\LANDSOFT\git\rag_test\gradlew.bat bootRun
```

### 2) 테스트 실행

```bat
C:\Users\LANDSOFT\git\rag_test\gradlew.bat test
```

### 3) 빌드 실행

```bat
C:\Users\LANDSOFT\git\rag_test\gradlew.bat build
```

### 4) Ollama 모델 준비 예시

```bat
ollama pull qwen3:4b
ollama pull nomic-embed-text:latest
```

---

## API 요약

기본 Base URL은 `http://localhost:8080`입니다.

### 헬스 체크
- `GET /health`

문자열 `OK`를 반환합니다.

### 수집 API
- `GET /api/collect/nlcf/{pageNo}`
  - 지정 페이지 데이터 조회/적재 건수를 반환합니다.
- `GET /api/collect/nlcf/all`
  - 전체 페이지를 순차 수집하고 전체 적재 건수를 반환합니다.

### RAG API
- `POST /api/rag/embed/all-stories`
  - 전체 스토리 기준 청크/임베딩을 생성합니다.
- `POST /api/rag/index`
  - 청크/벡터 인덱스를 재생성합니다.
- `POST /api/rag/ask`
  - 질문에 대한 답변과 참조 청크 목록을 반환합니다.

### 스토리 API
- `GET /api/stories/{storyId}`
  - 특정 스토리 상세 정보를 반환합니다.

### 테스트 페이지
- `GET /rag/test`
  - JSP 기반 RAG 테스트 화면입니다.
  - 컨트롤러에서 화면 기본 `topK=5`를 주입합니다.

상세 요청/응답 예시는 `api-spec.md`를 참고하세요.

---

## 주요 응답 형태

### 1) 질문 API 요청

`POST /api/rag/ask`

```json
{
  "question": "효녀 심청 이야기의 핵심 내용을 알려줘.",
  "topK": 5
}
```

- `question`: 필수, 공백 불가
- `topK`: 선택, `1~20`
  - 생략 시 `application.yml`의 `app.rag.top-k` 사용

### 2) 질문 API 응답 예시

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
  ],
  "relatedStoryIds": [10],
  "usedStoryIds": [10],
  "primaryStoryId": 10,
  "primaryReference": {
    "storyId": 10,
    "chunkId": 101,
    "chunkIndex": 0,
    "title": "효녀 심청",
    "chunkText": "title: 효녀 심청\nstoryTitle: 효녀 심청\ndescription: ...",
    "sourceUrl": "",
    "similarity": 0.9123
  }
}
```

응답 필드 메모
- `answer`: 현재 `coreAnswer`와 동일
- `relatedStoryIds`: 검색 참조 청크 기준 스토리 ID 목록
- `usedStoryIds`: 생성 답변 안의 `[[USED_STORY_IDS:...]]` 메타데이터에서 파싱한 스토리 ID 목록
- `primaryStoryId`: 첫 번째 대표 참조 스토리 ID
- `primaryReference`: 첫 번째 대표 참조 청크

---

## 에러 응답

전역 예외 처리기 기준 공통 에러 응답은 아래 형태입니다.

```json
{
  "success": false,
  "message": "에러 메시지"
}
```

현재 코드상 문서화 가능한 대표 케이스
- 존재하지 않는 스토리 조회: `404 Not Found`
- 잘못된 path variable / validation 오류: `400 Bad Request`

예시:

```json
{
  "success": false,
  "message": "스토리를 찾을 수 없습니다. storyId=999"
}
```

```json
{
  "success": false,
  "message": "요청 값이 올바르지 않습니다."
}
```

---

## 권장 사용 순서

1. `GET /api/collect/nlcf/all`로 원천 동화 데이터 적재
2. `POST /api/rag/embed/all-stories` 또는 `POST /api/rag/index`로 인덱스 생성
3. `GET /rag/test` 또는 `POST /api/rag/ask`로 질문 테스트
4. 필요 시 `GET /api/stories/{storyId}`로 상세 조회

---

## 빠른 확인 URL

- 헬스 체크: `http://localhost:8080/health`
- 테스트 페이지: `http://localhost:8080/rag/test`
- API 문서: `api-spec.md`