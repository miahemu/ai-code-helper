import {elements} from './elements.js?v=20260914-2';
import {renderDocumentContent} from './markdown.js?v=20260911-3';

let toastTimer = null;
let contentViewerTrigger = null;

export function createLogoImage() {
    const image = document.createElement('img');
    image.src = '/favicon.svg?v=20260911-3';
    image.alt = '';
    image.setAttribute('aria-hidden', 'true');
    return image;
}

export function showToast(message, isError = false) {
    window.clearTimeout(toastTimer);
    elements.toast.textContent = message;
    elements.toast.className = `toast show${isError ? ' error' : ''}`;
    toastTimer = window.setTimeout(() => {
        elements.toast.className = 'toast';
    }, 2600);
}

export function openContentViewer(type, title, meta, content) {
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

export function initUi() {
    elements.contentViewerBackdrop.addEventListener('click', closeContentViewer);
    elements.contentViewerClose.addEventListener('click', closeContentViewer);
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !elements.contentViewer.hidden) {
            closeContentViewer();
        }
    });
}
