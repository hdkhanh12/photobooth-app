module com.photobooth {
    // Yêu cầu các module cần thiết để chạy
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;      // Cần cho ImageIO (đọc kích thước ảnh)
    requires com.google.gson;   // Cần cho việc lưu/tải config

    // Mở package 'config' cho module 'gson' để nó có thể đọc/ghi JSON
    opens com.photobooth.config to com.google.gson;
    // Mở package 'ui' cho module 'fxml' để nó có thể truy cập controller và các thành phần FXML
    opens com.photobooth.ui to javafx.fxml;

    // Xuất package chính để ứng dụng có thể được khởi chạy
    exports com.photobooth;
}