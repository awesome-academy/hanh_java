package com.psms.entity.base;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serializable;

/**
 * Lớp gốc chứa {@code @Id} và {@code equals/hashCode} cho tất cả JPA entity.
 *
 * <p><b>Tại sao không có getter/setter ở đây?</b><br>
 * Type Erasure: sau khi compile, {@code ID} bị xóa thành {@code Serializable}.
 * Lombok {@code @Getter} ở đây sẽ sinh ra {@code Serializable getId()} trong bytecode
 * thay vì {@code Long getId()} — Hibernate dùng reflection tìm không thấy → {@code NoSuchMethodError}.
 * Getter/setter được khai báo tường minh ở {@link LongBaseEntity} để tạo đúng kiểu trong bytecode.
 *
 * <p><b>Hierarchy:</b>
 * <pre>
 *   BaseEntity&lt;ID&gt;
 *     └── LongBaseEntity           → User, Citizen, Department, Staff
 *           └── AuditableLongEntity  → ServiceType, Application, ApplicationStatusHistory
 *
 *   Role            → @Id Byte    (trực tiếp trong entity, không qua base class)
 *   ServiceCategory → @Id Integer (trực tiếp trong entity, không qua base class)
 * </pre>
 *
 * <p><b>Tại sao equals/hashCode dùng {@code HibernateProxy}?</b><br>
 * Hibernate lazy load trả về proxy thay vì entity thật.
 * {@code proxy.getClass()} trả về class proxy, không phải entity class
 * → so sánh class trực tiếp sẽ sai. {@code HibernateProxy} cho phép lấy đúng entity class thật.
 */
@MappedSuperclass
public abstract class BaseEntity<ID extends Serializable> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected ID id;

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }

        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : getClass();

        if (thisEffectiveClass != oEffectiveClass) {
            return false;
        }

        BaseEntity<?> that = (BaseEntity<?>) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        // Dùng class thật (không phải proxy class) để hashCode ổn định
        // trước và sau khi entity được load từ DB.
        return this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
