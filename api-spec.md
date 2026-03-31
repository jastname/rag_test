# Frontend API 명세서

이 문서는 `rag_test` 프로젝트에서 현재 백엔드가 실제로 제공하는 엔드포인트와 요청/응답 형식을 프론트엔드 관점에서 정리한 문서입니다.

기준 환경
- Base URL: `http://localhost:8080`
- JSON 요청 API 기본 Content-Type: `application/json`
- 문자셋: UTF-8
- LLM / 임베딩 연동: **Spring AI 1.0.0** (`spring-ai-starter-model-ollama`) 를 통해 Ollama에 연결
  - Chat 모델: `qwen3:4b` (`spring.ai.ollama.chat.options.model`)
  - Embedding 모델: `nomic-embed-text:latest` (`spring.ai.ollama.embedding.options.model`)
  - Ollama Base URL: `http://127.0.0.1:11434` (`spring.ai.ollama.base-url`)

---

## 1. 전체 흐름 요약

일반적인 사용 순서는 아래와 같습니다.

1. 원천 동화 데이터 적재
   - `GET /api/collect/nlcf/all`
2. 청크/임베딩 생성
   - `POST /api/rag/embed/all-stories`
   - 또는 `POST /api/rag/index`
3. 질문/답변 기능 사용
   - `POST /api/rag/ask`
   - `POST /api/rag/ask/stream` (SSE 스트리밍)
4. 대표 또는 참조 스토리 상세 조회
   - `GET /api/stories/{storyId}`
5. 테스트 화면 확인
   - `GET /rag/test`

---

## 2. 공통 응답 특성

### 2-1. 성공 응답

대부분의 JSON API는 `success` 필드를 포함합니다.

```json
{
  "success": true
}
```

단, `GET /api/stories/{storyId}`는 공통 래퍼 없이 `StoryDetailResponse` 객체를 직접 반환합니다.

### 2-2. 에러 응답

전역 예외 처리기 `ApiExceptionHandler` 기준, 현재 코드에서 문서화 가능한 공통 에러 응답 형식은 아래와 같습니다.

```json
{
  "success": false,
  "message": "에러 메시지"
}
```

현재 명시적으로 처리되는 케이스
- `ResponseStatusException`
  - 예: 존재하지 않는 `storyId` 조회 시 `404 Not Found`
- `ConstraintViolationException`
- `HandlerMethodValidationException`
  - 예: `@Positive`, `@Min`, `@Max`, `@NotBlank` 검증 실패

예시 1. 존재하지 않는 스토리 조회

```json
{
  "success": false,
  "message": "스토리를 찾을 수 없습니다. storyId=999"
}
```

예시 2. 잘못된 요청 값

```json
{
  "success": false,
  "message": "요청 값이 올바르지 않습니다."
}
```

프론트 권장 처리 순서
1. `response.ok` 확인
2. JSON 파싱 시도
3. `data.message`가 있으면 우선 표시
4. 없으면 기본 에러 문구 사용

---

## 3. 타입 정의 예시

프론트에서 바로 참고할 수 있도록 TypeScript 기준 예시 타입을 정리했습니다.

```ts
export interface ErrorResponse {
  success: false;
  message: string;
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
  usedStoryIds: number[];
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
  regDt: string | null; // 서버에서는 LocalDateTime 직렬화 값
  updDt: string | null; // 서버에서는 LocalDateTime 직렬화 값
}
```

참고
- Java DTO의 `regDt`, `updDt` 타입은 `LocalDateTime`입니다.
- JSON 직렬화 시점에는 문자열 형태로 내려온다고 보는 것이 프론트 처리에 안전합니다.

### SSE 스트리밍 이벤트 타입 (참고용)

`/api/rag/ask/stream` 응답에서 사용되는 SSE 이벤트를 프론트에서 구분할 때 참고할 수 있는 타입입니다.

```ts
// SSE 이벤트 종류
type SseEventName = 'references' | 'result' | '';

// references 이벤트 data
type ReferencesEventData = RagChunkResult[];

// message(기본) 이벤트 data — LLM 토큰 문자열
type TokenEventData = string;

// result 이벤트 data — 최종 응답 (RagAskResponse와 동일)
type ResultEventData = RagAskResponse;
```

---

## 4. API 상세

## 4-1. 헬스 체크

### `GET /health`

서버 기동 여부를 확인하는 가장 단순한 API입니다.

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
- 개발 환경 헬스 체크

---

## 4-2. 특정 페이지 동화 수집

### `GET /api/collect/nlcf/{pageNo}`

지정한 페이지 번호에 대해 현재 수집 가능한 건수를 반환합니다.
컨트롤러는 `savedCount`라는 이름으로 반환하지만, 실제 의미는 해당 페이지 처리 건수입니다.

#### Path Parameter
| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `pageNo` | number | Y | 조회/수집할 페이지 번호 |

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
| `savedCount` | number | 해당 페이지 처리 건수 |

#### 프론트 사용 포인트
- 관리자 화면에서 단일 페이지 테스트
- 수집 API 연결 상태 확인

---

## 4-3. 전체 동화 데이터 적재

### `GET /api/collect/nlcf/all`

1페이지부터 시작해 동화 데이터를 순차 수집하고 DB에 적재합니다.
서비스 설명 기준으로, 기존 수집 이력과 동화 데이터를 초기화한 뒤 전체 페이지를 재수집하는 흐름입니다.

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
- 프론트에서 버튼 연타 방지를 위한 로딩 상태를 두는 것이 좋습니다.
- RAG 기능을 쓰기 전에 먼저 수행하는 것이 안전합니다.

---

## 4-4. 전체 동화 임베딩 생성

### `POST /api/rag/embed/all-stories`

DB에 저장된 전체 스토리를 읽어서 청크를 만들고 임베딩을 생성합니다.
실제 구현은 내부적으로 `rebuildIndex()`를 호출한 뒤, 메시지만 별도로 설정합니다.

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
| `storyCount` | number | 처리한 스토리 수 |
| `chunkCount` | number | 생성된 청크 수 |
| `vectorCount` | number | 저장된 벡터 수 |

---

## 4-5. RAG 인덱스 재생성

### `POST /api/rag/index`

전체 스토리를 기준으로 청크와 벡터 인덱스를 재생성합니다.
기존 청크/벡터 데이터는 삭제 후 다시 적재됩니다.

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

#### 필드 설명
| 필드 | 타입 | 설명 |
|---|---|---|
| `success` | boolean | 성공 여부 |
| `message` | string | 처리 결과 메시지 |
| `storyCount` | number | 처리한 스토리 수 |
| `chunkCount` | number | 생성된 청크 수 |
| `vectorCount` | number | 저장된 벡터 수 |

---

## 4-6. 질문하기

### `POST /api/rag/ask`

질문을 임베딩한 뒤, 하이브리드 검색으로 상위 청크를 찾고, 해당 참조 청크를 바탕으로 LLM 답변을 생성합니다.
응답에는 답변 본문 외에 참조 청크, 대표 스토리, 모델 응답에서 파싱한 `usedStoryIds`도 포함됩니다.

#### Request Body

```json
{
  "question": "효녀 심청 이야기의 핵심 내용을 알려줘.",
  "topK": 5
}
```

#### 요청 필드 설명
| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `question` | string | Y | 질문 문자열. 공백만 있는 값은 불가 |
| `topK` | number | N | 반환할 상위 참조 개수. `1~20` |

- `topK` 미지정 시 `application.yml`의 `app.rag.top-k` 값을 사용합니다.

#### Response JSON 예시

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

#### 응답 필드 설명
| 필드 | 타입 | 설명 |
|---|---|---|
| `success` | boolean | 성공 여부 |
| `question` | string | 사용자 질문 |
| `answer` | string | 현재는 `coreAnswer`와 동일한 최종 답변 |
| `rawAnswer` | string | LLM 원본 응답 |
| `thinkAnswer` | string | `</think>` 이전 사고 과정 |
| `coreAnswer` | string | 최종 핵심 답변 |
| `topK` | number | 실제 사용된 topK |
| `matchedChunkCount` | number | 매칭된 청크 수 |
| `references` | `RagChunkResult[]` | 참조한 청크 목록 |
| `relatedStoryIds` | `number[]` | `references`에서 추출한 스토리 ID 목록 |
| `usedStoryIds` | `number[]` | 생성 답변의 `[[USED_STORY_IDS:...]]` 블록에서 파싱한 스토리 ID 목록 |
| `primaryStoryId` | number \/ null | 첫 번째 대표 참조 스토리 ID |
| `primaryReference` | `RagChunkResult \| null` | 첫 번째 대표 참조 청크 |

#### 참조 청크(`RagChunkResult`) 필드
| 필드 | 타입 | 설명 |
|---|---|---|
| `storyId` | number \| null | 스토리 ID |
| `chunkId` | number \| null | 청크 ID |
| `chunkIndex` | number | 청크 순번 |
| `similarity` | number | 하이브리드 점수/유사도 값 |
| `title` | string | 청크에서 추출한 제목 |
| `chunkText` | string | 원본 청크 텍스트 |
| `sourceUrl` | string | 청크에서 추출한 출처 URL |

#### 프론트 사용 팁
- `answer`만 바로 노출해도 되지만, 디버깅 화면에서는 `thinkAnswer`, `rawAnswer`, `references`를 함께 보여주면 유용합니다.
- `primaryStoryId`가 있으면 상세 조회 버튼을 연결하기 좋습니다.
- `usedStoryIds`는 생성 모델이 실제로 사용했다고 표기한 스토리 목록으로, `references`와 다를 수 있습니다.

---

## 4-7. 질문하기 (SSE 스트리밍)

### `POST /api/rag/ask/stream`

4-6의 `/ask`와 동일한 요청을 보내면, LLM 답변을 토큰 단위로 실시간 스트리밍합니다.
검색(하이브리드 검색)은 동기로 먼저 수행되고, 답변 생성 단계만 SSE(Server-Sent Events)로 스트리밍됩니다.

#### Request Body

`/api/rag/ask`와 동일합니다.

```json
{
  "question": "효녀 심청 이야기의 핵심 내용을 알려줘.",
  "topK": 5
}
```

#### 요청 필드 설명
| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `question` | string | Y | 질문 문자열. 공백만 있는 값은 불가 |
| `topK` | number | N | 반환할 상위 참조 개수. `1~20` |

#### Response: `text/event-stream`

SSE 이벤트가 아래 순서로 전송됩니다.

**① `references` 이벤트** — 검색 완료 직후, 토큰 스트리밍 시작 전

참조 청크 목록이 JSON 배열로 전송됩니다. 프론트에서 토큰이 들어오기 전에 참조 목록을 먼저 표시할 수 있습니다.

```
event: references
data: [{"storyId":10,"chunkId":101,"chunkIndex":0,"title":"효녀 심청","chunkText":"...","sourceUrl":"","similarity":0.91}]
```

**② `message` 이벤트 (반복)** — LLM 토큰이 생성될 때마다

모델이 토큰을 생성할 때마다 전송됩니다. 프론트에서 하나씩 이어 붙여 답변을 실시간으로 표시합니다.

```
data: 심청
data: 은
data:  아버지를
data:  위해
data:  자신을
```

**③ `result` 이벤트** — 스트리밍 완료 후

`/api/rag/ask`의 응답과 동일한 `RagAskResponse` JSON이 전송됩니다.
토큰 스트리밍 중에는 사용할 수 없는 후처리 결과(`thinkAnswer`, `coreAnswer`, `usedStoryIds` 등)가 여기 포함됩니다.

```
event: result
data: {"success":true,"question":"효녀 심청 이야기의 핵심 내용을 알려줘.","answer":"...","rawAnswer":"...","thinkAnswer":"...","coreAnswer":"...","topK":5,"matchedChunkCount":5,"references":[...],"relatedStoryIds":[10],"usedStoryIds":[10],"primaryStoryId":10,"primaryReference":{...}}
```

#### 프론트 사용 팁
- `references` 이벤트를 받으면 참조 패널을 먼저 렌더링할 수 있습니다.
- `message` 이벤트의 `data`를 순서대로 이어 붙이면 실시간 타이핑 효과를 줄 수 있습니다.
- `result` 이벤트에서 최종 메타데이터(`usedStoryIds`, `thinkAnswer` 등)를 받아 UI를 업데이트합니다.
- 타임아웃은 서버에서 5분(300초)으로 설정되어 있습니다.

#### 프론트 연동 예시 (fetch + ReadableStream)

```ts
const response = await fetch('http://localhost:8080/api/rag/ask/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ question: '효녀 심청 이야기', topK: 5 }),
});

const reader = response.body!.getReader();
const decoder = new TextDecoder();
let buffer = '';

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  buffer += decoder.decode(value, { stream: true });

  // SSE 이벤트 파싱
  const lines = buffer.split('\n');
  buffer = lines.pop() || '';

  let eventName = '';
  for (const line of lines) {
    if (line.startsWith('event: ')) {
      eventName = line.slice(7);
    } else if (line.startsWith('data:')) {
      const data = line.slice(5);
      if (eventName === 'references') {
        const refs = JSON.parse(data);
        console.log('참조 청크:', refs);
      } else if (eventName === 'result') {
        const result = JSON.parse(data);
        console.log('최종 결과:', result);
      } else {
        // 토큰 스트리밍
        process.stdout.write(data);
      }
      eventName = '';
    }
  }
}
```

---

## 4-8. 스토리 상세 조회

### `GET /api/stories/{storyId}`

특정 스토리 상세 정보를 반환합니다.
존재하지 않는 `storyId`면 `404`와 공통 에러 JSON을 반환합니다.

#### Path Parameter
| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `storyId` | number | Y | 조회할 스토리 ID. `1` 이상 |

#### Response JSON 예시

```json
{
  "storyId": 173,
  "collectId": 10,
  "title": "효녀 심청",
  "alternativeTitle": null,
  "creator": null,
  "regDateRaw": "2024-01-01",
  "collectionDb": "한국구비문학대계",
  "subjectCategory": null,
  "subjectKeyword": null,
  "extent": null,
  "description": "심청은 아버지를 위해 자신을 희생한다.",
  "spatialCoverage": null,
  "temporalInfo": null,
  "personInfo": null,
  "language": "ko",
  "sourceTitle": null,
  "referenceIdentifier": null,
  "rightsInfo": null,
  "copyrightOthers": null,
  "contentUrl": "https://example.com/story/173",
  "contributor": null,
  "hashKey": "...",
  "useYn": "Y",
  "regDt": "2026-03-26T10:00:00",
  "updDt": "2026-03-26T10:05:00"
}
```

#### 필드 설명
`StoryDetailResponse`는 아래 필드를 가집니다.

- `storyId`, `collectId`
- `title`, `alternativeTitle`, `creator`
- `regDateRaw`, `collectionDb`, `subjectCategory`, `subjectKeyword`
- `extent`, `description`, `spatialCoverage`, `temporalInfo`, `personInfo`
- `language`, `sourceTitle`, `referenceIdentifier`
- `rightsInfo`, `copyrightOthers`, `contentUrl`, `contributor`
- `hashKey`, `useYn`
- `regDt`, `updDt`

참고
- `regDt`, `updDt`의 Java 타입은 `LocalDateTime`입니다.
- 프론트에서는 문자열 날짜로 받아 처리하는 것이 무난합니다.

---

## 4-9. 테스트 페이지

### `GET /rag/test`

JSP 기반 RAG 테스트 페이지를 반환합니다.
이 페이지는 내부적으로 `POST /api/rag/ask`를 호출하는 용도로 사용됩니다.

#### 특징
- 화면 기본 `topK`는 컨트롤러에서 `5`를 주입합니다.
- 질문, `topK`, 검색 결과, 사고 과정, 핵심 답변, 원본 응답, 참조 청크를 확인하는 테스트 UI 용도입니다.

#### 프론트 참고
- 별도 프론트 애플리케이션을 만들 경우 이 페이지 없이 JSON API만 사용하면 됩니다.

---

## 5. 프론트 연동 예시

### 5-1. 질문 요청 예시

```ts
const response = await fetch('http://localhost:8080/api/rag/ask', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({
    question: '효녀 심청 이야기의 핵심 내용을 알려줘.',
    topK: 5,
  }),
});

const data = await response.json();
```

### 5-2. 스트리밍 질문 요청 예시

```ts
const response = await fetch('http://localhost:8080/api/rag/ask/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ question: '효녀 심청 이야기', topK: 5 }),
});

const reader = response.body!.getReader();
const decoder = new TextDecoder();
let buffer = '';
let answerText = '';

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  buffer += decoder.decode(value, { stream: true });

  const lines = buffer.split('\n');
  buffer = lines.pop() || '';

  let eventName = '';
  for (const line of lines) {
    if (line.startsWith('event: ')) {
      eventName = line.slice(7);
    } else if (line.startsWith('data:')) {
      const data = line.slice(5);
      if (eventName === 'references') {
        const refs = JSON.parse(data);
        // 참조 청크 목록 렌더링
      } else if (eventName === 'result') {
        const result = JSON.parse(data);
        // 최종 메타데이터(usedStoryIds, thinkAnswer 등) 처리
      } else {
        answerText += data;
        // 토큰을 이어 붙여 실시간 답변 표시
      }
      eventName = '';
    }
  }
}
```

### 5-3. 대표 스토리 상세 조회 예시

```ts
const storyResponse = await fetch(`http://localhost:8080/api/stories/${data.primaryStoryId}`);
const story = await storyResponse.json();
```

---

## 6. 프론트 구현 체크포인트

- `POST /api/rag/ask`는 공백 질문을 허용하지 않습니다.
- `topK`는 `1~20` 범위만 허용됩니다.
- `GET /api/stories/{storyId}`의 `storyId`는 `1` 이상이어야 합니다.
- 관리자 액션 성격의 수집/인덱싱 API는 응답 시간이 길 수 있습니다.
- Ollama 또는 DB 연결 상태에 따라 런타임 오류가 발생할 수 있으므로 에러 UI를 준비하는 것이 좋습니다.
  - LLM/임베딩 호출은 Spring AI(`ChatModel`, `EmbeddingModel`)를 통해 Ollama에 연결되며, Ollama 서버가 중지되어 있으면 `500` 에러가 발생합니다.
- `POST /api/rag/ask/stream`은 SSE(Server-Sent Events) 방식으로 응답합니다.
  - 요청 형식은 `/ask`와 동일하며, 응답만 `text/event-stream`으로 달라집니다.
  - SSE 타임아웃은 서버에서 5분(300초)입니다. LLM 모델 응답이 느리면 타임아웃이 발생할 수 있습니다.
  - 스트리밍 중 클라이언트가 연결을 끊으면 서버 로그에 경고가 남지만 서버 오류는 아닙니다.

---

## 7. 변경 시 우선 확인할 파일

API 계약이 바뀔 가능성이 높은 위치는 아래와 같습니다.

- `src/main/java/com/example/rag/collect/controller/NlcfCollectController.java`
- `src/main/java/com/example/rag/rag/controller/RagController.java`
- `src/main/java/com/example/rag/story/controller/StoryController.java`
- `src/main/java/com/example/rag/rag/dto/RagAskResponse.java`
- `src/main/java/com/example/rag/story/dto/StoryDetailResponse.java`
- `src/main/java/com/example/rag/common/controller/ApiExceptionHandler.java`

Spring AI 연동 및 모델 설정 관련:

- `src/main/resources/application.yml` — `spring.ai.ollama.*` 설정 (모델명, base-url 등)
- `src/main/java/com/example/rag/rag/service/AnswerGenerationService.java` — Spring AI `ChatModel` 사용
- `src/main/java/com/example/rag/rag/service/SimpleEmbeddingService.java` — Spring AI `EmbeddingModel` 사용
- `build.gradle` — `spring-ai-starter-model-ollama` 의존성 및 Spring AI BOM 버전

---

## 8. 기술 스택 및 설정 요약

### 8-1. 주요 의존성

| 항목 | 버전 / 설정 |
|---|---|
| Spring Boot | 3.5.12 |
| Spring AI | 1.0.0 (`spring-ai-bom`) |
| Spring AI Ollama Starter | `spring-ai-starter-model-ollama` |
| Java | 17 |
| DB | PostgreSQL (MyBatis) |

### 8-2. LLM / 임베딩 설정 (`application.yml`)

```yaml
spring:
  ai:
    ollama:
      base-url: http://127.0.0.1:11434
      chat:
        options:
          model: qwen3:4b        # 답변 생성용 Chat 모델
      embedding:
        options:
          model: nomic-embed-text:latest  # 임베딩 모델
```

### 8-3. Spring AI 연동 구조

기존에는 `RestClient`로 Ollama REST API(`/api/generate`, `/api/embeddings`)를 직접 호출했으나, 현재는 **Spring AI**가 제공하는 추상화 계층을 사용합니다.

| 역할 | 이전 방식 | 현재 방식 (Spring AI) |
|---|---|---|
| 답변 생성 | `RestClient` → `POST /api/generate` | `ChatModel.call(Prompt)` |
| 임베딩 | `RestClient` → `POST /api/embeddings` | `EmbeddingModel.embedForResponse(List)` |
| 모델 설정 | `app.rag.llm.model`, `app.rag.embedding.model` | `spring.ai.ollama.chat.options.model`, `spring.ai.ollama.embedding.options.model` |
| 서버 URL | `app.rag.llm.api-url`, `app.rag.embedding.api-url` | `spring.ai.ollama.base-url` |

Spring AI 자동 설정에 의해 `OllamaChatModel`, `OllamaEmbeddingModel` 빈이 자동 등록되며, 서비스 코드에서는 `ChatModel`, `EmbeddingModel` 인터페이스만 의존합니다.

### 8-4. 삭제된 파일

Spring AI 마이그레이션으로 아래 파일들이 삭제되었습니다.

- `src/main/java/com/example/rag/rag/dto/OllamaGenerateRequest.java`
- `src/main/java/com/example/rag/rag/dto/OllamaGenerateResponse.java`
- `src/main/java/com/example/rag/rag/dto/OllamaEmbeddingRequest.java`
- `src/main/java/com/example/rag/rag/dto/OllamaEmbeddingResponse.java`
