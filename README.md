# Ứng dụng PhotoBooth 📸

Một ứng dụng desktop được xây dựng bằng JavaFX để tạo ra các dải ảnh photobooth. Người dùng có thể dễ dàng ghép các ảnh cá nhân vào một khung (frame) có sẵn, áp dụng preset màu, và xuất ra một tệp ảnh hoàn chỉnh.

## ✨ Tính năng chính

- **Thiết kế dựa trên Khung (Template)** 
- **Vùng làm việc tương tác**   
- **Chỉnh sửa ảnh linh hoạt**
- **Quản lý Layout:**
    - Lưu lại bố cục (vị trí và kích thước của các ảnh) thành một template có thể tái sử dụng.
    - Tải lại các layout đã lưu để áp dụng nhanh các vị trí đặt ảnh.
- **Theo dõi thư mục (Folder Watching):** Tự động nhập ảnh mới được thêm vào một thư mục được chỉ định.
- **Áp dụng Preset màu:** Hỗ trợ áp dụng các file LUT (định dạng Hald CLUT `.png`) để chỉnh màu đồng bộ cho các bức ảnh.
- **Xử lý ảnh hàng loạt:** Tự động hóa quá trình ghép ảnh và áp dụng màu bằng công cụ ImageMagick.
- **Đóng gói chuyên nghiệp**

## 🛠️ Yêu cầu để Build từ mã nguồn

Để có thể tự biên dịch và đóng gói dự án, bạn cần cài đặt:
1.  **JDK 21 trở lên**
2.  **Apache Maven**
3.  **ImageMagick:** Công cụ xử lý ảnh mạnh mẽ. Phải được thêm vào biến môi trường `PATH`.
4.  **WiX Toolset v3:** Bộ công cụ cần thiết để `jpackage` tạo file cài đặt `.exe`. Phải được thêm vào biến môi trường `PATH`.

## 🚀 Hướng dẫn Build

Để đóng gói ứng dụng thành một bộ cài đặt `.exe`, hãy mở Terminal hoặc Command Prompt tại thư mục gốc của dự án và chạy lệnh:
```bash
mvn clean install
```
Bộ cài đặt sẽ được tạo ra trong thư mục `target/installer`.

