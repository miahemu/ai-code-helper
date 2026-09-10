import {request} from './api.js';
import {renderMarkdown} from './markdown.js';

const elements = {
    askButton: document.getElementById('askBtn'),
    askButtonTip: document.getElementById('askButtonTip'),
    askButtonWrapper: document.getElementById('askButtonWrapper'),
    chatList: document.getElementById('chatList'),
    clearChatButton: document.getElementById('clearChatBtn'),
    content: document.getElementById('content'),
    contentCount: document.getElementById('contentCount'),
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
    title: document.getElementById('title'),
    toast: document.getElementById('toast'),
    topK: document.getElementById('topK'),
    uploadButton: document.getElementById('uploadBtn')
};

let documents = [];
let asking = false;
let selectedUploadFile = null;
let toastTimer = null;

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

function renderWelcome() {
    elements.chatList.replaceChildren();
    const welcome = document.createElement('div');
    welcome.className = 'welcome';

    const content = document.createElement('div');
    content.className = 'welcome-inner';
    const icon = document.createElement('div');
    icon.className = 'welcome-icon';
    icon.textContent = '✦';
    const title = document.createElement('h2');
    title.textContent = '从你的资料中快速找到答案';
    const description = document.createElement('p');
    description.textContent = '先在右侧导入文档，然后直接提问。回答会标注使用到的知识片段，便于核对。';
    const suggestions = document.createElement('div');
    suggestions.className = 'suggestions';

    ['总结文档的核心内容', '列出关键规则和注意事项', '根据资料给出操作建议'].forEach(text => {
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

function appendMessage(type, text, references) {
    const welcome = elements.chatList.querySelector('.welcome');
    if (welcome) {
        welcome.remove();
    }

    const row = document.createElement('div');
    row.className = `message-row ${type}-row`;
    const message = document.createElement('div');
    message.className = 'message';

    if (type === 'assistant') {
        const avatar = document.createElement('div');
        avatar.className = 'avatar';
        avatar.textContent = 'AI';
        row.appendChild(avatar);
        renderMarkdown(message, text);
    } else {
        message.textContent = text;
    }

    if (references && references.length) {
        const referenceList = document.createElement('div');
        referenceList.className = 'references';
        references.forEach(reference => {
            const item = document.createElement('span');
            item.className = 'reference-chip';
            item.textContent = `${reference.title} · 片段 ${reference.chunkIndex + 1} · ${reference.score.toFixed(3)}`;
            item.title = reference.content || '';
            referenceList.appendChild(item);
        });
        message.appendChild(referenceList);
    }

    row.appendChild(message);
    elements.chatList.appendChild(row);
    elements.chatList.scrollTop = elements.chatList.scrollHeight;
    return row;
}

function appendLoadingMessage() {
    const row = document.createElement('div');
    row.className = 'message-row assistant-row';
    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.textContent = 'AI';
    const message = document.createElement('div');
    message.className = 'message loading-message';
    message.setAttribute('aria-label', '正在生成回答');
    message.append(document.createElement('span'), document.createElement('span'), document.createElement('span'));
    row.append(avatar, message);
    elements.chatList.appendChild(row);
    elements.chatList.scrollTop = elements.chatList.scrollHeight;
    return row;
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
    elements.askButton.disabled = asking || !hasQuestion;
    elements.askButtonWrapper.classList.toggle('show-empty-tip', !asking && !hasQuestion);
    elements.askButtonWrapper.classList.toggle('is-loading', asking);
    elements.askButtonTip.textContent = asking ? '正在生成回答' : '请输入你的问题';
}

async function ask() {
    const question = elements.question.value.trim();
    if (!question || asking) {
        return;
    }

    asking = true;
    appendMessage('user', question);
    elements.question.value = '';
    updateQuestionCount();
    const loadingMessage = appendLoadingMessage();
    try {
        const data = await request('/api/chat/ask', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({question, topK: Number(elements.topK.value)})
        });
        loadingMessage.remove();
        appendMessage('assistant', data.answer, data.references);
    } catch (error) {
        loadingMessage.remove();
        appendMessage('assistant', '请求失败：' + error.message);
    } finally {
        asking = false;
        updateAskButtonState();
        elements.question.focus();
    }
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
        const name = document.createElement('div');
        name.className = 'document-name';
        name.textContent = documentInfo.title;
        name.title = documentInfo.filename || documentInfo.title;
        titleRow.append(type, name);

        const meta = document.createElement('div');
        meta.className = 'document-meta';
        meta.textContent = `${documentInfo.chunkCount} 个切片 · 更新于 ${formatDocumentTime(documentInfo.updateTime)}`;

        const actions = document.createElement('div');
        actions.className = 'document-actions';
        actions.appendChild(createActionButton(
                '重新索引', '', () => reindexDocument(documentInfo.documentId)));
        actions.appendChild(createActionButton(
                '删除', 'danger', () => deleteDocument(documentInfo.documentId, documentInfo.title)));

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

async function deleteDocument(documentId, title) {
    if (!window.confirm(`确认删除“${title}”及其全部切片吗？此操作不可撤销。`)) {
        return;
    }
    try {
        await request(`/api/knowledge/documents/${encodeURIComponent(documentId)}`, {method: 'DELETE'});
        setOperationMessage('文档及其切片已删除');
        showToast('删除成功');
        await loadDocuments();
    } catch (error) {
        setOperationMessage('删除失败：' + error.message, true);
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
elements.clearChatButton.addEventListener('click', renderWelcome);
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

renderWelcome();
updateQuestionCount();
loadStatus();
loadDocuments();
