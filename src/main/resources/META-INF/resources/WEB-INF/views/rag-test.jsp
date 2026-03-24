<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>RAG 검색 테스트</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 1000px;
            margin: 40px auto;
            padding: 0 20px;
            line-height: 1.5;
            color: #222;
        }
        h1, h2, h3 { margin-bottom: 12px; }
        form {
            border: 1px solid #ddd;
            border-radius: 10px;
            padding: 20px;
            background: #fafafa;
            margin-bottom: 24px;
        }
        label {
            display: block;
            font-weight: 700;
            margin-bottom: 8px;
        }
        textarea, input[type="number"] {
            width: 100%;
            box-sizing: border-box;
            padding: 10px;
            border: 1px solid #bbb;
            border-radius: 6px;
            margin-bottom: 16px;
            font: inherit;
        }
        button {
            background: #2563eb;
            color: white;
            border: 0;
            border-radius: 6px;
            padding: 10px 16px;
            cursor: pointer;
        }
        button:disabled {
            background: #93c5fd;
            cursor: wait;
        }
        .panel, .answer, .meta, .reference {
            border: 1px solid #e5e7eb;
            border-radius: 10px;
            padding: 16px;
            margin-bottom: 16px;
            background: #fff;
        }
        .error {
            background: #fee2e2;
            color: #991b1b;
            border: 1px solid #fecaca;
        }
        .loading {
            background: #eff6ff;
            color: #1d4ed8;
            border: 1px solid #bfdbfe;
        }
        .think {
            background: #fff7ed;
            border-color: #fdba74;
        }
        .core {
            background: #f0fdf4;
            border-color: #86efac;
        }
        .reference pre, .answer-text {
            white-space: pre-wrap;
            word-break: break-word;
            background: #f8fafc;
            padding: 12px;
            border-radius: 8px;
            overflow-x: auto;
        }
        .muted {
            color: #666;
            font-size: 14px;
        }
        .hidden {
            display: none;
        }
        .collapsible {
            border: 1px solid #e5e7eb;
            border-radius: 10px;
            margin-bottom: 16px;
            background: #fff;
            overflow: hidden;
        }
        .toggle-button {
            width: 100%;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            padding: 16px;
            background: transparent;
            color: #111827;
            font: inherit;
            font-weight: 700;
            text-align: left;
        }
        .toggle-button:hover {
            background: #f8fafc;
        }
        .toggle-button::after {
            content: '접기';
            font-size: 13px;
            color: #6b7280;
            flex-shrink: 0;
        }
        .toggle-button.collapsed::after {
            content: '펼치기';
        }
        .collapsible-content {
            padding: 0 16px 16px;
        }
        .collapsible-content.hidden {
            display: none;
        }
    </style>
</head>
<body>
    <h1>RAG 검색 테스트 페이지</h1>
    <p class="muted">AJAX로 질문을 보내고, <code>&lt;/think&gt;</code> 이전 사고과정과 핵심 답변을 분리해서 확인할 수 있습니다.</p>

    <form id="ragForm" action="javascript:void(0);" method="post" novalidate>
        <label for="question">질문</label>
        <textarea id="question" name="question" rows="5" placeholder="예: 효녀 심청 이야기의 핵심 내용을 알려줘."></textarea>

        <label for="topK">Top K (1~20)</label>
        <input id="topK" type="number" name="topK" min="1" max="20" value="${empty topK ? 5 : topK}" />

        <button id="submitButton" type="submit">검색 실행</button>
    </form>

    <div id="loadingPanel" class="panel loading hidden">검색 중입니다. 임베딩 생성과 답변 생성을 기다리는 중입니다...</div>
    <div id="errorPanel" class="panel error hidden"></div>

    <div id="resultPanel" class="hidden">
        <div class="meta">
            <h2>검색 요약</h2>
            <p><strong>질문:</strong> <span id="summaryQuestion"></span></p>
            <p><strong>Top K:</strong> <span id="summaryTopK"></span></p>
            <p><strong>매칭 청크 수:</strong> <span id="summaryChunkCount"></span></p>
        </div>

        <div class="collapsible" data-collapsible>
            <button type="button" class="toggle-button" data-toggle-target="thinkContent" aria-expanded="true" aria-controls="thinkContent">사고 과정</button>
            <div id="thinkContent" class="collapsible-content">
                <div id="thinkSection" class="answer think hidden">
                    <div id="thinkAnswer" class="answer-text"></div>
                </div>
            </div>
        </div>

        <div class="collapsible" data-collapsible>
            <button type="button" class="toggle-button" data-toggle-target="coreContent" aria-expanded="true" aria-controls="coreContent">핵심 답변</button>
            <div id="coreContent" class="collapsible-content">
                <div id="coreSection" class="answer core hidden">
                    <div id="coreAnswer" class="answer-text"></div>
                </div>
            </div>
        </div>

        <div class="collapsible" data-collapsible>
            <button type="button" class="toggle-button" data-toggle-target="rawContent" aria-expanded="true" aria-controls="rawContent">원본 응답</button>
            <div id="rawContent" class="collapsible-content">
                <div id="rawSection" class="answer hidden">
                    <div id="rawAnswer" class="answer-text"></div>
                </div>
            </div>
        </div>

        <div class="collapsible" data-collapsible>
            <button type="button" class="toggle-button" data-toggle-target="referencesContent" aria-expanded="true" aria-controls="referencesContent">참조 청크</button>
            <div id="referencesContent" class="collapsible-content">
                <div id="referencesSection">
                    <div id="referencesContainer"></div>
                </div>
            </div>
        </div>
    </div>

    <script>
        const form = document.getElementById('ragForm');
        const submitButton = document.getElementById('submitButton');
        const loadingPanel = document.getElementById('loadingPanel');
        const errorPanel = document.getElementById('errorPanel');
        const resultPanel = document.getElementById('resultPanel');
        const summaryQuestion = document.getElementById('summaryQuestion');
        const summaryTopK = document.getElementById('summaryTopK');
        const summaryChunkCount = document.getElementById('summaryChunkCount');
        const thinkSection = document.getElementById('thinkSection');
        const thinkAnswer = document.getElementById('thinkAnswer');
        const coreSection = document.getElementById('coreSection');
        const coreAnswer = document.getElementById('coreAnswer');
        const rawSection = document.getElementById('rawSection');
        const rawAnswer = document.getElementById('rawAnswer');
        const referencesContainer = document.getElementById('referencesContainer');
        const questionInput = document.getElementById('question');
        const topKInput = document.getElementById('topK');
        const toggleButtons = document.querySelectorAll('.toggle-button');
        let isSubmitting = false;

        function setVisible(element, visible) {
            element.classList.toggle('hidden', !visible);
        }

        function syncCollapsibleState(button, content, expanded) {
            button.classList.toggle('collapsed', !expanded);
            button.setAttribute('aria-expanded', String(expanded));
            content.classList.toggle('hidden', !expanded);
        }

        function setSectionOpen(sectionId, open) {
            const button = document.querySelector('[data-toggle-target="' + sectionId + '"]');
            const content = document.getElementById(sectionId);
            if (!button || !content) {
                return;
            }
            syncCollapsibleState(button, content, open);
        }

        function openAllAvailableSections() {
            ['thinkContent', 'coreContent', 'rawContent', 'referencesContent'].forEach(function(sectionId) {
                const content = document.getElementById(sectionId);
                if (!content) {
                    return;
                }
                const nestedVisibleSection = content.querySelector(':scope > :not(.hidden)');
                setSectionOpen(sectionId, Boolean(nestedVisibleSection));
            });
        }

        function showError(message) {
            errorPanel.textContent = message;
            setVisible(errorPanel, true);
        }

        function clearState() {
            errorPanel.textContent = '';
            summaryQuestion.textContent = '';
            summaryTopK.textContent = '';
            summaryChunkCount.textContent = '';
            thinkAnswer.textContent = '';
            coreAnswer.textContent = '';
            rawAnswer.textContent = '';
            setVisible(errorPanel, false);
            setVisible(resultPanel, false);
            setVisible(thinkSection, false);
            setVisible(coreSection, false);
            setVisible(rawSection, false);
            referencesContainer.innerHTML = '';
            openAllAvailableSections();
        }

        function renderReferences(references) {
            if (!Array.isArray(references) || references.length === 0) {
                referencesContainer.innerHTML = '<p class="muted">참조된 청크가 없습니다.</p>';
                return;
            }

            referencesContainer.innerHTML = references.map(function(ref, index) {
                const sourceUrl = ref.sourceUrl
                    ? '<p><strong>sourceUrl:</strong> <a href="' + ref.sourceUrl + '" target="_blank" rel="noreferrer noopener">' + ref.sourceUrl + '</a></p>'
                    : '';

                return ''
                    + '<div class="reference">'
                    + '  <h3>#' + (index + 1) + ' ' + (ref.title || '') + '</h3>'
                    + '  <p><strong>storyId:</strong> ' + (ref.storyId ?? '') + ' / <strong>chunkId:</strong> ' + (ref.chunkId ?? '') + ' / <strong>chunkIndex:</strong> ' + (ref.chunkIndex ?? '') + '</p>'
                    + '  <p><strong>similarity:</strong> ' + (ref.similarity ?? '') + '</p>'
                    + sourceUrl
                    + '  <pre>' + (ref.chunkText || '') + '</pre>'
                    + '</div>';
            }).join('');
        }

        async function parseResponseData(response) {
            const contentType = response.headers.get('content-type') || '';
            if (contentType.indexOf('application/json') >= 0) {
                return await response.json();
            }

            const text = await response.text();
            if (!text) {
                return null;
            }

            try {
                return JSON.parse(text);
            } catch (parseError) {
                if (response.ok) {
                    throw new Error('서버가 JSON 응답 대신 다른 내용을 반환했습니다. API 응답 형식을 확인해 주세요.');
                }
                return { message: text };
            }
        }

        form.addEventListener('submit', async function(event) {
            event.preventDefault();

            if (isSubmitting) {
                return;
            }

            clearState();

            const question = questionInput.value.trim();
            const topK = Math.min(20, Math.max(1, Number(topKInput.value || 5) || 5));
            topKInput.value = topK;

            if (!question) {
                showError('질문을 입력해 주세요.');
                questionInput.focus();
                return;
            }

            isSubmitting = true;
            submitButton.disabled = true;
            setVisible(loadingPanel, true);

            try {
                const response = await fetch('${pageContext.request.contextPath}/api/rag/ask', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json',
                        'X-Requested-With': 'XMLHttpRequest'
                    },
                    body: JSON.stringify({
                        question: question,
                        topK: topK
                    })
                });

                const data = await parseResponseData(response);

                if (!response.ok) {
                    const message = data && data.message ? data.message : '검색 요청 처리 중 오류가 발생했습니다.';
                    throw new Error(message);
                }

                if (!data) {
                    throw new Error('서버 응답이 비어 있습니다.');
                }

                summaryQuestion.textContent = data.question || question;
                summaryTopK.textContent = data.topK ?? topK;
                summaryChunkCount.textContent = data.matchedChunkCount ?? 0;

                thinkAnswer.textContent = data.thinkAnswer || '';
                coreAnswer.textContent = data.coreAnswer || data.answer || '';
                rawAnswer.textContent = data.rawAnswer || data.answer || '';

                setVisible(thinkSection, Boolean(data.thinkAnswer));
                setVisible(coreSection, Boolean(data.coreAnswer || data.answer));
                setVisible(rawSection, Boolean(data.rawAnswer));

                renderReferences(data.references);
                openAllAvailableSections();
                setVisible(resultPanel, true);
                resultPanel.scrollIntoView({ behavior: 'smooth', block: 'start' });
            } catch (error) {
                showError(error && error.message ? error.message : '알 수 없는 오류가 발생했습니다.');
            } finally {
                isSubmitting = false;
                submitButton.disabled = false;
                setVisible(loadingPanel, false);
            }
        });

        toggleButtons.forEach(function(button) {
            button.addEventListener('click', function() {
                const content = document.getElementById(button.dataset.toggleTarget);
                if (!content) {
                    return;
                }
                const expanded = button.getAttribute('aria-expanded') === 'true';
                syncCollapsibleState(button, content, !expanded);
            });
        });
    </script>
</body>
</html>
