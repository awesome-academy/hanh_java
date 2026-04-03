/**
 * main.js — Utilities dùng chung cho cả client và admin layout.
 *
 * Functions:
 *  - showToast(message, type)      — hiện toast notification tạm thời
 *  - confirmAction(message)        — confirm dialog, trả về boolean
 *  - preventDoubleSubmit(formId)   — disable submit button sau click đầu
 *  - autoHideFlash()               — tự ẩn flash message sau 4s
 */

'use strict';

// ── Toast notification ────────────────────────────────────────────────
/**
 * Hiển thị toast message góc phải bên dưới, tự động ẩn sau 3s.
 * @param {string} message - Nội dung thông báo
 * @param {'success'|'error'|'warn'} type - Loại thông báo
 */
function showToast(message, type = 'success') {
    const existing = document.getElementById('toast-container');
    if (!existing) {
        const container = document.createElement('div');
        container.id = 'toast-container';
        container.style.cssText = `
            position:fixed;bottom:24px;right:24px;z-index:9999;
            display:flex;flex-direction:column;gap:8px;
        `;
        document.body.appendChild(container);
    }

    const colors = {
        success: { bg: '#DCFCE7', border: '#86EFAC', text: '#166534' },
        error:   { bg: '#FEE2E2', border: '#FCA5A5', text: '#991B1B' },
        warn:    { bg: '#FEF9C3', border: '#FDE047', text: '#854D0E' },
    };
    const c = colors[type] || colors.success;

    const toast = document.createElement('div');
    toast.style.cssText = `
        padding:12px 16px;border-radius:8px;font-size:13.5px;font-weight:500;
        background:${c.bg};border:1px solid ${c.border};color:${c.text};
        box-shadow:0 4px 12px rgba(0,0,0,.1);max-width:320px;
        animation:fadeIn .2s ease;
    `;
    toast.textContent = message;

    document.getElementById('toast-container').appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
}

// ── Confirm dialog ────────────────────────────────────────────────────
/**
 * Hiện confirm dialog. Dùng thay vì window.confirm để có thể custom sau.
 * @param {string} message - Nội dung câu hỏi confirm
 * @returns {boolean}
 */
function confirmAction(message) {
    return window.confirm(message);
}

// ── Prevent double-submit ─────────────────────────────────────────────
/**
 * Disable submit button sau khi form được submit lần đầu.
 * Ngăn citizen bấm nộp nhiều lần liên tiếp.
 * @param {string} formId - ID của form element
 */
function preventDoubleSubmit(formId) {
    const form = document.getElementById(formId);
    if (!form) return;

    form.addEventListener('submit', function () {
        const btn = form.querySelector('[type="submit"]');
        if (btn) {
            btn.disabled = true;
            btn.textContent = 'Đang xử lý...';
        }
    });
}

// ── Auto-hide flash messages ──────────────────────────────────────────
/**
 * Tự động ẩn flash message (success/error/warn) sau 4 giây.
 * Chạy khi DOM sẵn sàng.
 */
function autoHideFlash() {
    const flash = document.getElementById('flash-msg');
    if (flash) {
        setTimeout(() => {
            flash.style.transition = 'opacity .5s';
            flash.style.opacity = '0';
            setTimeout(() => flash.remove(), 500);
        }, 4000);
    }
}

// ── CSS animation ─────────────────────────────────────────────────────
(function injectStyles() {
    const style = document.createElement('style');
    style.textContent = `
        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(8px); }
            to   { opacity: 1; transform: translateY(0); }
        }
    `;
    document.head.appendChild(style);
})();

// ── Init ──────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', function () {
    autoHideFlash();
});

