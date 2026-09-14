import {request, streamRequest} from './api.js?v=20260914-2';

const CHAT_BASE_URL = '/api/chat';
const KNOWLEDGE_BASE_URL = '/api/knowledge';

export function streamChat(data, signal, onMessage) {
    return streamRequest(`${CHAT_BASE_URL}/askStream`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(data),
        signal
    }, onMessage);
}

export function chat(data, signal) {
    return request(`${CHAT_BASE_URL}/ask`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(data),
        signal
    });
}

export function importTextDocument(title, content) {
    return request(`${KNOWLEDGE_BASE_URL}/documents/text`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({title, content})
    });
}

export function uploadDocument(file) {
    const formData = new FormData();
    formData.append('file', file);
    return request(`${KNOWLEDGE_BASE_URL}/documents/file`, {
        method: 'POST',
        body: formData
    });
}

export function getDocuments() {
    return request(`${KNOWLEDGE_BASE_URL}/documents`);
}

export function getDocument(documentId) {
    return request(`${KNOWLEDGE_BASE_URL}/documents/${encodeURIComponent(documentId)}`);
}

export function reindexDocument(documentId) {
    return request(`${KNOWLEDGE_BASE_URL}/documents/${encodeURIComponent(documentId)}/reindex`, {
        method: 'POST'
    });
}

export function deleteDocument(documentId) {
    return request(`${KNOWLEDGE_BASE_URL}/documents/${encodeURIComponent(documentId)}/delete`, {
        method: 'POST'
    });
}

export function getSystemStatus() {
    return request(`${KNOWLEDGE_BASE_URL}/status`);
}
