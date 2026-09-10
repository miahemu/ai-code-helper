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
 * 兼容模型偶尔返回的转义符、HTML 空格和挤在同一行的表格内容。
 */
function normalizeMarkdown(markdown) {
    const content = String(markdown || '')
            .replace(/\r\n?/g, '\n')
            .replace(/(?:&#x20;|&#32;|&nbsp;)/gi, ' ')
            .replace(/\\([\\*_`#>|-])/g, '$1')
            .replace(/\*{4,}/g, '**');

    return content.split('\n').map(line => {
        if (/\|\s+\|\s*:?-{3,}/.test(line)) {
            return line.replace(/\|\s+\|/g, '|\n|');
        }
        return line;
    }).join('\n').replace(/([。；;])\s+[-*+]\s+/g, '$1\n- ');
}

function parseTableRow(line) {
    let value = line.trim();
    if (value.startsWith('|')) {
        value = value.substring(1);
    }
    if (value.endsWith('|')) {
        value = value.substring(0, value.length - 1);
    }
    return value.split('|').map(cell => cell.trim());
}

function isTableSeparator(line) {
    const cells = parseTableRow(line);
    return cells.length > 0 && cells.every(cell => /^:?-{3,}:?$/.test(cell));
}

function getTableAlignment(separator) {
    const left = separator.startsWith(':');
    const right = separator.endsWith(':');
    if (left && right) {
        return 'center';
    }
    return right ? 'right' : 'left';
}

function isDocumentHeading(line) {
    if (line.length > 32 || /[。！？；;]$/.test(line)) {
        return false;
    }
    return /^(?:第[一二三四五六七八九十百\d]+[章节篇部分]|[一二三四五六七八九十百]+[、.．]|[（(]?[一二三四五六七八九十百\d]+[)）、.．])/.test(line)
            || /(?:规范|流程|要求|说明|注意事项|操作建议)$/.test(line);
}

/**
 * 将 Office、PDF 提取出的纯文本整理成适合阅读的 Markdown 结构。
 */
function normalizeDocumentContent(content) {
    return normalizeMarkdown(content).split('\n').map(line => {
        const trimmed = line.trim();
        if (!trimmed || /^(?:#{1,3}|[-*+]\s|\d+\.\s|>|```|\|)/.test(trimmed)) {
            return line;
        }
        if (/^[•●▪◦]\s*/.test(trimmed)) {
            return `- ${trimmed.replace(/^[•●▪◦]\s*/, '')}`;
        }
        if (isDocumentHeading(trimmed)) {
            return `### ${trimmed}`;
        }
        return line;
    }).join('\n');
}

function appendTable(container, lines, startIndex) {
    const headers = parseTableRow(lines[startIndex]);
    const separators = parseTableRow(lines[startIndex + 1]);
    const wrapper = document.createElement('div');
    wrapper.className = 'md-table-wrapper';
    const table = document.createElement('table');
    const head = document.createElement('thead');
    const headRow = document.createElement('tr');

    headers.forEach((header, index) => {
        const cell = document.createElement('th');
        cell.style.textAlign = getTableAlignment(separators[index] || '---');
        appendInlineMarkdown(cell, header);
        headRow.appendChild(cell);
    });
    head.appendChild(headRow);
    table.appendChild(head);

    const body = document.createElement('tbody');
    let endIndex = startIndex + 2;
    while (endIndex < lines.length && lines[endIndex].trim()
            && lines[endIndex].includes('|')) {
        const values = parseTableRow(lines[endIndex]);
        const row = document.createElement('tr');
        headers.forEach((header, index) => {
            const cell = document.createElement('td');
            cell.style.textAlign = getTableAlignment(separators[index] || '---');
            appendInlineMarkdown(cell, values[index] || '');
            row.appendChild(cell);
        });
        body.appendChild(row);
        endIndex++;
    }
    table.appendChild(body);
    wrapper.appendChild(table);
    container.appendChild(wrapper);
    return endIndex - 1;
}

/**
 * 安全渲染模型返回的常用 Markdown，不执行其中的 HTML。
 *
 * @param {HTMLElement} container 渲染容器
 * @param {string} markdown Markdown 文本
 */
export function renderMarkdown(container, markdown) {
    const content = normalizeMarkdown(markdown);
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

    for (let lineIndex = 0; lineIndex < lines.length; lineIndex++) {
        const line = lines[lineIndex];
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
            continue;
        }
        if (inCodeBlock) {
            codeLines.push(line);
            continue;
        }
        if (!line.trim()) {
            flushParagraph();
            closeList();
            continue;
        }

        if (line.includes('|') && lineIndex + 1 < lines.length
                && isTableSeparator(lines[lineIndex + 1])) {
            flushParagraph();
            closeList();
            lineIndex = appendTable(container, lines, lineIndex);
            continue;
        }

        if (/^\s*(?:-{3,}|_{3,}|\*{3,})\s*$/.test(line)) {
            flushParagraph();
            closeList();
            container.appendChild(document.createElement('hr'));
            continue;
        }

        const heading = line.match(/^(#{1,3})\s+(.+)$/);
        if (heading) {
            flushParagraph();
            closeList();
            const element = document.createElement('div');
            element.className = `md-heading md-heading-${heading[1].length}`;
            appendInlineMarkdown(element, heading[2]);
            container.appendChild(element);
            continue;
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
            continue;
        }

        const quote = line.match(/^>\s?(.*)$/);
        if (quote) {
            flushParagraph();
            closeList();
            const element = document.createElement('blockquote');
            appendInlineMarkdown(element, quote[1]);
            container.appendChild(element);
            continue;
        }

        closeList();
        paragraph.push(line);
    }
    flushParagraph();

    if (inCodeBlock && codeLines.length) {
        const pre = document.createElement('pre');
        const code = document.createElement('code');
        code.textContent = codeLines.join('\n');
        pre.appendChild(code);
        container.appendChild(pre);
    }
}

/**
 * 以阅读模式渲染文档原文或引用片段，兼容纯文本和 Markdown 内容。
 *
 * @param {HTMLElement} container 渲染容器
 * @param {string} content 文档或片段内容
 */
export function renderDocumentContent(container, content) {
    renderMarkdown(container, normalizeDocumentContent(content));
}
