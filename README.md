# rag-backend

## Main RAG flow

1. Collect NLCF stories into `tb_nlcf_story`
2. Rebuild the RAG index with `/api/rag/index`
   - split each story into chunks
   - insert into `tb_nlcf_story_chunk`
   - generate embeddings via Ollama
   - insert into `tb_nlcf_story_vector`
3. Ask questions with `/api/rag/ask`
   - embed the question via Ollama
   - search top-k similar chunks
   - generate an answer from the retrieved chunks with Ollama

## Ollama models

This project is configured to use:
- generation model: `qwen3:4b`
- embedding model: `nomic-embed-text:latest`

Default local endpoints in `src/main/resources/application.yml`:
- embedding: `http://localhost:11434/api/embeddings`
- generation: `http://localhost:11434/api/generate`

## API

### Embed all stories from DB
- `POST /api/rag/embed/all-stories`

This endpoint reads all stories from `tb_nlcf_story`, creates chunks, requests embeddings, and stores them into:
- `tb_nlcf_story_chunk`
- `tb_nlcf_story_embedding`

Example response:
```json
{
  "success": true,
  "message": "story DB 전체 조회 후 chunk 생성과 임베딩 저장을 완료했습니다.",
  "storyCount": 120,
  "chunkCount": 540,
  "vectorCount": 540
}
```

### Rebuild index
- `POST /api/rag/index`

Example response:
```json
{
  "success": true,
  "message": "RAG 인덱스 재생성을 완료했습니다.",
  "storyCount": 120,
  "chunkCount": 540,
  "vectorCount": 540
}
```

### Ask question
- `POST /api/rag/ask`

Request body:
```json
{
  "question": "민속 기록에서 의례와 관련된 내용은 뭐야?",
  "topK": 5
}
```

## Notes

- Before running the RAG flow, make sure Ollama is running locally and the required models are pulled.
- Required tables for this flow:
  - `tb_nlcf_story`
  - `tb_nlcf_story_chunk`
  - `tb_nlcf_story_vector`

## Quick try

```bat
ollama pull qwen3:4b
ollama pull nomic-embed-text:latest
C:\Users\LANDSOFT\Desktop\시간과정신의방\rag\rag-backend\gradlew.bat bootRun
```