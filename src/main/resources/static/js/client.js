/**
 * client.js — Client-specific JS utilities
 * Loaded only in client layout.
 */

/**
 * File upload preview — hiển thị danh sách file đã chọn trước khi submit.
 *
 * @param {HTMLInputElement} input  - file input element
 * @param {HTMLElement}      listEl - ul element để render preview items
 */
function previewFiles(input, listEl) {
    listEl.innerHTML = '';
    const files = Array.from(input.files);
    if (!files.length) return;

    const MB = 1_048_576;
    const KB = 1_024;

    files.forEach((file, idx) => {
        const size = file.size >= MB
            ? (file.size / MB).toFixed(1) + ' MB'
            : file.size >= KB
            ? Math.round(file.size / KB) + ' KB'
            : file.size + ' B';

        const icon = getFileIcon(file.name);

        const li = document.createElement('li');
        li.className = 'file-preview-item';
        li.dataset.index = idx;
        li.innerHTML = `
            <span>${icon}</span>
            <span class="fp-name" title="${escHtml(file.name)}">${escHtml(file.name)}</span>
            <span class="fp-size">${size}</span>
            <button type="button" class="fp-rm" title="Xoá" onclick="removeFilePreview(this, '${input.id}')">×</button>
        `;
        listEl.appendChild(li);
    });
}

/**
 * Xoá file khỏi preview list và cập nhật FileList của input.
 * HTML input.files là readonly nên cần dùng DataTransfer.
 */
function removeFilePreview(btn, inputId) {
    const li = btn.closest('.file-preview-item');
    const idx = parseInt(li.dataset.index, 10);
    const input = document.getElementById(inputId);

    const dt = new DataTransfer();
    Array.from(input.files).forEach((f, i) => {
        if (i !== idx) dt.items.add(f);
    });
    input.files = dt.files;

    // Re-render để cập nhật index
    const listEl = li.closest('.file-preview-list');
    previewFiles(input, listEl);
}

function getFileIcon(name) {
    const ext = name.split('.').pop().toLowerCase();
    const map = { pdf: '📄', jpg: '🖼️', jpeg: '🖼️', png: '🖼️', docx: '📝' };
    return map[ext] || '📎';
}

function escHtml(str) {
    return str.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

