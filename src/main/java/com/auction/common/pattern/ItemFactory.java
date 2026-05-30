package com.auction.common.pattern;

import com.auction.common.models.Art;
import com.auction.common.models.Electronics;
import com.auction.common.models.Fashion;
import com.auction.common.models.Furniture;
import com.auction.common.models.Item;
import com.auction.common.models.Vehicle;

/**
 * Factory Method Pattern — tạo đối tượng Item phù hợp theo loại danh mục.
 *
 * <p>Thay thế switch-case phân tán trong ItemDAO, tập trung logic tạo Item
 * vào một chỗ duy nhất (Open/Closed Principle).</p>
 *
 * <pre>
 * Sử dụng:
 *   Item item = ItemFactory.create("ID1", "Laptop", "Mô tả", 500.0, "ELECTRONICS");
 * </pre>
 */
public final class ItemFactory {

    private ItemFactory() {
        // Utility class — không cho phép khởi tạo
    }

    /**
     * Tạo Item từ thông tin cơ bản đọc từ database.
     *
     * @param id          Mã định danh sản phẩm
     * @param name        Tên sản phẩm
     * @param description Mô tả
     * @param initPrice   Giá khởi điểm
     * @param category    Loại sản phẩm (không phân biệt hoa/thường)
     * @return Đối tượng Item đúng loại
     */
    public static Item create(String id, String name, String description,
                               double initPrice, String category) {
        if (category == null || category.isBlank()) {
            category = "ELECTRONICS";
        }
        return switch (category.trim().toUpperCase()) {
            case "ART"         -> new Art(id, name, description, initPrice,
                                          "Unknown Artist", 0, "Unknown Technique");
            case "VEHICLE"     -> new Vehicle(id, name, description, initPrice,
                                              "Unknown Brand", "Unknown Model", 0);
            case "FASHION"     -> new Fashion(id, name, description, initPrice,
                                              "Unknown Brand", "Unknown Size", "Unknown Material");
            case "FURNITURE"   -> new Furniture(id, name, description, initPrice,
                                               "Unknown Material", "Unknown Dimensions");
            default            -> new Electronics(id, name, description, initPrice,
                                                  category, "", "", "");
            // ELECTRONICS hoặc bất kỳ loại chưa biết → dùng Electronics làm fallback
        };
    }

    /**
     * Tạo Electronics với đầy đủ thông tin kỹ thuật.
     */
    public static Item createElectronics(String id, String name, String description,
                                          double initPrice, String brand, String model,
                                          String serialNumber, String warrantyDate) {
        return new Electronics(id, name, description, initPrice,
                               brand, model, serialNumber, warrantyDate);
    }

    /**
     * Tạo Art với đầy đủ thông tin nghệ thuật.
     */
    public static Item createArt(String id, String name, String description,
                                  double initPrice, String artist,
                                  int year, String technique) {
        return new Art(id, name, description, initPrice, artist, year, technique);
    }

    /**
     * Tạo Vehicle với đầy đủ thông tin xe.
     */
    public static Item createVehicle(String id, String name, String description,
                                      double initPrice, String brand,
                                      String model, int mileage) {
        return new Vehicle(id, name, description, initPrice, brand, model, mileage);
    }
}
