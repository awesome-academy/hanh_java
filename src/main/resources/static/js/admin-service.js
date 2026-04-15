/**
 * admin-service.js — JS cho trang Quản lý dịch vụ công (/admin/services)
 * Phụ thuộc: admin.js (getHeaders, openModal, closeModal, showPageFlash, showModalError)
 */

function openCreateServiceModal() {
    document.getElementById('createServiceForm')?.reset();
    openModal('createServiceModal');
}

function openEditServiceModal(btn) {
    const d = btn.dataset;
    document.getElementById('editSvcId').value           = d.svcId;
    document.getElementById('editSvcName').value         = d.name || '';
    document.getElementById('editSvcCategoryId').value   = d.categoryId || '';
    document.getElementById('editSvcDepartmentId').value = d.departmentId || '';
    document.getElementById('editSvcDays').value         = d.processingTimeDays || '5';
    document.getElementById('editSvcFee').value          = d.fee || '0';
    document.getElementById('editSvcFeeDesc').value      = d.feeDescription || '';
    document.getElementById('editSvcDesc').value         = d.description || '';
    document.getElementById('editSvcReqs').value         = d.requirements || '';
    openModal('editServiceModal');
}

async function submitCreateService(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const data = {
        code:               fd.get('code')?.trim(),
        name:               fd.get('name')?.trim(),
        categoryId:         parseInt(fd.get('categoryId')),
        departmentId:       parseInt(fd.get('departmentId')),
        processingTimeDays: parseInt(fd.get('processingTimeDays')),
        fee:                parseFloat(fd.get('fee') || '0'),
        feeDescription:     fd.get('feeDescription')?.trim() || null,
        description:        fd.get('description')?.trim() || null,
        requirements:       fd.get('requirements')?.trim() || null
    };
    const btn = e.target.querySelector('[type=submit]');
    btn.disabled = true; btn.textContent = 'Đang tạo...';
    try {
        const res = await fetch('/admin/services', {
            method: 'POST', credentials: 'same-origin',
            headers: getHeaders(), body: JSON.stringify(data)
        });
        const json = await res.json();
        if (!res.ok) { showModalError('createServiceError', json.message || 'Lỗi tạo dịch vụ'); return; }
        closeModal('createServiceModal');
        window.location.reload();
    } catch { showModalError('createServiceError', 'Lỗi kết nối. Vui lòng thử lại.'); }
    finally { btn.disabled = false; btn.textContent = 'Tạo dịch vụ'; }
}

async function submitEditService(e) {
    e.preventDefault();
    const svcId = document.getElementById('editSvcId').value;
    const data = {
        name:               document.getElementById('editSvcName').value?.trim(),
        categoryId:         parseInt(document.getElementById('editSvcCategoryId').value),
        departmentId:       parseInt(document.getElementById('editSvcDepartmentId').value),
        processingTimeDays: parseInt(document.getElementById('editSvcDays').value),
        fee:                parseFloat(document.getElementById('editSvcFee').value || '0'),
        feeDescription:     document.getElementById('editSvcFeeDesc').value?.trim() || null,
        description:        document.getElementById('editSvcDesc').value?.trim() || null,
        requirements:       document.getElementById('editSvcReqs').value?.trim() || null
    };
    const btn = e.target.querySelector('[type=submit]');
    btn.disabled = true; btn.textContent = 'Đang lưu...';
    try {
        const res = await fetch(`/admin/services/${svcId}`, {
            method: 'PUT', credentials: 'same-origin',
            headers: getHeaders(), body: JSON.stringify(data)
        });
        const json = await res.json();
        if (!res.ok) { showModalError('editServiceError', json.message || 'Lỗi cập nhật'); return; }
        closeModal('editServiceModal');
        showPageFlash('Cập nhật dịch vụ thành công!');
        // Cập nhật DOM + data-* attributes
        const row = document.getElementById(`svc-row-${svcId}`);
        if (row) {
            const s = json.data;
            // Col 1: tên dịch vụ
            const nameEl = row.querySelector('.svc-name');
            if (nameEl) nameEl.textContent = s.name || '';
            // Col 2: lĩnh vực badge
            const catBadge = row.cells[1]?.querySelector('.pill');
            if (catBadge) catBadge.textContent = s.categoryName || '';
            // Col 3: phòng ban tiếp nhận
            if (row.cells[2]) row.cells[2].textContent = s.departmentName || '';
            // Col 4: thời hạn xử lý
            if (row.cells[3]) row.cells[3].textContent = (s.processingTimeDays || 0) + ' ngày';
            // Col 5: lệ phí
            const feeSpan = row.cells[4]?.querySelector('span');
            if (feeSpan) {
                feeSpan.textContent = (s.fee != null && s.fee > 0)
                    ? Math.round(s.fee).toLocaleString('en-US') + ' đ'
                    : 'Miễn phí';
            }
            // Sync data-* trên nút Sửa
            const editBtn = row.querySelector('.bsm.be');
            if (editBtn) {
                editBtn.dataset.name               = s.name               || '';
                editBtn.dataset.categoryId         = s.categoryId         != null ? s.categoryId         : '';
                editBtn.dataset.departmentId       = s.departmentId       != null ? s.departmentId       : '';
                editBtn.dataset.description        = s.description        || '';
                editBtn.dataset.requirements       = s.requirements       || '';
                editBtn.dataset.processingTimeDays = s.processingTimeDays != null ? s.processingTimeDays : '';
                editBtn.dataset.fee                = s.fee                != null ? s.fee                : '';
                editBtn.dataset.feeDescription     = s.feeDescription     || '';
            }
        }
    } catch { showModalError('editServiceError', 'Lỗi kết nối. Vui lòng thử lại.'); }
    finally { btn.disabled = false; btn.textContent = 'Lưu thay đổi'; }
}

async function toggleService(btn) {
    const svcId  = btn.dataset.svcId;
    const active = btn.dataset.active === 'true';
    const action = active ? 'tắt' : 'bật';
    if (!confirm(`Bạn có muốn ${action} dịch vụ này?`)) return;
    try {
        const res  = await fetch(`/admin/services/${svcId}/toggle`, {
            method: 'PUT', credentials: 'same-origin', headers: getHeaders(false)
        });
        const json = await res.json();
        if (!res.ok) { showPageFlash(json.message || 'Lỗi thao tác', 'error'); return; }
        const row      = document.getElementById(`svc-row-${svcId}`);
        const newActive = json.data.active;
        if (row) {
            row.querySelector('.cell-svc-status').innerHTML = newActive
                ? '<span class="pill p-green">Hoạt động</span>'
                : '<span class="pill p-gray">Tạm dừng</span>';
            btn.dataset.active   = newActive;
            btn.textContent      = newActive ? 'Tắt' : 'Bật';
            btn.style.background = newActive ? '#FEF9C3' : '#DCFCE7';
            btn.style.color      = newActive ? '#92400E' : '#15803D';
        }
        showPageFlash(`Đã ${action} dịch vụ thành công!`);
    } catch { showPageFlash('Lỗi kết nối. Vui lòng thử lại.', 'error'); }
}

async function deleteService(btn) {
    const svcId       = btn.dataset.svcId;
    const name        = btn.dataset.name;
    const activeCount = parseInt(btn.dataset.activeCount || '0');
    if (activeCount > 0) {
        alert(`Không thể xóa dịch vụ "${name}".\nHiện có ${activeCount} hồ sơ đang xử lý.\nHãy tắt dịch vụ thay vì xóa.`);
        return;
    }
    if (!confirm(`Xóa dịch vụ "${name}"?\nHành động này không thể hoàn tác.`)) return;
    try {
        const res  = await fetch(`/admin/services/${svcId}`, {
            method: 'DELETE', credentials: 'same-origin', headers: getHeaders(false)
        });
        const json = await res.json();
        if (!res.ok) { showPageFlash(json.message || 'Lỗi xóa dịch vụ', 'error'); return; }
        document.getElementById(`svc-row-${svcId}`)?.remove();
        showPageFlash('Đã xóa dịch vụ thành công!');
    } catch { showPageFlash('Lỗi kết nối. Vui lòng thử lại.', 'error'); }
}

