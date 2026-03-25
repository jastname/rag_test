# DDL 문서

이 문서는 `rag_test` 프로젝트에서 사용하는 한국 구비문학대계 수집/RAG 관련 테이블 4종의 DDL을 정리한 문서입니다.

## 1. 테이블 구성 개요

이 스키마는 아래 흐름으로 연결됩니다.

1. `tb_nlcf_collect`
   - 외부 API 수집 요청 이력 저장
2. `tb_nlcf_story`
   - 수집된 동화 메타데이터 및 본문 저장
3. `tb_nlcf_story_chunk`
   - 동화 본문을 청크 단위로 분할한 데이터 저장
4. `tb_nlcf_story_embedding`
   - 청크별 임베딩 벡터 저장

### 관계 요약

- `tb_nlcf_collect.collect_id` → `tb_nlcf_story.collect_id`
- `tb_nlcf_story.story_id` → `tb_nlcf_story_chunk.story_id`
- `tb_nlcf_story_chunk.chunk_id` → `tb_nlcf_story_embedding.chunk_id`

즉, **수집 이력 → 동화 원문 → 청크 → 임베딩** 순서의 1:N 구조입니다.

---

## 2. `tb_nlcf_collect`

수집 요청 자체의 이력을 저장하는 테이블입니다.
한 번의 API 호출 URL, 페이지 번호, 성공 여부, 원본 응답 등을 보관합니다.

### 주요 컬럼

- `collect_id`: 수집 이력 PK
- `request_url`: 실제 요청 URL
- `page_no`: 수집 페이지 번호
- `num_of_rows`: 페이지당 요청 건수
- `result_code`, `result_msg`: 외부 API 응답 코드/메시지
- `success_yn`: 성공 여부
- `error_message`: 오류 메시지
- `collected_count`: 실제 수집 건수
- `raw_response`: 원본 응답 전체
- `reg_dt`: 생성 시각

### DDL

```sql
CREATE TABLE public.tb_nlcf_collect (
	collect_id bigserial NOT NULL,
	request_url varchar(1000) NULL,
	page_no int4 NULL,
	num_of_rows int4 NULL,
	result_code varchar(20) NULL,
	result_msg varchar(200) NULL,
	success_yn bpchar(1) DEFAULT 'Y'::bpchar NULL,
	error_message text NULL,
	collected_count int4 DEFAULT 0 NULL,
	raw_response text NULL,
	reg_dt timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	CONSTRAINT tb_nlcf_collect_pkey PRIMARY KEY (collect_id)
);
```

---

## 3. `tb_nlcf_story`

수집된 동화 메타데이터와 설명 본문을 저장하는 메인 원천 테이블입니다.
RAG 색인 생성 시 이 테이블의 데이터를 읽어서 청크/임베딩을 생성합니다.

### 주요 컬럼

- `story_id`: 동화 PK
- `collect_id`: 어떤 수집 이력에서 들어온 데이터인지 연결하는 FK
- `title`: 동화 제목
- `alternative_title`: 대체 제목
- `creator`: 작성자/구연자 등
- `reg_date_raw`: 원본 등록일 문자열
- `collection_db`: 수집 출처 DB명
- `subject_category`, `subject_keyword`: 주제 분류/키워드
- `description`: 동화 설명 또는 본문
- `spatial_coverage`, `temporal_info`, `person_info`: 공간/시간/인물 정보
- `language`: 언어 정보
- `source_title`, `reference_identifier`: 원 출처 정보
- `rights_info`, `copyright_others`: 권리 관련 정보
- `content_url`: 원문 URL
- `contributor`: 기여자
- `hash_key`: 중복 방지용 해시 키
- `use_yn`: 사용 여부
- `reg_dt`, `upd_dt`: 생성/수정 시각

### 제약 조건 및 인덱스

- PK: `story_id`
- FK: `collect_id` → `tb_nlcf_collect.collect_id`
- UNIQUE: `hash_key`
- INDEX
  - `title`
  - `creator`
  - `language`
  - `reg_date_raw`
  - `collect_id`

### DDL

```sql
CREATE TABLE public.tb_nlcf_story (
	story_id bigserial NOT NULL,
	collect_id int8 NULL,
	title varchar(500) NOT NULL,
	alternative_title varchar(500) NULL,
	creator varchar(300) NULL,
	reg_date_raw varchar(50) NULL,
	collection_db varchar(300) NULL,
	subject_category varchar(500) NULL,
	subject_keyword varchar(1000) NULL,
	extent varchar(200) NULL,
	description text NULL,
	spatial_coverage varchar(1000) NULL,
	temporal_info varchar(500) NULL,
	person_info varchar(500) NULL,
	"language" varchar(100) NULL,
	source_title varchar(500) NULL,
	reference_identifier varchar(1000) NULL,
	rights_info varchar(500) NULL,
	copyright_others varchar(1000) NULL,
	content_url varchar(1000) NULL,
	contributor varchar(500) NULL,
	hash_key varchar(64) NOT NULL,
	use_yn bpchar(1) DEFAULT 'Y'::bpchar NULL,
	reg_dt timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	upd_dt timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	CONSTRAINT tb_nlcf_story_pkey PRIMARY KEY (story_id),
	CONSTRAINT fk_tb_nlcf_story_01 FOREIGN KEY (collect_id) REFERENCES public.tb_nlcf_collect(collect_id)
);
CREATE INDEX idx_tb_nlcf_story_01 ON public.tb_nlcf_story USING btree (title);
CREATE INDEX idx_tb_nlcf_story_02 ON public.tb_nlcf_story USING btree (creator);
CREATE INDEX idx_tb_nlcf_story_03 ON public.tb_nlcf_story USING btree (language);
CREATE INDEX idx_tb_nlcf_story_04 ON public.tb_nlcf_story USING btree (reg_date_raw);
CREATE INDEX idx_tb_nlcf_story_05 ON public.tb_nlcf_story USING btree (collect_id);
CREATE UNIQUE INDEX uk_tb_nlcf_story_01 ON public.tb_nlcf_story USING btree (hash_key);
```

---

## 4. `tb_nlcf_story_chunk`

동화 본문을 검색 가능한 작은 단위로 나눈 청크 데이터를 저장하는 테이블입니다.
보통 1개의 동화(`story_id`)는 여러 개의 청크를 가질 수 있습니다.

### 주요 컬럼

- `chunk_id`: 청크 PK
- `story_id`: 원본 동화 FK
- `chunk_no`: 동화 내 청크 순번
- `chunk_text`: 청크 텍스트
- `token_count`: 토큰 수
- `reg_dt`: 생성 시각

### 제약 조건 및 인덱스

- PK: `chunk_id`
- FK: `story_id` → `tb_nlcf_story.story_id`
- UNIQUE: `(story_id, chunk_no)`
  - 하나의 동화 안에서 같은 청크 번호가 중복되지 않도록 보장
- INDEX
  - `story_id`

### DDL

```sql
CREATE TABLE public.tb_nlcf_story_chunk (
	chunk_id bigserial NOT NULL,
	story_id int8 NOT NULL,
	chunk_no int4 NOT NULL,
	chunk_text text NOT NULL,
	token_count int4 NULL,
	reg_dt timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	CONSTRAINT tb_nlcf_story_chunk_pkey PRIMARY KEY (chunk_id),
	CONSTRAINT fk_tb_nlcf_story_chunk_01 FOREIGN KEY (story_id) REFERENCES public.tb_nlcf_story(story_id)
);
CREATE INDEX idx_tb_nlcf_story_chunk_01 ON public.tb_nlcf_story_chunk USING btree (story_id);
CREATE UNIQUE INDEX uk_tb_nlcf_story_chunk_01 ON public.tb_nlcf_story_chunk USING btree (story_id, chunk_no);
```

---

## 5. `tb_nlcf_story_embedding`

청크별 임베딩 벡터를 저장하는 테이블입니다.
벡터 검색 또는 유사도 계산의 기반이 되는 데이터입니다.

### 주요 컬럼

- `embedding_id`: 임베딩 PK
- `chunk_id`: 청크 FK
- `embedding_model`: 사용한 임베딩 모델명
- `embedding_vector`: 벡터 데이터 (`public.vector` 타입)
- `story_id`: 원본 동화 ID
- `reg_dt`: 생성 시각

### 제약 조건 및 인덱스

- PK: `embedding_id`
- FK: `chunk_id` → `tb_nlcf_story_chunk.chunk_id`
- UNIQUE: `chunk_id`
  - 하나의 청크에 대해 하나의 임베딩만 저장되도록 제한

### DDL

```sql
CREATE TABLE public.tb_nlcf_story_embedding (
	embedding_id bigserial NOT NULL,
	chunk_id int8 NOT NULL,
	embedding_model varchar(200) NULL,
	embedding_vector public.vector NULL,
	reg_dt timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	story_id int8 NULL,
	CONSTRAINT tb_nlcf_story_embedding_pkey PRIMARY KEY (embedding_id),
	CONSTRAINT fk_tb_nlcf_story_embedding_01 FOREIGN KEY (chunk_id) REFERENCES public.tb_nlcf_story_chunk(chunk_id)
);
CREATE UNIQUE INDEX uk_tb_nlcf_story_embedding_01 ON public.tb_nlcf_story_embedding USING btree (chunk_id);
```

---

## 6. 생성 순서 권장안

FK 의존성을 고려하면 아래 순서로 테이블을 생성하는 것이 안전합니다.

1. `tb_nlcf_collect`
2. `tb_nlcf_story`
3. `tb_nlcf_story_chunk`
4. `tb_nlcf_story_embedding`

반대로 삭제할 때는 아래 역순이 안전합니다.

1. `tb_nlcf_story_embedding`
2. `tb_nlcf_story_chunk`
3. `tb_nlcf_story`
4. `tb_nlcf_collect`

---

## 7. 참고 사항

- `tb_nlcf_story.hash_key`는 동일 데이터 중복 적재를 방지하는 핵심 컬럼입니다.
- `tb_nlcf_story_chunk`는 검색/임베딩 단위로 분리된 데이터를 저장합니다.
- `tb_nlcf_story_embedding.embedding_vector`는 PostgreSQL의 `vector` 타입을 사용하므로, 운영 환경에 `pgvector` 확장이 준비되어 있어야 합니다.
- 현재 프로젝트 매퍼에서는 `tb_nlcf_story`, `tb_nlcf_story_chunk`, `tb_nlcf_story_embedding`를 직접 사용합니다.