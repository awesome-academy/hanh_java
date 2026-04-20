/**
 * admin.js — Admin portal JS utilities
 */
function getCsrfToken() {
    return document.querySelector('input[name="_csrf"]')?.value
        || document.head.querySelector('meta[name="_csrf"]')?.content
        || '';
}

function getCsrfHeader() {
    return document.head.querySelector('meta[name="_csrf_header"]')?.content
        || 'X-CSRF-TOKEN';
}

function getHeaders(json = true) {
    const h = { 'Accept': 'application/json' };
    if (json) h['Content-Type'] = 'application/json';
    const token = getCsrfToken();
    if (token) h[getCsrfHeader()] = token;
    return h;
}

// ─── Modal utilities ─────────────────────────────────────────────────────────

/**
 * Mở modal theo ID — thêm class `.open` (admin.css dùng opacity transition).
 * admin.css: .modal-overlay { opacity: 0; pointer-events: none }
 *            .modal-overlay.open { opacity: 1; pointer-events: auto }
 * @param {string} modalId - ID của element .modal-overlay
 */
function openModal(modalId) {
    const el = document.getElementById(modalId);
    if (el) { el.classList.add('open'); document.body.style.overflow = 'hidden'; }
}

/**
 * Đóng modal — xóa class `.open`, reset error messages.
 * @param {string} modalId - ID của element .modal-overlay
 */
function closeModal(modalId) {
    const el = document.getElementById(modalId);
    if (!el) return;
    el.classList.remove('open');
    document.body.style.overflow = '';
    el.querySelectorAll('.modal-error').forEach(e => {
        e.style.display = 'none'; e.textContent = '';
    });
}

// Đóng modal khi click vào overlay (vùng tối ngoài modal-box)
document.addEventListener('click', function (e) {
    if (e.target.classList.contains('modal-overlay')) {
        closeModal(e.target.id);
    }
});

// ─── Flash message helper (không reload trang) ────────────────────────────────

function showPageFlash(message, type = 'success') {
    let flash = document.getElementById('page-flash');
    if (!flash) {
        flash = document.createElement('div');
        flash.id = 'page-flash';
        document.querySelector('.adm-content')?.prepend(flash);
    }
    flash.className = type === 'success' ? 'flash flash-success' : 'flash flash-error';
    flash.textContent = message;
    flash.style.display = 'block';
    setTimeout(() => { flash.style.opacity = '0'; }, 3000);
    setTimeout(() => { flash.style.display = 'none'; flash.style.opacity = '1'; }, 3400);
}

function showModalError(errorElId, message) {
    const el = document.getElementById(errorElId);
    if (el) { el.textContent = message; el.style.display = 'block'; }
}

// ─── User Management ──────────────────────────────────────────────────────────

/**
 * Mở modal tạo user — reset form + disabled state trước khi mở.
 */
function openCreateModal() {
    const form = document.getElementById('createUserForm');
    if (form) form.reset();
    document.getElementById('createStaffFields')?.classList.remove('visible');
    document.getElementById('createCitizenFields')?.classList.remove('visible');
    // Reset disabled state từ lần mở trước
    document.querySelectorAll('#createUserForm input[name="roles"]').forEach(cb => {
        cb.disabled = false;
        const label = cb.closest('label');
        if (label) { label.style.opacity = '1'; label.title = ''; }
    });
    openModal('createUserModal');
}

/**
 * Danh sách admin roles — dùng cho Separation of Duties check.
 */
const ADMIN_ROLES = ['STAFF', 'MANAGER', 'SUPER_ADMIN'];

/**
 * Kiểm tra xem một set roles có vi phạm Separation of Duties không.
 * CITIZEN không được kết hợp với STAFF/MANAGER/SUPER_ADMIN.
 * @param {string[]} roles - mảng tên role đã chọn
 * @returns {boolean} true nếu vi phạm
 */
function hasRoleConflict(roles) {
    const hasCitizen = roles.includes('CITIZEN');
    const hasAdmin   = roles.some(r => ADMIN_ROLES.includes(r));
    return hasCitizen && hasAdmin;
}

/**
 * Handler khi thay đổi role trong modal TẠO USER.
 *
 * Logic Separation of Duties (disable approach):
 * - Nếu CITIZEN được check → disable tất cả admin role checkboxes (và ngược lại)
 * - Đồng thời hiện/ẩn citizen/staff form sections tương ứng
 *
 * Lưu ý: SUPER_ADMIN không cần staff profile nên KHÔNG hiện staff fields.
 * Staff fields chỉ hiện khi chọn STAFF hoặc MANAGER.
 */
function handleCreateRoleChange() {
    const allChecks = Array.from(document.querySelectorAll('#createUserForm input[name="roles"]'));
    const selected  = allChecks.filter(c => c.checked).map(c => c.value);

    const hasCitizen  = selected.includes('CITIZEN');
    const hasAdmin    = selected.some(r => ADMIN_ROLES.includes(r));
    // Chỉ STAFF/MANAGER mới cần nhập staff profile — SUPER_ADMIN không cần
    const hasStaffRole = selected.some(r => ['STAFF', 'MANAGER'].includes(r));

    // Toggle citizen/staff form sections
    document.getElementById('createStaffFields')?.classList.toggle('visible', hasStaffRole);
    document.getElementById('createCitizenFields')?.classList.toggle('visible', hasCitizen);

    // Disable only unchecked conflicting checkboxes so checked ones can still be unselected
    allChecks.forEach(cb => {
        const label = cb.closest('label');
        const conflictsWithCitizen = hasCitizen && ADMIN_ROLES.includes(cb.value);
        const conflictsWithAdmin = hasAdmin && cb.value === 'CITIZEN';
        const isConflicting = conflictsWithCitizen || conflictsWithAdmin;
        cb.disabled = isConflicting && !cb.checked;
        if (cb.disabled) {
            if (label) {
                label.style.opacity = '0.4';
                label.title = conflictsWithCitizen
                    ? 'Không thể kết hợp với CITIZEN'
                    : 'Không thể kết hợp với admin roles';
            }
        } else {
            if (label) { label.style.opacity = '1'; label.title = ''; }
        }
    });
}

// Alias để không break code cũ nếu có chỗ nào gọi toggleCreateStaffFields
const toggleCreateStaffFields = handleCreateRoleChange;

/**
 * Handler khi thay đổi role trong modal CẬP NHẬT ROLES.
 * CITIZEN đã bị loại khỏi modal này, nên không cần check conflict.
 * Chỉ cần giữ function để không bị lỗi nếu có reference cũ.
 */
function handleRolesModalChange() {
    // no-op: CITIZEN không xuất hiện trong modal Roles nên không có conflict
}

/**
 * Tạo user mới qua fetch() POST đến /admin/users (session-based, có CSRF).
 * Sau khi thành công → reload page để cập nhật bảng.
 *
 * @param {Event} e - form submit event
 */
async function submitCreateUser(e) {
    e.preventDefault();
    const form = e.target;
    const fd = new FormData(form);

    const roles = fd.getAll('roles');

    // Client-side Separation of Duties guard — backend cũng enforce, đây là UX layer
    if (hasRoleConflict(roles)) {
        showModalError('createUserError',
            'Separation of Duties: CITIZEN không thể kết hợp với STAFF/MANAGER/SUPER_ADMIN.');
        return;
    }

    const data = {
        email:          fd.get('email')?.trim(),
        password:       fd.get('password'),
        fullName:       fd.get('fullName')?.trim(),
        phone:          fd.get('phone')?.trim() || null,
        roles:          roles,
        // Citizen fields
        nationalId:     fd.get('nationalId')?.trim() || null,
        // Staff fields
        staffCode:      fd.get('staffCode')?.trim() || null,
        departmentId:   fd.get('departmentId') ? parseInt(fd.get('departmentId')) : null,
        position:       fd.get('position')?.trim() || null
    };

    const submitBtn = form.querySelector('[type=submit]');
    submitBtn.disabled = true;
    submitBtn.textContent = 'Đang tạo...';

    try {
        const res = await fetch('/admin/users', {
            method: 'POST',
            credentials: 'same-origin',
            headers: getHeaders()  ,
            body: JSON.stringify(data)
        });
        const json = await res.json();
        if (!res.ok) {
            showModalError('createUserError', json.message || 'Lỗi tạo tài khoản');
            return;
        }
        closeModal('createUserModal');
        window.location.reload();
    } catch (err) {
        showModalError('createUserError', 'Lỗi kết nối. Vui lòng thử lại.');
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Tạo tài khoản';
    }
}

/**
 * Mở modal sửa user, populate form từ data-attributes của nút Edit.
 *
 * Staff fields chỉ hiện nếu user thực sự có staff profile (staffCode != null).
 * - CITIZEN: không có staffCode → ẩn staff fields
 * - SUPER_ADMIN: không có staffCode → ẩn staff fields (chỉ sửa họ tên, phone)
 * - STAFF/MANAGER: có staffCode → hiện staff fields
 */
function openEditModal(btn) {
    const d = btn.dataset;
    document.getElementById('editUserId').value       = d.userId;
    document.getElementById('editFullName').value     = d.fullName || '';
    document.getElementById('editPhone').value        = d.phone || '';
    document.getElementById('editDepartmentId').value = d.departmentId || '';
    document.getElementById('editPosition').value     = d.position || '';

    // Chỉ hiện staff fields nếu user có staff profile (staffCode không rỗng)
    const hasStaffProfile = !!(d.staffCode && d.staffCode.trim());
    document.getElementById('editStaffFields')?.classList.toggle('visible', hasStaffProfile);

    openModal('editUserModal');
}

/**
 * Cập nhật thông tin user qua fetch() PUT đến /admin/users/{id}.
 * @param {Event} e - form submit event
 */
async function submitEditUser(e) {
    e.preventDefault();
    const form = e.target;
    const userId = document.getElementById('editUserId').value;

    const data = {
        fullName:     document.getElementById('editFullName').value?.trim(),
        phone:        document.getElementById('editPhone').value?.trim() || null,
        departmentId: document.getElementById('editDepartmentId').value
                        ? parseInt(document.getElementById('editDepartmentId').value) : null,
        position:     document.getElementById('editPosition').value?.trim() || null
    };

    const submitBtn = form.querySelector('[type=submit]');
    submitBtn.disabled = true;
    submitBtn.textContent = 'Đang lưu...';

    try {
        const res = await fetch(`/admin/users/${userId}`, {
            method: 'PUT',
            credentials: 'same-origin',
            headers: getHeaders(),
            body: JSON.stringify(data)
        });
        const json = await res.json();
        if (!res.ok) {
            showModalError('editUserError', json.message || 'Lỗi cập nhật');
            return;
        }
        closeModal('editUserModal');
        // Cập nhật DOM + data-* attributes để modal Sửa hiển thị đúng data lần sau
        const u = json.data;
        const row = document.getElementById(`user-row-${userId}`);
        if (row) {
            row.querySelector('.cell-fullname').textContent = u.fullName;
            const phoneEl = row.querySelector('.cell-phone');
            if (phoneEl) phoneEl.textContent = u.phone || '';
            // Cập nhật data-* trên nút Sửa để openEditModal đọc đúng data mới
            const editBtn = row.querySelector('.bsm.be');
            if (editBtn) {
                editBtn.dataset.fullName     = u.fullName     || '';
                editBtn.dataset.phone        = u.phone        || '';
                editBtn.dataset.departmentId = u.departmentId != null ? u.departmentId : '';
                editBtn.dataset.position     = u.position     || '';
            }
        }
        showPageFlash('Cập nhật thành công!');
    } catch (err) {
        showModalError('editUserError', 'Lỗi kết nối. Vui lòng thử lại.');
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Lưu thay đổi';
    }
}

/**
 * Mở modal cập nhật roles.
 * @param {HTMLElement} btn - nút "Roles" có data-* attributes
 */
function openRolesModal(btn) {
    const d = btn.dataset;
    document.getElementById('rolesUserId').value = d.userId;
    document.getElementById('rolesUserName').textContent = d.fullName;

    // Reset + check current roles
    document.querySelectorAll('#rolesForm input[name="roles"]').forEach(cb => {
        const currentRoles = (d.currentRoles || '').split(',');
        cb.checked = currentRoles.includes(cb.value);
    });
    openModal('rolesModal');
}

/**
 * Cập nhật roles qua fetch() PUT đến /admin/users/{id}/roles.
 */
async function submitUpdateRoles(e) {
    e.preventDefault();
    const userId = document.getElementById('rolesUserId').value;
    const checked = document.querySelectorAll('#rolesForm input[name="roles"]:checked');
    const roles = Array.from(checked).map(c => c.value);

    if (roles.length === 0) {
        showModalError('rolesError', 'Phải chọn ít nhất một vai trò');
        return;
    }

    // Client-side Separation of Duties guard
    if (hasRoleConflict(roles)) {
        showModalError('rolesError',
            'Separation of Duties: CITIZEN không thể kết hợp với STAFF/MANAGER/SUPER_ADMIN.');
        return;
    }

    const submitBtn = e.target.querySelector('[type=submit]');
    submitBtn.disabled = true;

    try {
        const res = await fetch(`/admin/users/${userId}/roles`, {
            method: 'PUT',
            credentials: 'same-origin',
            headers: getHeaders(),
            body: JSON.stringify({ roles })
        });
        const json = await res.json();
        if (!res.ok) {
            showModalError('rolesError', json.message || 'Lỗi cập nhật quyền');
            return;
        }
        closeModal('rolesModal');
        window.location.reload(); // Reload để hiển thị role badges mới
    } catch (err) {
        showModalError('rolesError', 'Lỗi kết nối. Vui lòng thử lại.');
    } finally {
        submitBtn.disabled = false;
    }
}

/**
 * Khóa / Mở khóa tài khoản.
 * @param {string|number} userId - ID của user (string khi đến từ dataset)
 * @param {boolean} lock  - true = khóa, false = mở khóa
 */
async function toggleLockUser(userId, lock) {
    const action = lock ? 'khóa' : 'mở khóa';
    if (!confirm(`Bạn có chắc muốn ${action} tài khoản này?`)) return;

    try {
        const endpoint = lock ? 'lock' : 'unlock';
        const res = await fetch(`/admin/users/${userId}/${endpoint}`, {
            method: 'PUT',
            credentials: 'same-origin',
            headers: getHeaders(false) // không cần Content-Type (không có body)
        });
        const json = await res.json();
        if (!res.ok) { showPageFlash(json.message || 'Lỗi thao tác', 'error'); return; }

        // Cập nhật DOM: đổi badge trạng thái + nút lock/unlock
        const row = document.getElementById(`user-row-${userId}`);
        if (row) {
            const statusCell = row.querySelector('.cell-status');
            if (statusCell) {
                statusCell.innerHTML = lock
                    ? '<span class="pill p-red">Bị khóa</span>'
                    : '<span class="pill p-green">Hoạt động</span>';
            }
            // Đổi nút lock ↔ unlock
            const lockBtn   = row.querySelector('.btn-lock');
            const unlockBtn = row.querySelector('.btn-unlock');
            if (lockBtn)   lockBtn.style.display   = lock ? 'none' : '';
            if (unlockBtn) unlockBtn.style.display = lock ? '' : 'none';
        }
        showPageFlash(`Đã ${action} tài khoản thành công!`);
    } catch (err) {
        showPageFlash('Lỗi kết nối. Vui lòng thử lại.', 'error');
    }
}

/**
 * Xóa mềm tài khoản (is_active=false).
 * @param {string|number} userId   - ID của user (string khi đến từ dataset)
 * @param {string} fullName - Tên user (dùng trong confirm dialog)
 */
async function deleteUser(userId, fullName) {
    if (!confirm(`Xóa tài khoản "${fullName}"?\nDữ liệu hồ sơ sẽ được giữ lại.`)) return;

    try {
        const res = await fetch(`/admin/users/${userId}`, {
            method: 'DELETE',
            credentials: 'same-origin',
            headers: getHeaders(false)
        });
        const json = await res.json();
        if (!res.ok) { showPageFlash(json.message || 'Lỗi xóa tài khoản', 'error'); return; }

        // Xóa row khỏi bảng
        const row = document.getElementById(`user-row-${userId}`);
        if (row) row.remove();
        showPageFlash('Đã xóa tài khoản thành công!');
    } catch (err) {
        showPageFlash('Lỗi kết nối. Vui lòng thử lại.', 'error');
    }
}

// ─── CSV Import ───────────────────────────────────────────────────────────────

/**
 * Import CSV: upload file lên /admin/import/{type} qua fetch().
 * Sau khi xong → renderImportResult() hiển thị kết quả trong modal.
 *
 * @param {string} type - loại import: 'citizens' | 'services' | 'departments' | 'staff'
 */
async function importCsv(type) {
    const fileInput = document.getElementById(`csv-file-${type}`);
    const errorEl   = document.getElementById(`import-error-${type}`);
    const resultEl  = document.getElementById(`import-result-${type}`);
    const btn       = document.getElementById(`import-btn-${type}`);

    // Reset trạng thái cũ
    if (errorEl) { errorEl.style.display = 'none'; errorEl.textContent = ''; }
    if (resultEl) { resultEl.style.display = 'none'; resultEl.innerHTML = ''; }

    if (!fileInput || !fileInput.files.length) {
        if (errorEl) { errorEl.textContent = 'Vui lòng chọn file CSV trước khi import.'; errorEl.style.display = 'block'; }
        return;
    }

    const file = fileInput.files[0];
    if (!file.name.toLowerCase().endsWith('.csv')) {
        if (errorEl) { errorEl.textContent = 'Chỉ chấp nhận file .csv'; errorEl.style.display = 'block'; }
        return;
    }

    const formData = new FormData();
    formData.append('file', file);

    if (btn) { btn.disabled = true; btn.textContent = 'Đang import...'; }

    try {
        const csrf  = getCsrfToken();
        const headers = { 'Accept': 'application/json' };
        if (csrf) headers[getCsrfHeader()] = csrf;

        const res  = await fetch(`/admin/import/${type}`, {
            method: 'POST',
            credentials: 'same-origin',
            headers,
            body: formData
        });
        const json = await res.json();

        if (!res.ok) {
            if (errorEl) { errorEl.textContent = json.message || 'Lỗi import. Vui lòng thử lại.'; errorEl.style.display = 'block'; }
            return;
        }
        // Hiển thị kết quả
        if (resultEl && json.data) {
            renderImportResult(resultEl, json.data);
            resultEl.style.display = 'block';
        }
    } catch (err) {
        if (errorEl) { errorEl.textContent = 'Lỗi kết nối. Vui lòng thử lại.'; errorEl.style.display = 'block'; }
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = '↑ Import'; }
    }
}

/**
 * Render kết quả import vào container element.
 * Hiển thị: summary (total/success/failed) + bảng lỗi từng row nếu có.
 *
 * @param {HTMLElement} container - element để render vào
 * @param {Object} result - { total, success, failed, errors: [{row, field, message}] }
 */
function renderImportResult(container, result) {
    const isSuccess = result.failed === 0;
    const summaryColor = isSuccess ? 'var(--success)' : (result.success > 0 ? 'var(--warn)' : 'var(--danger)');

    let html = `
      <div style="border:1px solid ${summaryColor};border-radius:8px;padding:12px;background:${isSuccess ? '#f0fdf4' : '#fffbeb'}">
        <p style="margin:0 0 4px;font-weight:700;color:${summaryColor}">
          ${isSuccess ? '✓ Import thành công' : '⚠ Import hoàn thành với lỗi'}
        </p>
        <p style="margin:0;font-size:13px">
          Tổng cộng: <strong>${result.total}</strong> rows —
          <span style="color:var(--success)">✓ ${result.success} thành công</span>
          ${result.failed > 0 ? ` · <span style="color:var(--danger)">✗ ${result.failed} lỗi</span>` : ''}
        </p>
      </div>`;

    // Bảng lỗi chi tiết nếu có
    if (result.errors && result.errors.length > 0) {
        html += `
          <div style="margin-top:12px;max-height:200px;overflow-y:auto">
            <table style="width:100%;border-collapse:collapse;font-size:12px">
              <thead>
                <tr style="background:var(--light)">
                  <th style="padding:6px 8px;text-align:left;border-bottom:1px solid var(--border)">Row</th>
                  <th style="padding:6px 8px;text-align:left;border-bottom:1px solid var(--border)">Field</th>
                  <th style="padding:6px 8px;text-align:left;border-bottom:1px solid var(--border)">Lỗi</th>
                </tr>
              </thead>
              <tbody>
                ${result.errors.map(e => `
                  <tr>
                    <td style="padding:5px 8px;border-bottom:1px solid var(--border);color:var(--muted)">${e.row}</td>
                    <td style="padding:5px 8px;border-bottom:1px solid var(--border);font-family:var(--mono);color:var(--info)">${e.field}</td>
                    <td style="padding:5px 8px;border-bottom:1px solid var(--border);color:var(--danger)">${e.message}</td>
                  </tr>`).join('')}
              </tbody>
            </table>
          </div>`;
    }

    container.innerHTML = html;
}

/**
 * Reset and open import modal for CSV import (citizens, services, departments, staff)
 * @param {string} type - import type (e.g. 'citizens')
 */
function openImportModal(type) {
    const modalId = 'import-modal-' + type;
    const modal = document.getElementById(modalId);
    if (!modal) return;
    // Reset file input
    const fileInput = document.getElementById('csv-file-' + type);
    if (fileInput) fileInput.value = '';
    // Reset error
    const errorDiv = document.getElementById('import-error-' + type);
    if (errorDiv) {
        errorDiv.style.display = 'none';
        errorDiv.textContent = '';
    }
    // Reset result
    const resultDiv = document.getElementById('import-result-' + type);
    if (resultDiv) {
        resultDiv.style.display = 'none';
        resultDiv.textContent = '';
    }
    openModal(modalId);
}
