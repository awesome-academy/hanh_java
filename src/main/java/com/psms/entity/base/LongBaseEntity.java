package com.psms.entity.base;

import jakarta.persistence.MappedSuperclass;

/**
 * Base class cho entity có PK kiểu {@code Long} (BIGINT UNSIGNED).
 *
 * <p>Khai báo {@code Long getId()} tường minh để fix type erasure:
 * compiler sinh thêm bridge method {@code Serializable getId()} tự động
 * → Hibernate reflection tìm thấy cả hai dạng, không còn {@code NoSuchMethodError}.
 *
 * <p><b>Entity kế thừa:</b> User, Citizen, Department, Staff (trực tiếp);
 * ServiceType, Application, ApplicationStatusHistory (qua {@link AuditableLongEntity}).
 */
@MappedSuperclass
public abstract class LongBaseEntity extends BaseEntity<Long> {

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
