package com.photobooth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Lớp quản lý việc lưu, tải và xóa các cấu hình layout (TemplateConfig).
 * Sử dụng định dạng JSON để lưu trữ dữ liệu một cách có cấu trúc.
 */
public class ConfigManager {

    /** Đường dẫn đến thư mục nơi tất cả các file cấu hình .json sẽ được lưu. */
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), "PhotoBoothConfigs");

    /** Đối tượng Gson để thực hiện việc chuyển đổi giữa đối tượng Java và chuỗi JSON. */
    private final Gson gson;

    /**
     * Hàm khởi tạo (constructor) cho ConfigManager.
     * Sẽ khởi tạo Gson và tạo thư mục cấu hình nếu nó chưa tồn tại.
     */
    public ConfigManager() {
        // Khởi tạo Gson với tùy chọn "pretty printing" để file JSON dễ đọc hơn.
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            // Kiểm tra nếu thư mục cấu hình chưa tồn tại thì tạo mới.
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
        } catch (IOException e) {
            System.err.println("Không thể tạo thư mục cấu hình: " + e.getMessage());
        }
    }

    /**
     * Lưu một đối tượng TemplateConfig vào một file .json.
     * @param config Đối tượng cấu hình cần lưu.
     * @throws IOException Nếu có lỗi xảy ra trong quá trình ghi file.
     */
    public void saveConfig(TemplateConfig config) throws IOException {
        // Tạo tên file an toàn bằng cách thay thế các ký tự đặc biệt bằng dấu gạch dưới.
        String fileName = config.name().replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
        Path configFile = CONFIG_DIR.resolve(fileName); // Kết hợp đường dẫn thư mục và tên file.

        // Sử dụng try-with-resources để đảm bảo Writer được đóng lại sau khi dùng.
        try (Writer writer = new FileWriter(configFile.toFile())) {
            // Chuyển đổi đối tượng config thành chuỗi JSON và ghi vào file.
            gson.toJson(config, writer);
        }
    }

    /**
     * Tải tất cả các file cấu hình (.json) từ thư mục cấu hình.
     * @return Một danh sách (List) các đối tượng TemplateConfig đã được tải.
     */
    public List<TemplateConfig> loadAllConfigs() {
        List<TemplateConfig> configs = new ArrayList<>();
        if (!Files.isDirectory(CONFIG_DIR)) {
            return configs; // Trả về danh sách rỗng nếu thư mục không tồn tại.
        }

        // Sử dụng Stream API để duyệt qua tất cả các file trong thư mục.
        try (Stream<Path> paths = Files.walk(CONFIG_DIR)) {
            paths.filter(Files::isRegularFile) // Chỉ lấy các file thông thường (không phải thư mục).
                    .filter(path -> path.toString().endsWith(".json")) // Chỉ lấy các file có đuôi .json.
                    .forEach(path -> { // Với mỗi file .json tìm thấy...
                        // Sử dụng try-with-resources để đảm bảo Reader được đóng lại.
                        try (Reader reader = new FileReader(path.toFile())) {
                            // Chuyển đổi nội dung JSON từ file trở lại thành đối tượng TemplateConfig.
                            TemplateConfig config = gson.fromJson(reader, TemplateConfig.class);
                            if (config != null) {
                                configs.add(config); // Thêm vào danh sách kết quả.
                            }
                        } catch (IOException e) {
                            System.err.println("Không thể tải file cấu hình: " + path + "; " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Không thể đọc thư mục cấu hình: " + e.getMessage());
        }
        return configs;
    }

    /**
     * Xóa một file cấu hình dựa vào tên của nó.
     * @param configName Tên của cấu hình cần xóa.
     * @throws IOException Nếu có lỗi xảy ra trong quá trình xóa file.
     */
    public void deleteConfig(String configName) throws IOException {
        // Tạo lại tên file an toàn giống như lúc lưu để đảm bảo tìm đúng file.
        String fileName = configName.replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
        Path configFile = CONFIG_DIR.resolve(fileName);

        // Nếu file tồn tại, thực hiện xóa.
        if (Files.exists(configFile)) {
            Files.delete(configFile);
        }
    }
}