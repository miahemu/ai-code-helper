/**
 * 向节点中追加加粗、斜体和行内代码等 Markdown 内容。
 */
function appendInlineMarkdown(container, text) {
    const pattern = /(\*\*[^*\n]+\*\*|`[^`\n]+`|\*[^*\n]+\*)/g;
    let lastIndex = 0;
    let match;
    while ((match = pattern.exec(text)) !== null) {
        container.appendChild(document.createTextNode(text.substring(lastIndex, match.index)));
        const token = match[0];
        const tagName = token.startsWith('`') ? 'code' : token.startsWith('**') ? 'strong' : 'em';
        const element = document.createElement(tagName);
        element.textContent = token.startsWith('**') ? token.slice(2, -2) : token.slice(1, -1);
        container.appendChild(element);
        lastIndex = pattern.lastIndex;
    }
    container.appendChild(document.createTextNode(text.substring(lastIndex)));
}

/**
 * 安全渲染模型返回的常用 Markdown，不执行其中的 HTML。
 *
 * @param {HTMLElement} container 渲染容器
 * @param {string} markdown Markdown 文本
 */
export function renderMarkdown(container, markdown) {
    const content = String(markdown || '')
            .replace(/\r\n/g, '\n')
            .replace(/\\([*_`#>-])/g, '$1')
            .replace(/\*{3,}/g, '**');
    const lines = content.split('\n');
    let list = null;
    let listType = '';
    let paragraph = [];
    let codeLines = [];
    let inCodeBlock = false;

    function flushParagraph() {
        if (!paragraph.length) {
            return;
        }
        const element = document.createElement('div');
        element.className = 'md-paragraph';
        appendInlineMarkdown(element, paragraph.join('\n'));
        container.appendChild(element);
        paragraph = [];
    }

    function closeList() {
        list = null;
        listType = '';
    }

    lines.forEach(line => {
        if (line.trim().startsWith('```')) {
            flushParagraph();
            closeList();
            if (inCodeBlock) {
                const pre = document.createElement('pre');
                const code = document.createElement('code');
                code.textContent = codeLines.join('\n');
                pre.appendChild(code);
                container.appendChild(pre);
                codeLines = [];
            }
            inCodeBlock = !inCodeBlock;
            return;
        }
        if (inCodeBlock) {
            codeLines.push(line);
            return;
        }
        if (!line.trim()) {
            flushParagraph();
            closeList();
            return;
        }

        const heading = line.match(/^(#{1,3})\s+(.+)$/);
        if (heading) {
            flushParagraph();
            closeList();
            const element = document.createElement('div');
            element.className = `md-heading md-heading-${heading[1].length}`;
            appendInlineMarkdown(element, heading[2]);
            container.appendChild(element);
            return;
        }

        const unorderedItem = line.match(/^[-*+]\s+(.+)$/);
        const orderedItem = line.match(/^\d+\.\s+(.+)$/);
        if (unorderedItem || orderedItem) {
            flushParagraph();
            const currentType = orderedItem ? 'ol' : 'ul';
            if (!list || listType !== currentType) {
                closeList();
                list = document.createElement(currentType);
                listType = currentType;
                container.appendChild(list);
            }
            const item = document.createElement('li');
            appendInlineMarkdown(item, (orderedItem || unorderedItem)[1]);
            list.appendChild(item);
            return;
        }

        const quote = line.match(/^>\s?(.*)$/);
        if (quote) {
            flushParagraph();
            closeList();
            const element = document.createElement('blockquote');
            appendInlineMarkdown(element, quote[1]);
            container.appendChild(element);
            return;
        }

        closeList();
        paragraph.push(line);
    });
    flushParagraph();

    if (inCodeBlock && codeLines.length) {
        const pre = document.createElement('pre');
        const code = document.createElement('code');
        code.textContent = codeLines.join('\n');
        pre.appendChild(code);
        container.appendChild(pre);
    }
}
