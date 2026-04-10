/**
 * admin-dept.js — JS cho trang Quản lý phòng ban (/admin/departments)
 * Phụ thuộc: admin.js (getHeaders, openModal, closeModal, showPageFlash, showModalError)
 */

function openCreateDeptModal() {
    document.getElementById('createDeptForm')?.reset();
    openModal('createDeptModal');
}

function openEditDeptModal(btn) {
    const d = btn.dataset;
    document.getElementById('editDeptId').value       = d.deptId;
    document.getElementById('editDeptCode').value     = d.code || '';
    document.getElementById('editDeptName').value     = d.name || '';
    document.getElementById('editDeptPhone').value    = d.phone || '';
    document.getElementById('editDeptEmail').value    = d.email || '';
    document.getElementById('editDeptAddress').value  = d.address || '';
    document.getElementById('editDeptLeaderId').value = d.leaderId || '';
    openModal('editDeptModal');
}

async function submitCreateDept(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const data = {
        code:     fd.get('code')?.trim(),
        name:     fd.get('name')?.trim(),
        phone:    fd.get('phone')?.trim() || null,
        email:    fd.get('email')?.trim() || null,
        address:  fd.get('address')?.trim() || null,
        leaderId: fd.get('leaderId') ? parseInt(fd.get('leaderId')) : null
    };
    const btn = e.target.querySelector('[type=submit]');
    btn.disabled = true; btn.textContent = 'Đang tạo...';
    try {
        const res = await fetch('/admin/departments', {
            method: 'POST', credentials: 'same-origin',
            headers: getHeaders(), body: JSON.stringify(data)
        });
        const json = await res.json();
        if (!res.ok) { showModalError('createDeptError', json.message || 'Lỗi tạo phòng ban'); return; }
        closeModal('createDeptModal');
        window.location.reload();
    } catch { showModalError('createDeptError', 'Lỗi kết nối. Vui lòng thử lại.'); }
    finally { btn.disabled = false; btn.textContent = 'Tạo phòng ban'; }
}

async function submitEditDept(e) {
    e.preventDefault();
    const deptId = document.getElementById('editDeptId').value;
    const data = {
        name:     document.getElementById('editDeptName').value?.trim(),
        phone:    document.getElementById('editDeptPhone').value?.trim() || null,
        email:    document.getElementById('editDeptEmail').value?.trim() || null,
        address:  document.getElementById('editDeptAddress').value?.trim() || null,
        leaderId: document.getElementById('editDeptLeaderId').value
                    ? parseInt(document.getElementById('editDeptLeaderId').value) : null
    };
    const btn = e.target.querySelector('[type=submit]');
    btn.disabled = true; btn.textContent = 'Đang lưu...';
    try {
        const res = await fetch(`/admin/departments/${deptId}`, {
            method: 'PUT', credentials: 'same-origin',
            headers: getHeaders(), body: JSON.stringify(data)
        });
        const json = await res.json();
        if (!res.ok) { showModalError('editDeptError', json.message || 'Lỗi cập nhật'); return; }
        closeModal('editDeptModal');
        showPageFlash('Cập nhật phòng ban thành công!');
        // Cập nhật DOM + data-* attributes
        const row = document.getElementById(`dept-row-${deptId}`);
        if (row) {
            const s = json.data;
            // Col 1: tên phòng ban
            const nameEl = row.querySelector('div[style*="font-weight:600"]');
            if (nameEl) nameEl.textContent = s.name || '';
            // Col 2: SĐT + email
            const phoneDivs = row.cells[1]?.querySelectorAll('div');
            if (phoneDivs?.[0]) phoneDivs[0].textContent = s.phone || '—';
            if (phoneDivs?.[1]) phoneDivs[1].textContent = s.email || '';
            // Col 3: trưởng phòng
            if (row.cells[2]) {
                row.cells[2].innerHTML = s.leaderName
                    ? `<span><div>${s.leaderName}</div><div style="font-size:12px;color:var(--muted)">${s.leaderEmail || ''}</div></span>`
                    : `<span style="color:var(--muted)">Chưa có</span>`;
            }
            // Sync data-* trên nút Sửa
            const editBtn = row.querySelector('.bsm.be');
            if (editBtn) {
                editBtn.dataset.name     = s.name     || '';
                editBtn.dataset.phone    = s.phone    || '';
                editBtn.dataset.email    = s.email    || '';
                editBtn.dataset.address  = s.address  || '';
                editBtn.dataset.leaderId = s.leaderId != null ? s.leaderId : '';
            }
        }
    } catch { showModalError('editDeptError', 'Lỗi kết nối. Vui lòng thử lại.'); }
    finally { btn.disabled = false; btn.textContent = 'Lưu thay đổi'; }
}

async function deleteDept(btn) {
    const deptId     = btn.dataset.deptId;
    const name       = btn.dataset.name;
    const staffCount = parseInt(btn.dataset.staffCount || '0');
    if (staffCount > 0) {
        alert(`Không thể xóa phòng ban "${name}".\nCòn ${staffCount} cán bộ thuộc phòng ban này.\nHãy chuyển cán bộ sang phòng ban khác trước.`);
        return;
    }
    if (!confirm(`Xóa phòng ban "${name}"?\nHành động này không thể hoàn tác.`)) return;
    try {
        const res  = await fetch(`/admin/departments/${deptId}`, {
            method: 'DELETE', credentials: 'same-origin', headers: getHeaders(false)
        });
        const json = await res.json();
        if (!res.ok) { showPageFlash(json.message || 'Lỗi xóa phòng ban', 'error'); return; }
        document.getElementById(`dept-row-${deptId}`)?.remove();
        showPageFlash('Đã xóa phòng ban thành công!');
    } catch { showPageFlash('Lỗi kết nối. Vui lòng thử lại.', 'error'); }
}

