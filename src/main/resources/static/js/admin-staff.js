/**
 * admin-staff.js — JS cho trang Quản lý cán bộ (/admin/staff)
 * Phụ thuộc: admin.js (getHeaders, openModal, closeModal, showPageFlash, showModalError)
 */

function openEditStaffModal(btn) {
    const d = btn.dataset;
    document.getElementById('editStaffId').value         = d.staffId;
    document.getElementById('editStaffName').textContent = d.fullName || '';
    document.getElementById('editStaffDeptId').value     = d.departmentId || '';
    document.getElementById('editStaffPosition').value   = d.position || '';
    document.getElementById('editStaffAvailable').value  = d.available || 'true';
    openModal('editStaffModal');
}

async function submitEditStaff(e) {
    e.preventDefault();
    const staffId = document.getElementById('editStaffId').value;
    const data = {
        departmentId: parseInt(document.getElementById('editStaffDeptId').value),
        position:     document.getElementById('editStaffPosition').value?.trim() || null,
        available:    document.getElementById('editStaffAvailable').value === 'true'
    };
    const btn = e.target.querySelector('[type=submit]');
    btn.disabled = true; btn.textContent = 'Đang lưu...';
    try {
        const res = await fetch(`/admin/staff/${staffId}`, {
            method: 'PUT', credentials: 'same-origin',
            headers: getHeaders(), body: JSON.stringify(data)
        });
        const json = await res.json();
        if (!res.ok) { showModalError('editStaffError', json.message || 'Lỗi cập nhật'); return; }
        closeModal('editStaffModal');
        showPageFlash('Cập nhật cán bộ thành công!');
        // Cập nhật DOM + data-* attributes để modal Sửa hiển thị đúng data lần sau
        const row = document.getElementById(`staff-row-${staffId}`);
        const s = json.data;
        if (row) {
            row.cells[2].textContent = s.departmentName || '—';
            row.cells[3].textContent = s.position || '—';
            row.cells[5].innerHTML = s.available
                ? '<span class="pill p-green">Sẵn sàng</span>'
                : '<span class="pill p-amber">Nghỉ phép</span>';
            const editBtn = row.querySelector('.bsm.be');
            if (editBtn) {
                editBtn.dataset.departmentId = s.departmentId != null ? s.departmentId : '';
                editBtn.dataset.position     = s.position     || '';
                editBtn.dataset.available    = s.available;
            }
        }
    } catch { showModalError('editStaffError', 'Lỗi kết nối. Vui lòng thử lại.'); }
    finally { btn.disabled = false; btn.textContent = 'Lưu thay đổi'; }
}

