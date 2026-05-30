package com.auction.common.models;

/**
 * Lớp trừu tượng đại diện cho sản phẩm đưa ra đấu giá.
 *
 * <p>Các lớp con cụ thể: {@link Electronics}, {@link Art},
 * {@link Vehicle}, {@link Fashion}, {@link Furniture}.</p>
 *
 * <p>Áp dụng Polymorphism: {@link #showDetail()} được override
 * ở mỗi lớp con để in thông tin đặc thù của từng loại sản phẩm.</p>
 *
 * <p>Factory Method Pattern: dùng {@link com.auction.common.pattern.ItemFactory}
 * để tạo đối tượng Item đúng loại theo category.</p>
 */
public abstract class Item extends Entity {

    protected String name;
    protected String description;
    protected double initPrice;
    protected String category;

    protected Item(String id, String name, String description,
                    double initPrice, String category) {
        super(id);
        this.name        = name;
        this.description = description;
        this.initPrice   = (initPrice >= 0) ? initPrice : 0;
        this.category    = category;
    }

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------
    public String getName()        { return name;        }
    public String getDescription() { return description; }
    public double getInitPrice()   { return initPrice;   }
    public String getCategory()    { return category;    }

    public void setName(String name)               { this.name        = name;        }
    public void setDescription(String description) { this.description = description; }
    public void setInitPrice(double initPrice)     { if (initPrice >= 0) this.initPrice = initPrice; }
    public void setCategory(String category)       { this.category    = category;    }

    // -------------------------------------------------------------------------
    // Polymorphism — mỗi lớp con in thông tin đặc thù
    // -------------------------------------------------------------------------

    /**
     * Hiển thị thông tin chi tiết sản phẩm.
     * Override tại: {@link Electronics}, {@link Art}, {@link Vehicle},
     * {@link Fashion}, {@link Furniture}.
     */
    @Override
    public abstract void showDetail();

    @Override
    public String toString() {
        return String.format("Item{id='%s', name='%s', category='%s', initPrice=%,.0f}",
                id, name, category, initPrice);
    }
}
