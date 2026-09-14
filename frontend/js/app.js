import {request, streamRequest} from './api.js?v=20260914-1';
import {renderDocumentContent, renderMarkdown} from './markdown.js?v=20260911-3';

const elements = {
    askButton: document.getElementById('askBtn'),
    askButtonTip: document.getElementById('askButtonTip'),
    askButtonWrapper: document.getElementById('askButtonWrapper'),
    chatList: document.getElementById('chatList'),
    clearChatButton: document.getElementById('clearChatBtn'),
    content: document.getElementById('content'),
    contentCount: document.getElementById('contentCount'),
    contentViewer: document.getElementById('contentViewer'),
    contentViewerBackdrop: document.getElementById('contentViewerBackdrop'),
    contentViewerBody: document.getElementById('contentViewerBody'),
    contentViewerClose: document.getElementById('contentViewerClose'),
    contentViewerMeta: document.getElementById('contentViewerMeta'),
    contentViewerTitle: document.getElementById('contentViewerTitle'),
    contentViewerType: document.getElementById('contentViewerType'),
    deleteDialog: document.getElementById('deleteDialog'),
    deleteDialogBackdrop: document.getElementById('deleteDialogBackdrop'),
    deleteDialogCancel: document.getElementById('deleteDialogCancel'),
    deleteDialogClose: document.getElementById('deleteDialogClose'),
    deleteDialogConfirm: document.getElementById('deleteDialogConfirm'),
    deleteDialogDocumentMeta: document.getElementById('deleteDialogDocumentMeta'),
    deleteDialogDocumentName: document.getElementById('deleteDialogDocumentName'),
    documentCount: document.getElementById('documentCount'),
    documentList: document.getElementById('documentList'),
    documentSearch: document.getElementById('documentSearch'),
    dropZone: document.getElementById('dropZone'),
    file: document.getElementById('file'),
    importButton: document.getElementById('importBtn'),
    operationMessage: document.getElementById('operationMessage'),
    question: document.getElementById('question'),
    questionCount: document.getElementById('questionCount'),
    refreshDocumentsButton: document.getElementById('refreshDocumentsBtn'),
    selectedFile: document.getElementById('selectedFile'),
    status: document.getElementById('status'),
    stopButton: document.getElementById('stopBtn'),
    title: document.getElementById('title'),
    toast: document.getElementById('toast'),
    topK: document.getElementById('topK'),
    uploadButton: document.getElementById('uploadBtn')
};

let documents = [];
let asking = false;
let contentViewerTrigger = null;
let deleteDialogTrigger = null;
let deleting = false;
let pendingDeleteDocument = null;
let selectedUploadFile = null;
let toastTimer = null;
let conversationId = createConversationId();
let conversationVersion = 0;
let activeRequest = null;
const pendingQuestions = [];

const DEFAULT_QUESTION_PLACEHOLDER = '向 Diving 提问，Enter 发送，Shift + Enter 换行';
const FOLLOW_UP_QUESTION_PLACEHOLDER = '可继续输入补充问题，将在当前回答结束后发送';

function createLogoImage() {
    const image = document.createElement('img');
    image.src = '/favicon.svg?v=20260911-3';
    image.alt = '';
    image.setAttribute('aria-hidden', 'true');
    return image;
}

function createAssistantAvatar() {
    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.appendChild(createLogoImage());
    return avatar;
}

function createConversationId() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
        return window.crypto.randomUUID();
    }
    return `conversation-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function showToast(message, isError = false) {
    window.clearTimeout(toastTimer);
    elements.toast.textContent = message;
    elements.toast.className = `toast show${isError ? ' error' : ''}`;
    toastTimer = window.setTimeout(() => {
        elements.toast.className = 'toast';
    }, 2600);
}

function setOperationMessage(message, isError = false) {
    elements.operationMessage.textContent = message;
    elements.operationMessage.className = `operation-message${isError ? ' error' : ''}`;
}

function openContentViewer(type, title, meta, content) {
    contentViewerTrigger = document.activeElement;
    elements.contentViewerType.textContent = type;
    elements.contentViewerTitle.textContent = title;
    elements.contentViewerMeta.textContent = meta;
    elements.contentViewerBody.replaceChildren();
    renderDocumentContent(elements.contentViewerBody, content || '暂无内容');
    elements.contentViewer.hidden = false;
    document.body.classList.add('content-viewer-open');
    elements.contentViewerClose.focus();
}

function closeContentViewer() {
    elements.contentViewer.hidden = true;
    document.body.classList.remove('content-viewer-open');
    if (contentViewerTrigger && typeof contentViewerTrigger.focus === 'function') {
        contentViewerTrigger.focus();
    }
    contentViewerTrigger = null;
}

function openDeleteDialog(documentInfo) {
    deleteDialogTrigger = document.activeElement;
    pendingDeleteDocument = documentInfo;
    elements.deleteDialogDocumentName.textContent = documentInfo.title;
    elements.deleteDialogDocumentMeta.textContent = `${documentInfo.chunkCount} 个知识切片将一并删除`;
    elements.deleteDialog.hidden = false;
    document.body.classList.add('delete-dialog-open');
    elements.deleteDialogCancel.focus();
}

function closeDeleteDialog() {
    if (deleting) {
        return;
    }
    elements.deleteDialog.hidden = true;
    document.body.classList.remove('delete-dialog-open');
    pendingDeleteDocument = null;
    if (deleteDialogTrigger && typeof deleteDialogTrigger.focus === 'function') {
        deleteDialogTrigger.focus();
    }
    deleteDialogTrigger = null;
}

function renderWelcome() {
    elements.chatList.replaceChildren();
    const welcome = document.createElement('div');
    welcome.className = 'welcome';

    const content = document.createElement('div');
    content.className = 'welcome-inner';
    const icon = document.createElement('div');
    icon.className = 'welcome-icon';
    icon.appendChild(createLogoImage());
    const title = document.createElement('h2');
    title.textContent = '你好，我是 Diving';
    const description = document.createElement('p');
    description.textContent = '你可以直接提问。当问题需要内部资料时，我会自动检索右侧知识库并标注引用。';
    const suggestions = document.createElement('div');
    suggestions.className = 'suggestions';

    ['介绍一下你能做什么', '总结知识库中的核心内容', '搜索最新的 AI 行业动态'].forEach(text => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'suggestion-button';
        button.textContent = text;
        button.addEventListener('click', () => {
            elements.question.value = text;
            updateQuestionCount();
            elements.question.focus();
        });
        suggestions.appendChild(button);
    });

    content.append(icon, title, description, suggestions);
    welcome.appendChild(content);
    elements.chatList.appendChild(welcome);
}

function clearChat() {
    conversationVersion++;
    pendingQuestions.length = 0;
    if (activeRequest) {
        activeRequest.controller.abort();
        activeRequest = null;
    }
    asking = false;
    conversationId = createConversationId();
    renderWelcome();
    updateAskButtonState();
}

function openReference(reference) {
    openContentViewer(
            '引用资料',
            reference.title,
            `原文片段 ${reference.chunkIndex + 1} · 相似度 ${reference.score.toFixed(3)}`,
            reference.content);
}

function appendReferenceList(message, references) {
    const referenceList = document.createElement('div');
    referenceList.className = 'references';
    references.forEach(reference => {
        const item = document.createElement('button');
        item.type = 'button';
        item.className = 'reference-chip';
        item.textContent = `${reference.title} · 原文片段 ${reference.chunkIndex + 1} · ${reference.score.toFixed(3)}`;
        item.title = '查看引用片段';
        item.addEventListener('click', () => openReference(reference));
        referenceList.appendChild(item);
    });
    message.appendChild(referenceList);
}

function appendMessage(type, text, references, relatedUrls) {
    const welcome = elements.chatList.querySelector('.welcome');
    if (welcome) {
        welcome.remove();
    }

    const row = document.createElement('div');
    row.className = `message-row ${type}-row`;
    const message = document.createElement('div');
    message.className = 'message';

    if (type === 'assistant') {
        row.appendChild(createAssistantAvatar());
        const referencedIndexes = renderMarkdown(message, text, {
            references,
            relatedUrls,
            onReferenceClick: openReference
        });
        if (references && references.length && referencedIndexes.size === 0) {
            appendReferenceList(message, references);
        }
    } else {
        message.textContent = text;
    }

    row.appendChild(message);
    elements.chatList.appendChild(row);
    elements.chatList.scrollTop = elements.chatList.scrollHeight;
    return row;
}

function appendLoadingMessage(afterRow) {
    const row = document.createElement('div');
    row.className = 'message-row assistant-row';
    const avatar = createAssistantAvatar();
    const message = document.createElement('div');
    message.className = 'message loading-message';
    message.setAttribute('aria-label', '正在生成回答');
    message.append(document.createElement('span'), document.createElement('span'), document.createElement('span'));
    row.append(avatar, message);
    if (afterRow && afterRow.isConnected) {
        afterRow.after(row);
    } else {
        elements.chatList.appendChild(row);
    }
    elements.chatList.scrollTop = elements.chatList.scrollHeight;
    return row;
}

function replaceLoadingMessage(loadingMessage, text, references, relatedUrls) {
    const nextRow = loadingMessage.nextSibling;
    loadingMessage.remove();
    const answerRow = appendMessage('assistant', text, references, relatedUrls);
    if (nextRow) {
        elements.chatList.insertBefore(answerRow, nextRow);
    }
    elements.chatList.scrollTop = elements.chatList.scrollHeight;
}

function updateStreamingMessage(loadingMessage, text) {
    const message = loadingMessage.querySelector('.message');
    message.classList.remove('loading-message');
    message.removeAttribute('aria-label');
    message.textContent = text;
    elements.chatList.scrollTop = elements.chatList.scrollHeight;
}

function appendPendingQuestion(questionInfo) {
    const row = appendMessage('user', questionInfo.question);
    row.classList.add('pending-row');
    const footer = document.createElement('span');
    footer.className = 'pending-message-footer';
    const status = document.createElement('span');
    status.className = 'pending-message-status';
    status.textContent = '等待当前回答完成后发送';
    const cancelButton = document.createElement('button');
    cancelButton.type = 'button';
    cancelButton.className = 'pending-cancel-button';
    cancelButton.textContent = '取消排队';
    cancelButton.addEventListener('click', () => cancelPendingQuestion(questionInfo));
    footer.append(status, cancelButton);
    row.querySelector('.message').appendChild(footer);
    return row;
}

function cancelPendingQuestion(questionInfo) {
    const questionIndex = pendingQuestions.indexOf(questionInfo);
    if (questionIndex < 0) {
        return;
    }
    pendingQuestions.splice(questionIndex, 1);
    questionInfo.row.remove();
    showToast('已取消等待中的问题');
}

function markQuestionSending(row) {
    if (!row) {
        return;
    }
    row.classList.remove('pending-row');
    row.querySelector('.pending-message-footer')?.remove();
}

function resizeQuestionInput() {
    elements.question.style.height = 'auto';
    elements.question.style.height = `${Math.min(elements.question.scrollHeight, 150)}px`;
}

function updateQuestionCount() {
    elements.questionCount.textContent = `${elements.question.value.length} / 2000`;
    resizeQuestionInput();
    updateAskButtonState();
}

function updateAskButtonState() {
    const hasQuestion = Boolean(elements.question.value.trim());
    elements.askButton.disabled = !hasQuestion;
    elements.askButtonWrapper.classList.toggle('show-empty-tip', !hasQuestion);
    elements.askButtonWrapper.classList.remove('is-loading');
    elements.askButtonTip.textContent = asking
            ? '输入补充问题，将在当前回答结束后发送' : '请输入你的问题';
    elements.stopButton.hidden = !asking;
    elements.stopButton.disabled = !activeRequest;
    elements.question.placeholder = asking
            ? FOLLOW_UP_QUESTION_PLACEHOLDER : DEFAULT_QUESTION_PLACEHOLDER;
}

function ask() {
    const question = elements.question.value.trim();
    if (!question) {
        return;
    }

    elements.question.value = '';
    updateQuestionCount();
    const questionInfo = {
        question,
        topK: Number(elements.topK.value),
        row: null
    };
    if (asking) {
        questionInfo.row = appendPendingQuestion(questionInfo);
        pendingQuestions.push(questionInfo);
        showToast('补充问题已加入等待队列');
        elements.question.focus();
        return;
    }
    sendQuestion(questionInfo);
}

async function sendQuestion(questionInfo) {
    const requestConversationId = conversationId;
    const requestConversationVersion = conversationVersion;
    asking = true;
    const userRow = questionInfo.row || appendMessage('user', questionInfo.question);
    markQuestionSending(userRow);
    const loadingMessage = appendLoadingMessage(userRow);
    const controller = new AbortController();
    activeRequest = {controller, conversationVersion: requestConversationVersion};
    updateAskButtonState();
    try {
        let answer = '';
        let result = null;
        await streamRequest('/api/chat/askStream', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                conversationId: requestConversationId,
                question: questionInfo.question,
                topK: questionInfo.topK
            }),
            signal: controller.signal
        }, event => {
            if (event.type === 'content') {
                answer += event.content || '';
                updateStreamingMessage(loadingMessage, answer);
            } else if (event.type === 'complete') {
                result = event.result;
            } else if (event.type === 'error') {
                throw new Error(event.content || '生成回答失败');
            }
        });
        if (requestConversationVersion === conversationVersion) {
            replaceLoadingMessage(loadingMessage, result?.answer || answer,
                    result?.references || [], result?.relatedUrls || []);
        }
    } catch (error) {
        if (requestConversationVersion !== conversationVersion) {
            return;
        }
        if (error.name === 'AbortError') {
            replaceLoadingMessage(loadingMessage, '已停止生成。');
        } else {
            replaceLoadingMessage(loadingMessage, '请求失败：' + error.message);
        }
    } finally {
        if (requestConversationVersion !== conversationVersion) {
            return;
        }
        if (activeRequest && activeRequest.controller === controller) {
            activeRequest = null;
        }
        asking = false;
        updateAskButtonState();
        const nextQuestion = pendingQuestions.shift();
        if (nextQuestion) {
            sendQuestion(nextQuestion);
        } else {
            elements.question.focus();
        }
    }
}

function stopAnswer() {
    if (!activeRequest) {
        return;
    }
    elements.stopButton.disabled = true;
    activeRequest.controller.abort();
}

async function uploadTextDocument() {
    const title = elements.title.value.trim();
    const content = elements.content.value.trim();
    if (!title || !content) {
        setOperationMessage('请填写文档标题和内容', true);
        return;
    }

    elements.importButton.disabled = true;
    setOperationMessage('正在切分并生成向量…');
    try {
        const data = await request('/api/knowledge/documents/text', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({title, content})
        });
        setOperationMessage(`导入成功，共生成 ${data.chunkCount} 个切片`);
        showToast('文档已加入知识库');
        elements.title.value = '';
        elements.content.value = '';
        elements.contentCount.textContent = '0 字';
        await loadDocuments();
    } catch (error) {
        setOperationMessage('导入失败：' + error.message, true);
    } finally {
        elements.importButton.disabled = false;
    }
}

function formatFileSize(size) {
    if (size < 1024) {
        return `${size} B`;
    }
    if (size < 1024 * 1024) {
        return `${(size / 1024).toFixed(1)} KB`;
    }
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function selectFile(file) {
    selectedUploadFile = file || null;
    if (!selectedUploadFile) {
        elements.selectedFile.hidden = true;
        elements.selectedFile.textContent = '';
        return;
    }
    elements.selectedFile.hidden = false;
    elements.selectedFile.textContent = `${selectedUploadFile.name} · ${formatFileSize(selectedUploadFile.size)}`;
}

async function uploadFileDocument() {
    if (!selectedUploadFile) {
        setOperationMessage('请先选择需要上传的文档', true);
        return;
    }
    if (selectedUploadFile.size > 10 * 1024 * 1024) {
        setOperationMessage('文件不能超过 10 MB', true);
        return;
    }

    const formData = new FormData();
    formData.append('file', selectedUploadFile);
    elements.uploadButton.disabled = true;
    setOperationMessage('正在解析文档并生成向量…');
    try {
        const data = await request('/api/knowledge/documents/file', {
            method: 'POST',
            body: formData
        });
        setOperationMessage(`上传成功，共生成 ${data.chunkCount} 个切片`);
        showToast('文档解析并索引完成');
        elements.file.value = '';
        selectFile(null);
        await loadDocuments();
    } catch (error) {
        setOperationMessage('上传失败：' + error.message, true);
    } finally {
        elements.uploadButton.disabled = false;
    }
}

function createActionButton(text, className, handler) {
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = text;
    if (className) {
        button.className = className;
    }
    button.addEventListener('click', async () => {
        button.disabled = true;
        try {
            await handler();
        } finally {
            button.disabled = false;
        }
    });
    return button;
}

function formatDocumentTime(value) {
    if (!value) {
        return '-';
    }
    return new Date(value).toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

async function viewDocument(documentId) {
    try {
        const documentInfo = await request(
                `/api/knowledge/documents/${encodeURIComponent(documentId)}`);
        const filename = documentInfo.filename ? ` · ${documentInfo.filename}` : '';
        openContentViewer(
                '文档原文',
                documentInfo.title,
                `${documentInfo.documentType.toUpperCase()}${filename} · ${documentInfo.chunkCount} 个切片 · 更新于 ${formatDocumentTime(documentInfo.updateTime)}`,
                documentInfo.content);
    } catch (error) {
        showToast('文档读取失败：' + error.message, true);
    }
}

function renderDocuments() {
    const keyword = elements.documentSearch.value.trim().toLowerCase();
    const filteredDocuments = documents.filter(documentInfo => {
        const name = `${documentInfo.title || ''} ${documentInfo.filename || ''}`.toLowerCase();
        return !keyword || name.includes(keyword);
    });

    elements.documentList.replaceChildren();
    if (!filteredDocuments.length) {
        const empty = document.createElement('div');
        empty.className = 'empty-state compact';
        empty.textContent = documents.length ? '没有匹配的文档' : '暂无文档，请先导入资料';
        elements.documentList.appendChild(empty);
        return;
    }

    filteredDocuments.forEach(documentInfo => {
        const item = document.createElement('article');
        item.className = 'document-item';

        const titleRow = document.createElement('div');
        titleRow.className = 'document-title-row';
        const type = document.createElement('span');
        type.className = 'document-type';
        type.textContent = documentInfo.documentType.toUpperCase();
        const name = document.createElement('button');
        name.type = 'button';
        name.className = 'document-name';
        name.textContent = documentInfo.title;
        name.title = '查看文档原文';
        name.addEventListener('click', () => viewDocument(documentInfo.documentId));
        titleRow.append(type, name);

        const meta = document.createElement('div');
        meta.className = 'document-meta';
        meta.textContent = `${documentInfo.chunkCount} 个切片 · 更新于 ${formatDocumentTime(documentInfo.updateTime)}`;

        const actions = document.createElement('div');
        actions.className = 'document-actions';
        actions.appendChild(createActionButton(
                '重新索引', '', () => reindexDocument(documentInfo.documentId)));
        actions.appendChild(createActionButton(
                '删除', 'danger', () => openDeleteDialog(documentInfo)));

        item.append(titleRow, meta, actions);
        elements.documentList.appendChild(item);
    });
}

async function loadDocuments() {
    elements.refreshDocumentsButton.disabled = true;
    try {
        documents = await request('/api/knowledge/documents');
        elements.documentCount.textContent = `${documents.length} 篇`;
        renderDocuments();
    } catch (error) {
        documents = [];
        elements.documentCount.textContent = '0 篇';
        elements.documentList.replaceChildren();
        const empty = document.createElement('div');
        empty.className = 'empty-state compact';
        empty.textContent = '文档读取失败：' + error.message;
        elements.documentList.appendChild(empty);
    } finally {
        elements.refreshDocumentsButton.disabled = false;
    }
}

async function reindexDocument(documentId) {
    try {
        const data = await request(
                `/api/knowledge/documents/${encodeURIComponent(documentId)}/reindex`,
                {method: 'POST'});
        setOperationMessage(`重新索引成功，共生成 ${data.chunkCount} 个切片`);
        showToast('文档重新索引完成');
        await loadDocuments();
    } catch (error) {
        setOperationMessage('重新索引失败：' + error.message, true);
    }
}

async function deleteDocument() {
    if (!pendingDeleteDocument || deleting) {
        return;
    }

    const documentInfo = pendingDeleteDocument;
    deleting = true;
    elements.deleteDialogBackdrop.disabled = true;
    elements.deleteDialogCancel.disabled = true;
    elements.deleteDialogClose.disabled = true;
    elements.deleteDialogConfirm.disabled = true;
    elements.deleteDialogConfirm.textContent = '正在删除…';
    try {
        await request(
                `/api/knowledge/documents/${encodeURIComponent(documentInfo.documentId)}/delete`,
                {method: 'POST'});
        elements.deleteDialog.hidden = true;
        document.body.classList.remove('delete-dialog-open');
        pendingDeleteDocument = null;
        deleteDialogTrigger = null;
        setOperationMessage('文档及其切片已删除');
        showToast('删除成功');
        await loadDocuments();
    } catch (error) {
        setOperationMessage('删除失败：' + error.message, true);
        showToast('删除失败：' + error.message, true);
    } finally {
        deleting = false;
        elements.deleteDialogBackdrop.disabled = false;
        elements.deleteDialogCancel.disabled = false;
        elements.deleteDialogClose.disabled = false;
        elements.deleteDialogConfirm.disabled = false;
        elements.deleteDialogConfirm.textContent = '确认删除';
    }
}

async function loadStatus() {
    const statusContainer = elements.status.closest('.system-status');
    try {
        const data = await request('/api/knowledge/status');
        elements.status.textContent = `向量：${data.embeddingMode} · 存储：${data.vectorStoreMode}`;
        elements.status.title = data.indexName ? `索引：${data.indexName}` : '';
        statusContainer.classList.add('ready');
    } catch (error) {
        elements.status.textContent = '服务状态读取失败';
        statusContainer.classList.remove('ready');
    }
}

function switchTab(tabName) {
    document.querySelectorAll('.tab-button').forEach(button => {
        const active = button.dataset.tab === tabName;
        button.classList.toggle('active', active);
        button.setAttribute('aria-selected', String(active));
    });
    document.querySelectorAll('.tab-content').forEach(panel => {
        const active = panel.id === tabName;
        panel.classList.toggle('active', active);
        panel.hidden = !active;
    });
    setOperationMessage('');
}

document.querySelectorAll('.tab-button').forEach(button => {
    button.addEventListener('click', () => switchTab(button.dataset.tab));
});
elements.askButton.addEventListener('click', ask);
elements.stopButton.addEventListener('click', stopAnswer);
elements.clearChatButton.addEventListener('click', clearChat);
elements.contentViewerBackdrop.addEventListener('click', closeContentViewer);
elements.contentViewerClose.addEventListener('click', closeContentViewer);
elements.deleteDialogBackdrop.addEventListener('click', closeDeleteDialog);
elements.deleteDialogCancel.addEventListener('click', closeDeleteDialog);
elements.deleteDialogClose.addEventListener('click', closeDeleteDialog);
elements.deleteDialogConfirm.addEventListener('click', deleteDocument);
elements.importButton.addEventListener('click', uploadTextDocument);
elements.uploadButton.addEventListener('click', uploadFileDocument);
elements.refreshDocumentsButton.addEventListener('click', loadDocuments);
elements.documentSearch.addEventListener('input', renderDocuments);
elements.content.addEventListener('input', () => {
    elements.contentCount.textContent = `${elements.content.value.length} 字`;
});
elements.question.addEventListener('input', updateQuestionCount);
elements.question.addEventListener('keydown', event => {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        ask();
    }
});
elements.file.addEventListener('change', () => selectFile(elements.file.files[0]));
elements.dropZone.addEventListener('dragover', event => {
    event.preventDefault();
    elements.dropZone.classList.add('dragover');
});
elements.dropZone.addEventListener('dragleave', () => elements.dropZone.classList.remove('dragover'));
elements.dropZone.addEventListener('drop', event => {
    event.preventDefault();
    elements.dropZone.classList.remove('dragover');
    selectFile(event.dataTransfer.files[0]);
});
document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !elements.deleteDialog.hidden) {
        closeDeleteDialog();
        return;
    }
    if (event.key === 'Escape' && !elements.contentViewer.hidden) {
        closeContentViewer();
    }
});

renderWelcome();
updateQuestionCount();
loadStatus();
loadDocuments();
