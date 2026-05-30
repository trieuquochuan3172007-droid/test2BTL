package com.auction.common.models;

import java.io.Serializable;
import java.util.Objects;

/**
 * Lớp cơ sở trừu tượng cho tất cả thực thể trong hệ thống.
 *
 * <p>Cung cấp mã định danh duy nhất ({@code id}) và triển khai
 * {@code equals}/{@code hashCode} chuẩn để dùng trong Collection.</p>
 *
 * <p>Áp dụng: Abstraction — lớp này không thể khởi tạo trực tiếp,
 * phải kế thừa qua {@link User}, {@link Item}, v.v.</p>
 */
public abstract class Entity implements Serializable {

    private static final long serialVersionUID = 1L;

    protected String id;

    protected Entity() {}

    protected Entity(String id) {
        this.id = id;
    }

    public String getId()            { return id; }
    public void   setId(String id)   { this.id = id; }

    /**
     * Hiển thị thông tin chi tiết của thực thể — mỗi lớp con override để in đặc thù.
     * Áp dụng Polymorphism.
     */
    public abstract void showDetail();

    /** Hai thực thể bằng nhau khi cùng ID — chuẩn để dùng trong Set/Map. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entity other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id='" + id + "'}";
    }
}
