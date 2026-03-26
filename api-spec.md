# Frontend API 명세서

이 문서는 `rag_test` 프로젝트의 프론트엔드 개발을 위해 현재 백엔드에서 제공하는 API와 테스트 페이지 연동 방식을 정리한 문서입니다.

기준 환경
- Base URL: `http://localhost:8080`
- Content-Type: `application/json` (JSON 요청 API 기준)
- 문자셋: UTF-8

---

## 1. 전체 흐름 요약

프론트에서 이 프로젝트를 사용할 때 일반적인 흐름은 아래와 같습니다.

1. 동화 원천 데이터 적재
   - `GET /api/collect/nlcf/all`
2. 청크/임베딩 생성
   - `POST /api/rag/embed/all-stories`
   - 또는 `POST /api/rag/index`
3. 질문/답변 기능 사용
   - `POST /api/rag/ask`
4. 대표/참조 스토리 상세 조회
   - `GET /api/stories/{storyId}`
5. 테스트 화면 확인
   - `GET /rag/test`

---

## 2. 공통 응답 특성

### 2-1. 성공 응답
대부분 JSON API는 아래처럼 `success` 필드를 포함합니다.

```json
{
  "success": true
}
```

단, 일부 조회 API는 도메인 객체 자체를 바로 반환합니다.
예: `GET /api/stories/{storyId}`는 `StoryDetailResponse`를 직접 반환합니다.

### 2-2. 에러 응답
현재 코드에는 전역 예외 처리기 `ApiExceptionHandler`가 적용되어 있습니다.
검증 오류와 명시적 상태 예외는 아래 JSON 형식으로 내려옵니다.

#### 공통 에러 응답 형태
```json
{
  "success": false,
  "message": "에러 메시지"
}
```

#### 현재 문서 기준 보장되는 케이스
- `ResponseStatusException`
  - 예: 존재하지 않는 `storyId` 조회 시 `404 Not Found`
- `ConstraintViolationException`
- `HandlerMethodValidationException`
  - 예: `@Positive` 위반, 잘못된 path variable

#### 예시 1. 존재하지 않는 스토리 조회
```json
{
  "success": false,
  "message": "스토리를 찾을 수 없습니다. storyId=999"
}
```

#### 예시 2. 잘못된 path variable / validation
```json
{
  "success": false,
  "message": "요청 값이 올바르지 않습니다."
}
```

권장 프론트 처리 순서:
1. `response.ok` 확인
2. JSON 파싱 시도
3. `data.message` 우선 사용
4. 없으면 기본 에러 문구 사용

---

## 3. 타입 정의

프론트에서 바로 옮겨 쓸 수 있도록 TypeScript 기준 예시 타입을 제공합니다.

```ts
export interface ErrorResponse {
  success: false;
  message: string;
}

export interface RagIndexResponse {
  success: boolean;
  message: string;
  storyCount: number;
  chunkCount: number;
  vectorCount: number;
}

export interface RagAskRequest {
  question: string;
  topK?: number; // 1~20
}

export interface RagChunkResult {
  storyId: number | null;
  chunkId: number | null;
  chunkIndex: number;
  similarity: number;
  title: string;
  chunkText: string;
  sourceUrl: string;
}

export interface RagAskResponse {
  success: boolean;
  question: string;
  answer: string;
  rawAnswer: string;
  thinkAnswer: string;
  coreAnswer: string;
  topK: number;
  matchedChunkCount: number;
  references: RagChunkResult[];
  relatedStoryIds: number[];
  primaryStoryId: number | null;
  primaryReference: RagChunkResult | null;
}

export interface StoryDetailResponse {
  storyId: number | null;
  collectId: number | null;
  title: string;
  alternativeTitle: string | null;
  creator: string | null;
  regDateRaw: string | null;
  collectionDb: string | null;
  subjectCategory: string | null;
  subjectKeyword: string | null;
  extent: string | null;
  description: string | null;
  spatialCoverage: string | null;
  temporalInfo: string | null;
  personInfo: string | null;
  language: string | null;
  sourceTitle: string | null;
  referenceIdentifier: string | null;
  rightsInfo: string | null;
  copyrightOthers: string | null;
  contentUrl: string | null;
  contributor: string | null;
  hashKey: string | null;
  useYn: string | null;
  regDt: string | null;
  updDt: string | null;
}

export interface CollectPageResponse {
  success: boolean;
  pageNo: number;
  savedCount: number;
}

export interface CollectAllResponse {
  success: boolean;
  savedCount: number;
}
```

---

## 4. API 상세

## 4-1. 헬스 체크

### `GET /health`

서버가 떠 있는지 확인하는 가장 단순한 API입니다.

#### Request
- Body 없음

#### Response
- Content-Type: `text/plain` 또는 문자열 응답

예시:
```text
OK
```

#### 프론트 사용 목적
- 서버 연결 확인
- 초기 상태 체크
- 개발 환경 헬스 체크

---

## 4-2. 특정 페이지 동화 수집 건수 확인

### `GET /api/collect/nlcf/{pageNo}`

지정한 페이지 번호에 대해 현재 수집 가능한 건수를 조회합니다.
현재 컨트롤러는 저장 결과보다는 조회 건수를 반환하는 용도로 볼 수 있습니다.

#### Path Parameter
| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `pageNo` | number | Y | 조회할 페이지 번호 |

#### Response JSON
```json
{
  "success": true,
  "pageNo": 1,
  "savedCount": 100
}
```

#### 필드 설명
| 필드 | 타입 | 설명 |
|---|---|---|
| `success` | boolean | 성공 여부 |
| `pageNo` | number | 요청한 페이지 번호 |
| `savedCount` | number | 해당 페이지에서 조회된 건수 |

#### 프론트 사용 포인트
- 관리자 페이지에서 특정 페이지 데이터 확인
- 수집 테스트 버튼 구현

---

## 4-3. 전체 동화 데이터 적재

### `GET /api/collect/nlcf/all`

1페이지부터 시작해 동화 데이터를 순차 수집하고 DB에 적재합니다.
기존 데이터 삭제 후 다시 적재하는 흐름입니다.

#### Request
- Body 없음

#### Response JSON
```json
{
  "success": true,
  "savedCount": 1240
}
```

#### 필드 설명
| 필드 | 타입 | 설명 |
|---|---|---|
| `success` | boolean | 성공 여부 |
| `savedCount` | number | 전체 적재 건수 |

#### 주의사항
- 실행 시간이 길 수 있습니다.
- 버튼 연타 방지를 위해 프론트에서 로딩 상태를 두는 것이 좋습니다.
- RAG 기능을 쓰기 전에 먼저 수행하는 것이 안전합니다.

#### 프론트 사용 포인트
- 관리자 전용 “동화 데이터 적재” 버튼
- 적재 완료 후 토스트/알림 표시

---

## 4-4. 전체 동화 임베딩 생성

### `POST /api/rag/embed/all-stories`

DB에 저장된 동화를 읽어서 청크를 만들고 임베딩을 생성합니다.
현재 구현은 내부적으로 인덱스 재생성 로직을 호출합니다.

#### Request
- Body 없음

#### Response JSON
```json
{
  "success": true,
  "message": "story DB 전체 조회 후 chunk 생성과 임베딩 저장을 완료했습니다.",
  "storyCount": 120,
  "chunkCount": 540,
  "vectorCount": 540
}
```

#### 필드 설명
| 필드 | 타입 | 설명 |
|---|---|---|
| `success` | boolean | 성공 여부 |
| `message` | string | 처리 결과 메시지 |
| `storyCount` | number | 처리한 동화 수 |
| `chunkCount` | number | 생성된 청크 수 |
| `vectorCount` | number | 생성된 벡터 수 |

#### 프론트 사용 포인트
- 관리자 전용 “임베딩 생성” 버튼
- 진행 완료 후 통계 표시

---

## 4-5. RAG 인덱스 재생성

### `POST /api/rag/index`

전체 동화를 기준으로 청크와 벡터를 다시 생성합니다.
기존 인덱스를 지우고 다시 만드는 개념으로 이해하면 됩니다.

#### Request
- Body 없음

#### Response JSON
```json
{
  "success": true,
  "message": "RAG 인덱스 재생성을 완료했습니다.",
  "storyCount": 120,
  "chunkCount": 540,
  "vectorCount": 540
}
```

#### 프론트 사용 포인트
- 관리자 페이지 재색인 버튼
- 재처리 완료 결과 표시

#### `embed/all-stories`와 차이
- 실제 처리 흐름은 거의 동일
- 응답 `message`만 다름
- 프론트에서는 둘 중 하나만 노출해도 무방

---

## 4-6. 질문하기

### `POST /api/rag/ask`

질문을 임베딩한 후, 유사한 청크를 찾아 답변을 생성합니다.
프론트에서 실제 사용자 채팅/질문 기능에 직접 연결되는 핵심 API입니다.

#### Request Headers
```http
Content-Type: application/json
Accept: application/json
```

#### Request Body
```json
{
  "question": "효녀 심청 이야기의 핵심 내용을 알려줘.",
  "topK": 5
}
```

#### Request 필드 설명
| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `question` | string | Y | 사용자 질문. 공백만 있는 문자열 불가 |
| `topK` | number | N | 상위 유사 청크 개수. 허용 범위 `1~20` |

#### Validation 규칙
- `question`: `@NotBlank`
- `topK`: `@Min(1)`, `@Max(20)`
- `topK` 생략 시 서버 기본값 사용

#### Response Body
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
      "similarity": 0.9123,
      "title": "효녀 심청",
      "chunkText": "title: 효녀 심청\nstoryTitle: 효녀 심청\ndescription: ...",
      "sourceUrl": ""
    }
  ],
  "relatedStoryIds": [10],
  "primaryStoryId": 10,
  "primaryReference": {
    "storyId": 10,
    "chunkId": 101,
    "chunkIndex": 0,
    "similarity": 0.9123,
    "title": "효녀 심청",
    "chunkText": "title: 효녀 심청\nstoryTitle: 효녀 심청\ndescription: ...",
    "sourceUrl": ""
  }
}
```

#### Response 필드 설명
| 필드 | 타입 | 설명 |
|---|---|---|
| `success` | boolean | 성공 여부 |
| `question` | string | 최종 처리된 질문 |
| `answer` | string | 핵심 답변. `coreAnswer`와 동일하게 사용 가능 |
| `rawAnswer` | string | 모델 원본 응답 |
| `thinkAnswer` | string | `<think>` 구간이 있다면 사고 과정 |
| `coreAnswer` | string | 사용자에게 보여줄 핵심 답변 |
| `topK` | number | 실제 사용한 topK |
| `matchedChunkCount` | number | 매칭된 참조 청크 수 |
| `references` | array | 참조 청크 목록 |
| `relatedStoryIds` | array<number> | 핵심 답변 생성에 사용된 참조 청크들에서 추출한 storyId 목록(중복 제거, 검색 순서 유지) |
| `primaryStoryId` | number \| null | 가장 높은 similarity를 가진 첫 번째 참조 청크의 storyId |
| `primaryReference` | object \| null | 대표 story를 결정한 첫 번째 참조 청크 전체 |

#### 대표 story 선택 규칙
- `references`는 similarity 내림차순으로 정렬됩니다.
- `primaryStoryId`와 `primaryReference`는 이 정렬 결과의 첫 번째 항목을 사용합니다.
- 참조 청크가 없으면 둘 다 `null`입니다.

#### 프론트 사용 권장 규칙
- 답변 우선순위
  1. `coreAnswer`
  2. `answer`
  3. `rawAnswer`
- 대표 스토리 1건만 필요하면 `primaryStoryId` 사용
- 대표 스토리의 근거 청크까지 필요하면 `primaryReference` 사용
- 후보 스토리 전체가 필요하면 `relatedStoryIds` 사용
- 참조 문서/근거 UI는 `references` 배열로 렌더링
- `sourceUrl`이 빈 문자열일 수 있으니 조건부 렌더링 필요

#### 대표 스토리 상세 연계 예시
- `primaryStoryId`가 있으면 `GET /api/stories/{storyId}`로 상세 조회 가능
- `references[i].storyId` 또는 `relatedStoryIds[i]`도 동일하게 상세 조회에 사용 가능

#### 프론트 예시 fetch
```js
const response = await fetch('/api/rag/ask', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  },
  body: JSON.stringify({
    question: '효녀 심청 이야기의 핵심 내용을 알려줘.',
    topK: 5
  })
});

const data = await response.json();
```

---

## 4-7. 스토리 상세 조회

### `GET /api/stories/{storyId}`

`storyId`로 원본 동화 상세 정보를 조회합니다.
RAG 답변의 `primaryStoryId`, `relatedStoryIds`, `references[].storyId`와 자연스럽게 연결되는 조회 API입니다.

#### Path Parameter
| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `storyId` | number | Y | 조회할 동화 ID. 양수만 허용 |

#### Response Body
```json
{
  "storyId": 173,
  "collectId": 22,
  "title": "도깨비 씨름 잔치",
  "alternativeTitle": null,
  "creator": "미상",
  "regDateRaw": null,
  "collectionDb": "한국 구비문학대계",
  "subjectCategory": null,
  "subjectKeyword": "도깨비, 씨름, 명당",
  "extent": null,
  "description": "옛날에 작은 모래밭 옆 초가집에 할아버지와 할머니가 오순도순 정답게 살고 있었어요....",
  "spatialCoverage": null,
  "temporalInfo": null,
  "personInfo": null,
  "language": "ko",
  "sourceTitle": null,
  "referenceIdentifier": null,
  "rightsInfo": null,
  "copyrightOthers": null,
  "contentUrl": null,
  "contributor": null,
  "hashKey": "...",
  "useYn": "Y",
  "regDt": "2026-03-26T10:00:00",
  "updDt": "2026-03-26T10:00:00"
}
```

#### Response 필드 설명
| 필드 | 타입 | 설명 |
|---|---|---|
| `storyId` | number \| null | 동화 ID |
| `collectId` | number \| null | 수집 이력 ID |
| `title` | string | 제목 |
| `alternativeTitle` | string \| null | 대체 제목 |
| `creator` | string \| null | 작성자/구연자 |
| `regDateRaw` | string \| null | 원본 등록일 문자열 |
| `collectionDb` | string \| null | 수집 출처 DB명 |
| `subjectCategory` | string \| null | 주제 분류 |
| `subjectKeyword` | string \| null | 주제 키워드 |
| `extent` | string \| null | 분량 정보 |
| `description` | string \| null | 설명 또는 본문 |
| `spatialCoverage` | string \| null | 공간 정보 |
| `temporalInfo` | string \| null | 시간 정보 |
| `personInfo` | string \| null | 인물 정보 |
| `language` | string \| null | 언어 정보 |
| `sourceTitle` | string \| null | 원 출처 제목 |
| `referenceIdentifier` | string \| null | 참조 식별자 |
| `rightsInfo` | string \| null | 권리 정보 |
| `copyrightOthers` | string \| null | 기타 저작권 정보 |
| `contentUrl` | string \| null | 원문 URL |
| `contributor` | string \| null | 기여자 |
| `hashKey` | string \| null | 중복 방지용 해시 |
| `useYn` | string \| null | 사용 여부 |
| `regDt` | string \| null | 생성 시각(ISO-8601) |
| `updDt` | string \| null | 수정 시각(ISO-8601) |

#### 에러 응답
- 존재하지 않는 `storyId`
  - HTTP `404`
  - `{"success": false, "message": "스토리를 찾을 수 없습니다. storyId=..."}`
- `0` 이하 등 잘못된 `storyId`
  - HTTP `400`
  - `{"success": false, "message": "요청 값이 올바르지 않습니다."}`

#### 프론트 사용 포인트
- 챗봇 답변의 대표 스토리 상세 패널 표시
- 참조 스토리 클릭 시 상세 팝업/사이드 패널 표시
- 관리자/운영 화면에서 원문 검수용 상세 조회

---

## 5. 화면 라우트

## 5-1. RAG 테스트 페이지

### `GET /rag/test`

백엔드에서 제공하는 JSP 테스트 페이지입니다.
프론트를 새로 만들 경우 이 페이지는 참고 구현으로 보면 됩니다.

#### 특징
- 기본 `topK` 값: `5`
- 내부적으로 `POST /api/rag/ask` 호출
- 표시 항목
  - 질문
  - topK
  - 매칭 청크 수
  - 사고 과정
  - 핵심 답변
  - 원본 응답
  - 참조 청크 목록

#### 프론트 이관 시 참고 포인트
- 질문 빈값 체크
- `topK`를 프론트에서 `1~20` 범위로 보정
- 응답 실패 시 `message` 우선 표시
- 대표 스토리 1건을 강조하려면 `primaryStoryId` 또는 `primaryReference`를 함께 표시
- `references`는 아코디언/카드 UI로 표시 가능

---

## 6. 프론트 구현 권장 순서

### 일반 사용자 화면
1. 질문 입력창
2. topK 선택 입력
3. `POST /api/rag/ask` 연결
4. 답변 / 근거 청크 UI 표시
5. `primaryStoryId`가 있으면 `GET /api/stories/{storyId}`로 상세 패널 연동

### 관리자 화면
1. 서버 상태 확인: `GET /health`
2. 원천 데이터 적재: `GET /api/collect/nlcf/all`
3. 임베딩 생성: `POST /api/rag/embed/all-stories`
4. 필요 시 재색인: `POST /api/rag/index`
5. 스토리 상세 검수: `GET /api/stories/{storyId}`

---

## 7. 프론트 에러 처리 가이드

권장 처리 예시:

```ts
async function parseApiResponse(response: Response) {
  const contentType = response.headers.get('content-type') || '';

  if (contentType.includes('application/json')) {
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data?.message || '요청 처리 중 오류가 발생했습니다.');
    }
    return data;
  }

  const text = await response.text();
  if (!response.ok) {
    throw new Error(text || '요청 처리 중 오류가 발생했습니다.');
  }
  return text;
}
```

---

## 8. 빠른 체크용 엔드포인트 목록

| 기능 | Method | URL |
|---|---|---|
| 헬스 체크 | `GET` | `/health` |
| 특정 페이지 수집 확인 | `GET` | `/api/collect/nlcf/{pageNo}` |
| 전체 동화 적재 | `GET` | `/api/collect/nlcf/all` |
| 전체 임베딩 생성 | `POST` | `/api/rag/embed/all-stories` |
| RAG 인덱스 재생성 | `POST` | `/api/rag/index` |
| 질문하기 | `POST` | `/api/rag/ask` |
| 스토리 상세 조회 | `GET` | `/api/stories/{storyId}` |
| 테스트 페이지 | `GET` | `/rag/test` |

---

## 9. 참고 사항

- 현재 질문 API는 스트리밍 응답이 아니라 일반 JSON 응답입니다.
- 인증/인가 헤더는 현재 백엔드 API에 적용되어 있지 않습니다.
- CORS 설정은 이 문서 작성 시점 기준 별도 확인되지 않았습니다. 프론트와 백엔드가 다른 Origin이면 추가 설정이 필요할 수 있습니다.
- `sourceUrl`은 빈 값일 수 있습니다.
- `topK`는 서버에서 최대 `20`까지만 허용합니다.
- `GET /api/stories/{storyId}`는 도메인 상세 객체를 직접 반환하므로 `success` 필드가 없습니다.
