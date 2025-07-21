package com.photobooth;

import com.photobooth.ui.PhotoBoothController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.image.Image;

/**
 * Lớp chính (Main Class) của ứng dụng, chịu trách nhiệm khởi tạo và hiển thị giao diện người dùng
 */
public class Main extends Application {
    /** Tham chiếu đến controller chính để gọi phương thức dọn dẹp khi đóng ứng dụng */
    private PhotoBoothController controller;

    /**
     * Phương thức chính, được gọi khi ứng dụng khởi động
     *
     * @param primaryStage Cửa sổ chính của ứng dụng, được cung cấp bởi JavaFX
     * @throws Exception Nếu có lỗi xảy ra trong quá trình tải file FXML
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Tạo một FXMLLoader để tải file giao diện từ thư mục resources
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/photobooth/ui/photobooth.fxml"));
        // Tải FXML và tạo Scene chứa toàn bộ các thành phần giao diện
        Scene scene = new Scene(loader.load());
        // Lấy một tham chiếu đến đối tượng controller được liên kết với file FXML
        controller = loader.getController();

        // Tải file icon của ứng dụng từ thư mục resources
        Image icon = new Image(getClass().getResourceAsStream("/icon/logoptb.png"));
        // Thêm icon vào danh sách icon của cửa sổ chính
        primaryStage.getIcons().add(icon);

        // Đặt Scene vào cửa sổ chính
        primaryStage.setScene(scene);
        // Phóng to cửa sổ ra toàn màn hình khi khởi động
        primaryStage.setMaximized(true);
        // Đặt tiêu đề cho cửa sổ
        primaryStage.setTitle("PhotoBooth");
        // Thiết lập một hành động để thực thi khi người dùng nhấn nút đóng cửa sổ
        primaryStage.setOnCloseRequest(event -> {
            // Đảm bảo controller tồn tại trước khi gọi phương thức dọn dẹp
            if (controller != null) {
                controller.cleanup(); // Gọi hàm dọn dẹp tài nguyên (dừng các luồng nền)
            }
        });
        // Hiển thị cửa sổ ứng dụng
        primaryStage.show();
    }

    /**
     * Phương thức này được gọi bởi JavaFX khi ứng dụng sắp đóng lại
     * Đây là một cơ chế dự phòng để đảm bảo việc dọn dẹp luôn được thực hiện
     */
    @Override
    public void stop() {
        if (controller != null) {
            controller.cleanup();
        }
    }

    public static void main(String[] args) {
        // Gọi phương thức launch() của JavaFX để bắt đầu toàn bộ ứng dụng
        launch(args);
    }
}