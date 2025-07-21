package com.photobooth.watcher;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Lớp chuyên dụng để theo dõi một thư mục cho các sự kiện tạo file mới.
 * Nó hoạt động trên một luồng nền (background thread) để không làm ảnh hưởng đến
 * luồng giao diện chính của ứng dụng.
 */
public class FolderWatcher {
    /** Đường dẫn đến thư mục cần theo dõi. */
    private final Path folderPath;
    /** Dịch vụ theo dõi của Java NIO, dùng để nhận các sự kiện từ hệ điều hành. */
    private final WatchService watchService;
    /** Bộ quản lý luồng để chạy tác vụ theo dõi trong nền. */
    private final ExecutorService executor;
    /**
     * Một hàm (callback) sẽ được gọi mỗi khi một file ảnh mới được phát hiện.
     * Controller sẽ cung cấp hàm này để xử lý việc thêm ảnh vào danh sách.
     */
    private final Consumer<Path> onImageDetected;
    /** Cờ (flag) để kiểm soát vòng lặp theo dõi, cho phép dừng một cách an toàn. */
    private volatile boolean running;

    /**
     * Hàm khởi tạo (constructor) cho FolderWatcher.
     *
     * @param folderPath      Đường dẫn dạng chuỗi (String) đến thư mục cần theo dõi.
     * @param onImageDetected Hàm callback sẽ được thực thi khi có ảnh mới.
     * @throws IOException Nếu có lỗi khi khởi tạo WatchService.
     */
    public FolderWatcher(String folderPath, Consumer<Path> onImageDetected) throws IOException {
        this.folderPath = Paths.get(folderPath);
        this.watchService = FileSystems.getDefault().newWatchService();
        this.onImageDetected = onImageDetected;
        this.running = false;
        // Mỗi đối tượng FolderWatcher sẽ có một ExecutorService riêng, đảm bảo hoạt động độc lập.
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Bắt đầu quá trình theo dõi thư mục trên một luồng nền.
     */
    public void start() {
        // Ngăn việc chạy nhiều lần nếu đã đang chạy.
        if (running) return;
        running = true;

        // Gửi tác vụ theo dõi vào bộ quản lý luồng để nó chạy trong nền.
        executor.submit(() -> {
            try {
                // Đăng ký thư mục với WatchService, chỉ quan tâm đến sự kiện TẠO MỚI (ENTRY_CREATE).
                folderPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
                System.out.println("FolderWatcher started for: " + folderPath);

                // Bắt đầu vòng lặp chính để chờ đợi sự kiện.
                while (running) {
                    WatchKey key;
                    try {
                        // Đây là một lệnh blocking, luồng sẽ "ngủ" ở đây cho đến khi có sự kiện xảy ra.
                        key = watchService.take();
                    } catch (InterruptedException | ClosedWatchServiceException e) {
                        // Thoát vòng lặp một cách an toàn khi service bị đóng (bởi phương thức stop()) hoặc luồng bị ngắt.
                        break;
                    }

                    // Lấy danh sách các sự kiện đã xảy ra.
                    for (WatchEvent<?> event : key.pollEvents()) {
                        // Chỉ xử lý sự kiện tạo file mới.
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            // Lấy đường dẫn đầy đủ của file mới được tạo.
                            Path filePath = folderPath.resolve((Path) event.context());
                            // Kiểm tra xem file có phải là file ảnh không.
                            if (isImageFile(filePath)) {
                                // Nếu đúng là ảnh, gọi hàm callback đã được cung cấp.
                                if (onImageDetected != null) {
                                    onImageDetected.accept(filePath);
                                }
                            }
                        }
                    }
                    // Reset WatchKey để nó sẵn sàng nhận các sự kiện tiếp theo.
                    if (!key.reset()) {
                        break; // Thoát vòng lặp nếu thư mục không còn hợp lệ (ví dụ: đã bị xóa).
                    }
                }
            } catch (IOException e) {
                // Chỉ báo lỗi nếu watcher vẫn đang trong trạng thái "running".
                if (running) {
                    System.err.println("Error watching folder: " + e.getMessage());
                }
            } finally {
                System.out.println("FolderWatcher stopped");
            }
        });
    }

    /**
     * Dừng quá trình theo dõi một cách an toàn.
     */
    public void stop() {
        // Đặt cờ running thành false để vòng lặp chính kết thúc.
        running = false;
        try {
            // Đóng watchService. Hành động này sẽ gây ra một ngoại lệ (Exception)
            // ở lệnh watchService.take(), giúp "đánh thức" luồng đang ngủ và thoát ra.
            watchService.close();
        } catch (IOException e) {
            System.err.println("Error closing watch service: " + e.getMessage());
        }
        // Yêu cầu ExecutorService dừng tất cả các tác vụ ngay lập tức.
        executor.shutdownNow();
    }

    /**
     * Một phương thức trợ giúp đơn giản để kiểm tra đuôi file.
     *
     * @param filePath Đường dẫn đến file cần kiểm tra.
     * @return true nếu là file ảnh (.jpg, .jpeg, .png), ngược lại là false.
     */
    private boolean isImageFile(Path filePath) {
        String fileName = filePath.toString().toLowerCase();
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png");
    }
}