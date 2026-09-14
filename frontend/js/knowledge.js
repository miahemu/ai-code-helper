import {elements} from './elements.js?v=20260914-9';
import {setWebSearchAvailable} from './commands.js?v=20260914-6';
import {
    deleteDocument as deleteDocumentRequest,
    getDocument,
    getDocuments,
    getSystemStatus,
    importTextDocument,
    reindexDocument as reindexDocumentRequest,
    uploadDocument
} from './route.js?v=20260914-2';
import {openContentViewer, showToast} from './ui.js?v=20260914-2';

let documents = [];
let deleteDialogTrigger = null;
let deleting = false;
let pendingDeleteDocument = null;
let selectedUploadFile = null;

function setOperationMessage(message, isError = false) {
    elements.operationMessage.textContent = message;
    elements.operationMessage.className = `operation-message${isError ? ' error' : ''}`;
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

async function uploadText() {
    const title = elements.title.value.trim();
    const content = elements.content.value.trim();
    if (!title || !content) {
        setOperationMessage('请填写文档标题和内容', true);
        return;
    }

    elements.importButton.disabled = true;
    setOperationMessage('正在切分并生成向量…');
    try {
        const data = await importTextDocument(title, content);
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

async function uploadFile() {
    if (!selectedUploadFile) {
        setOperationMessage('请先选择需要上传的文档', true);
        return;
    }
    if (selectedUploadFile.size > 10 * 1024 * 1024) {
        setOperationMessage('文件不能超过 10 MB', true);
        return;
    }

    elements.uploadButton.disabled = true;
    setOperationMessage('正在解析文档并生成向量…');
    try {
        const data = await uploadDocument(selectedUploadFile);
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
        const documentInfo = await getDocument(documentId);
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
                '重新索引', '', () => reindex(documentInfo.documentId)));
        actions.appendChild(createActionButton(
                '删除', 'danger', () => openDeleteDialog(documentInfo)));

        item.append(titleRow, meta, actions);
        elements.documentList.appendChild(item);
    });
}

async function loadDocuments() {
    elements.refreshDocumentsButton.disabled = true;
    try {
        documents = await getDocuments();
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

async function reindex(documentId) {
    try {
        const data = await reindexDocumentRequest(documentId);
        setOperationMessage(`重新索引成功，共生成 ${data.chunkCount} 个切片`);
        showToast('文档重新索引完成');
        await loadDocuments();
    } catch (error) {
        setOperationMessage('重新索引失败：' + error.message, true);
    }
}

async function removeDocument() {
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
        await deleteDocumentRequest(documentInfo.documentId);
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
        const data = await getSystemStatus();
        const webSearchAvailable = data.webSearchEnabled === true;
        setWebSearchAvailable(webSearchAvailable);
        elements.webSearchButton.hidden = !webSearchAvailable;
        if (!webSearchAvailable) {
            elements.webSearchButton.setAttribute('aria-pressed', 'false');
        }
        elements.status.textContent = `向量：${data.embeddingMode} · 存储：${data.vectorStoreMode}`;
        elements.status.title = data.indexName ? `索引：${data.indexName}` : '';
        statusContainer.classList.add('ready');
    } catch (error) {
        setWebSearchAvailable(false);
        elements.webSearchButton.hidden = true;
        elements.webSearchButton.setAttribute('aria-pressed', 'false');
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

export function initKnowledge() {
    document.querySelectorAll('.tab-button').forEach(button => {
        button.addEventListener('click', () => switchTab(button.dataset.tab));
    });
    elements.deleteDialogBackdrop.addEventListener('click', closeDeleteDialog);
    elements.deleteDialogCancel.addEventListener('click', closeDeleteDialog);
    elements.deleteDialogClose.addEventListener('click', closeDeleteDialog);
    elements.deleteDialogConfirm.addEventListener('click', removeDocument);
    elements.importButton.addEventListener('click', uploadText);
    elements.uploadButton.addEventListener('click', uploadFile);
    elements.refreshDocumentsButton.addEventListener('click', loadDocuments);
    elements.documentSearch.addEventListener('input', renderDocuments);
    elements.content.addEventListener('input', () => {
        elements.contentCount.textContent = `${elements.content.value.length} 字`;
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
        }
    });

    loadStatus();
    loadDocuments();
}
