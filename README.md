# 🏆 Hệ Thống Đấu Giá Trực Tuyến

> **Bài tập lớn — Lập trình nâng cao**  
> Trường Đại học Công nghệ — ĐHQGHN

[![CI](https://github.com/YOUR_ORG/BaiTapLonLTNC/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_ORG/BaiTapLonLTNC/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://adoptium.net/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)](https://openjfx.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?logo=mysql)](https://www.mysql.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 📖 Mục lục

1. [Giới thiệu](#giới-thiệu)
2. [Kiến trúc hệ thống](#kiến-trúc-hệ-thống)
3. [Design Patterns](#design-patterns)
4. [Cây kế thừa OOP](#cây-kế-thừa-oop)
5. [Tính năng](#tính-năng)
6. [Hướng dẫn chạy](#hướng-dẫn-chạy)
7. [Cấu trúc thư mục](#cấu-trúc-thư-mục)
8. [Phân công nhiệm vụ](#phân-công-nhiệm-vụ)
9. [Chấm điểm tự đánh giá](#chấm-điểm-tự-đánh-giá)

---

## Giới thiệu

Hệ thống đấu giá trực tuyến cho phép nhiều người dùng đồng thời cạnh tranh giá để mua sản phẩm trong thời gian xác định, tương tự mô hình [eBay Auctions](https://www.ebay.com/b/bn_7000000718).

**Điểm nổi bật:**
- Kiến trúc **Client–Server** qua TCP Socket, giao tiếp bằng giao thức text tự định nghĩa
- **Realtime update** toàn bộ client khi có bid mới (không dùng polling)
- **Anti-sniping**: Tự động gia hạn phiên nếu có bid trong 30 giây cuối
- **HikariCP Connection Pool**: Xử lý 50 client đồng thời an toàn
- **BCrypt**: Mã hóa mật khẩu chuẩn công nghiệp
- **Wallet Freeze/Release**: Tạm khóa tiền khi đặt cọc, hoàn tiền tự động khi bị vượt mặt

---

## Kiến trúc hệ thống

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT (JavaFX)                          │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────┐    │
│  │  Login /     │   │  Main Auction│   │  Auction Room    │    │
│  │  Register    │   │  (Danh sách) │   │  (Realtime bid)  │    │
│  │  Controller  │   │  Controller  │   │  + LineChart     │    │
│  └──────┬───────┘   └──────┬───────┘   └────────┬─────────┘    │
│         │                  │                     │              │
│         └──────────────────┼─────────────────────┘             │
│                            │ NetworkClient (Singleton)          │
│                            │ TCP Socket :9999                   │
└────────────────────────────┼────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                        SERVER                                   │
│  AuctionServer ──► ThreadPool (50 threads)                      │
│         │                │                                      │
│         ▼                ▼                                      │
│  ClientHandler ──► AuctionManager (Singleton)                   │
│  (per client)            │                                      │
│                    ┌─────┴──────────────────┐                   │
│                    │                        │                   │
│              AuctionSession          Wallet Logic               │
│              (Domain Logic)         (Freeze/Release)            │
│                    │                        │                   │
│              ┌─────▼─────────────────────▼──┤                   │
│              │         DAO Layer            │                   │
│              │  UserDAO  AuctionDAO ItemDAO │                   │
│              └─────────────────────────────┘                   │
│                          │                                      │
│                    HikariCP Pool (20 connections)               │
│                          │                                      │
│                     MySQL 8.0                                   │
└─────────────────────────────────────────────────────────────────┘
```

### Luồng xử lý Bid

```
Client           NetworkClient       Server            AuctionManager
  │                   │                │                    │
  │── PLACE_BID ──────►                │                    │
  │                   │── TCP send ───►│                    │
  │                   │                │── placeBid() ─────►│
  │                   │                │                    │── session.processBid()
  │                   │                │                    │   (synchronized)
  │                   │                │                    │── wallet.freeze(newBidder)
  │                   │                │                    │── wallet.release(oldBidder)
  │                   │                │                    │── auctionDAO.save()
  │                   │                │◄── BidResult ──────│
  │                   │                │── broadcast() ─────────────────► All Clients
  │                   │◄── CHAP_NHAN──│                    │
  │◄── update UI ─────│                │                    │
```

---

## Design Patterns

| Pattern | Lớp áp dụng | Mục đích |
|---------|-------------|----------|
| **Singleton** | `AuctionManager`, `DatabaseUtil`, `NetworkClient` | Đảm bảo chỉ có một instance, tránh tạo nhiều kết nối |
| **Factory Method** | `ItemFactory` | Tạo `Item` đúng loại (Electronics/Art/Vehicle/...) theo `category`, tránh switch-case phân tán |
| **Observer** | `NetworkClient.MessageListener` | Nhận broadcast realtime từ server mà không cần polling |
| **Command / Value Object** | `BidResult` | Đóng gói kết quả đặt giá, tách logic nghiệp vụ khỏi tầng I/O |
| **DAO (Repository)** | `UserDAO`, `AuctionDAO`, `ItemDAO` | Tách biệt logic DB khỏi domain |

---

## Cây kế thừa OOP

```
Entity (abstract — Serializable)
│  └─ getId(), setId(), showDetail() [abstract], equals(), hashCode()
│
├── User (abstract)
│   ├── Bidder ──── Wallet (freeze/release)
│   ├── Seller
│   └── Admin
│
└── Item (abstract)
    ├── Electronics  ─ brand, model, serialNumber, warrantyDate
    ├── Art          ─ artist, year, technique
    ├── Vehicle      ─ brand, model, mileage
    ├── Fashion      ─ brand, size, material
    └── Furniture    ─ material, dimensions

Domain:
AuctionSession  ──── BidTransaction (immutable)
AuctionManager  ──── AuctionSession (ConcurrentHashMap)
AuctionStatus   (enum: OPEN → RUNNING ⇄ EXTENDED → FINISHED → PAID/CANCELED)
BidResult       (DTO: success/rejected + refund info)
```

**Nguyên tắc OOP áp dụng:**

| Nguyên tắc | Ví dụ cụ thể |
|------------|--------------|
| **Encapsulation** | `Wallet.freeze()` — ẩn logic tạm khóa; `AuctionSession.processBid()` — ẩn logic kiểm tra bid |
| **Inheritance** | `Bidder`, `Seller`, `Admin` kế thừa `User`; `Electronics`, `Art` kế thừa `Item` |
| **Polymorphism** | `showDetail()` được override ở mỗi lớp con; `ItemFactory.create()` trả `Item` đúng loại |
| **Abstraction** | `Entity` và `Item` là abstract class; `MessageListener` là functional interface |

---

## Tính năng

### Bắt buộc ✅

| Tính năng | Trạng thái | Ghi chú |
|-----------|------------|---------|
| Đăng ký / Đăng nhập | ✅ | BCrypt password hashing |
| Phân quyền Bidder / Seller / Admin | ✅ | Hiển thị giao diện theo role |
| Thêm / Xem sản phẩm | ✅ | Seller tạo item, hệ thống tạo phiên |
| Đặt giá (bid) | ✅ | Kiểm tra hợp lệ, cập nhật winner |
| Tự động đóng phiên hết giờ | ✅ | ScheduledExecutorService, 1s interval |
| Xử lý lỗi & ngoại lệ | ✅ | Custom exceptions, try-with-resources |
| GUI JavaFX + FXML | ✅ | Login, Main, AuctionRoom, CreateAuction |
| Kiến trúc Client–Server | ✅ | TCP Socket, ThreadPool 50 threads |
| MVC Client + Server | ✅ | FXML Controller / Controller→DAO |
| Maven build | ✅ | pom.xml với HikariCP, BCrypt, JUnit 5 |
| Unit Test JUnit 5 | ✅ | AuctionSessionTest, ManagerTest, WalletTest |
| CI/CD GitHub Actions | ✅ | Build + Test + Upload artifact |

### Nâng cao ✅

| Tính năng | Trạng thái | Chi tiết |
|-----------|------------|---------|
| **Anti-sniping** | ✅ | Bid trong 30s cuối → gia hạn 60s, status = EXTENDED |
| **Bid History Visualization** | ✅ | LineChart realtime (JavaFX Charts) |
| **Auto-bidding** | ✅ | maxBid + increment tuỳ chỉnh, kích hoạt khi bị vượt mặt |
| **Concurrent Bidding** | ✅ | `synchronized processBid()` + HikariCP pool |
| **Realtime update** | ✅ | Observer pattern + server broadcast, không polling |
| **Wallet Freeze/Release** | ✅ | Tạm khóa tiền bidder; hoàn tiền khi bị vượt mặt |

---

## Hướng dẫn chạy

### Yêu cầu

- Java 17+
- Maven 3.8+
- MySQL 8.0+

### Bước 1: Clone và cấu hình

```bash
git clone https://github.com/YOUR_ORG/BaiTapLonLTNC.git
cd BaiTapLonLTNC
```

Tạo file `src/main/resources/config/application.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/auction_system?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC
db.username=root
db.password=your_mysql_password
```

> ⚠️ File này đã được thêm vào `.gitignore` — **không commit mật khẩu lên GitHub**.

### Bước 2: Build

```bash
mvn clean compile
```

### Bước 3: Chạy Server

```bash
mvn exec:java -Dexec.mainClass="com.auction.server.network.AuctionServer"
```

Server tự động:
- Tạo database `auction_system` nếu chưa có
- Tạo tất cả bảng (bao gồm cột `frozen_amount`)
- Tải phiên đấu giá từ DB
- Lắng nghe ở cổng **9999**

### Bước 4: Chạy Client (terminal khác)

```bash
mvn javafx:run
```

### Bước 5: Chạy Tests

```bash
mvn test
```

---

## Cấu trúc thư mục

```
BaiTapLonLTNC/
├── src/
│   ├── main/java/com/auction/
│   │   ├── common/                    # Dùng chung client + server
│   │   │   ├── dto/
│   │   │   │   └── BidResult.java     # DTO kết quả đặt giá (Command Pattern)
│   │   │   ├── exception/
│   │   │   │   ├── InvalidBidException.java
│   │   │   │   ├── AuctionNotFoundException.java
│   │   │   │   └── InsufficientFundsException.java
│   │   │   ├── models/
│   │   │   │   ├── Entity.java        # Abstract base (equals/hashCode)
│   │   │   │   ├── User.java          # Abstract
│   │   │   │   ├── Bidder.java        ─┐
│   │   │   │   ├── Seller.java         │ Kế thừa User
│   │   │   │   ├── Admin.java         ─┘
│   │   │   │   ├── Item.java          # Abstract
│   │   │   │   ├── Electronics.java   ─┐
│   │   │   │   ├── Art.java            │ Kế thừa Item
│   │   │   │   ├── Vehicle.java        │
│   │   │   │   ├── Fashion.java        │
│   │   │   │   └── Furniture.java     ─┘
│   │   │   │   ├── Wallet.java        # Freeze/Release cơ chế
│   │   │   │   └── UserManager.java   # Session người dùng hiện tại
│   │   │   ├── pattern/
│   │   │   │   └── ItemFactory.java   # Factory Method Pattern ✨
│   │   │   └── util/
│   │   │       └── SceneUtil.java
│   │   │
│   │   ├── domain/                    # Nghiệp vụ thuần túy (không phụ thuộc DB)
│   │   │   ├── AuctionSession.java    # Core — synchronized processBid()
│   │   │   ├── AuctionManager.java    # Singleton — wallet + DAO
│   │   │   ├── AuctionStatus.java     # Enum với helper methods
│   │   │   └── BidTransaction.java    # Immutable value object
│   │   │
│   │   ├── server/
│   │   │   ├── network/
│   │   │   │   ├── AuctionServer.java # ThreadPool + broadcast
│   │   │   │   └── ClientHandler.java # Router lệnh + BCrypt login
│   │   │   ├── dao/
│   │   │   │   ├── UserDAO.java       # frozen_amount + try-with-resources
│   │   │   │   ├── AuctionDAO.java    # JOIN với items
│   │   │   │   └── ItemDAO.java       # Dùng ItemFactory
│   │   │   └── util/
│   │   │       └── DatabaseUtil.java  # HikariCP Singleton ✨
│   │   │
│   │   └── client/
│   │       ├── AuctionFxApp.java
│   │       ├── service/
│   │       │   └── NetworkClient.java # Observer + synchronized
│   │       ├── controller/
│   │       │   ├── LoginController.java
│   │       │   ├── MainAuctionController.java  # LIST_DETAIL (1 call) ✨
│   │       │   ├── AuctionRoomController.java  # Realtime + LineChart
│   │       │   └── CreateAuctionController.java
│   │       └── viewmodel/
│   │           └── AuctionRow.java
│   │
│   ├── main/resources/
│   │   ├── config/
│   │   │   ├── application.properties  # (gitignore — không commit)
│   │   │   └── schema.sql
│   │   └── fxml/
│   │       ├── Login.fxml
│   │       ├── MainAuction.fxml
│   │       ├── AuctionRoom.fxml
│   │       └── CreateAuction.fxml
│   │
│   └── test/java/com/auction/
│       ├── domain/
│       │   ├── AuctionSessionTest.java  # 8 tests, không cần DB ✨
│       │   └── AuctionManagerTest.java  # 9 tests
│       └── common/models/
│           └── WalletTest.java          # 14 tests, edge cases đầy đủ
│
├── .github/workflows/
│   └── ci.yml                          # GitHub Secrets (không hardcode pwd) ✨
├── pom.xml                             # HikariCP + BCrypt + JUnit 5
└── README.md
```

---

## Phân công nhiệm vụ

| Thành viên | Phụ trách | File chính |
|------------|-----------|------------|
| **Triệu Quốc Huân** | User System, Login/Register, GUI Login | `User`, `Bidder`, `Seller`, `Admin`, `LoginController`, `UserDAO` |
| **Đặng Gia Khánh** | Item System, Factory Pattern, Seller GUI | `Item`, `Electronics`, `Art`, `Vehicle`, `ItemFactory`, `ItemDAO`, `CreateAuctionController` |
| **Trần Mạnh Đức** | Auction Engine, Anti-sniping, Concurrency | `AuctionSession`, `AuctionStatus`, `BidTransaction`, `AuctionManager`, `AuctionDAO` |
| **Tống Trung Kiên** | Server, Network, LineChart, Auto-bid, CI/CD | `AuctionServer`, `ClientHandler`, `NetworkClient`, `DatabaseUtil`, `AuctionRoomController`, CI/CD |

---

## Chấm điểm tự đánh giá

| Tiêu chí | Điểm tối đa | Tự chấm | Lý do |
|----------|-------------|---------|-------|
| Thiết kế lớp & cây kế thừa | 0.5 | **0.5** | Entity→User/Item, equals/hashCode, abstract showDetail |
| Nguyên tắc OOP | 1.0 | **1.0** | Đầy đủ Encapsulation, Inheritance, Polymorphism, Abstraction |
| Design Patterns | 1.0 | **1.0** | Singleton, Factory Method, Observer, Command (BidResult) |
| Quản lý user/sản phẩm | 1.0 | **1.0** | Login BCrypt, Register, phân quyền, CRUD item |
| Chức năng đấu giá | 1.0 | **0.9** | Bid hợp lệ, wallet freeze/release, status transitions |
| Xử lý lỗi & ngoại lệ | 1.0 | **0.9** | Custom exceptions, try-with-resources, validation |
| Concurrent bidding | 1.0 | **0.9** | synchronized + HikariCP pool, wallet persisted |
| Realtime update | 0.5 | **0.5** | broadcast + Observer, daemon thread, no polling |
| Kiến trúc Client–Server | 0.5 | **0.5** | Tầng rõ ràng, domain không phụ thuộc server |
| MVC | 0.5 | **0.5** | FXML+Controller client; Controller→DAO server |
| Maven + Code quality | 0.5 | **0.5** | pom.xml, Javadoc, DRY, naming chuẩn |
| Unit Test | 0.5 | **0.5** | 31 tests, không cần DB, edge cases |
| CI/CD | 0.5 | **0.5** | GitHub Actions + Secrets + artifact upload |
| **Auto-bidding** (bonus) | 0.5 | **0.4** | maxBid + increment tuỳ chỉnh, kích hoạt tự động |
| **Anti-sniping** (bonus) | 0.5 | **0.5** | EXTENDED status, 30s threshold, 60s extension |
| **Bid History Chart** (bonus) | 0.5 | **0.5** | LineChart realtime, cập nhật mỗi bid |
| **Tổng** | **10 + 1.5** | **~10.2** | |

---

## Công nghệ sử dụng

| Công nghệ | Phiên bản | Mục đích |
|-----------|-----------|---------|
| Java | 17 | Ngôn ngữ chính, text blocks, pattern matching |
| JavaFX | 21 | GUI + FXML + LineChart |
| MySQL | 8.0 | Database lưu trữ |
| HikariCP | 5.1.0 | Connection Pool thread-safe |
| BCrypt | 0.10.2 | Mã hóa mật khẩu |
| JUnit 5 | 5.11.4 | Unit testing |
| GitHub Actions | — | CI/CD tự động |
| Maven | 3.8+ | Build tool |
