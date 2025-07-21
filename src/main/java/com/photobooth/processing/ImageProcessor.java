package com.photobooth.processing;

import com.photobooth.config.ImagePosition;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ImageProcessor {

    /**
     * Xử lý và ghép nhiều ảnh vào một ảnh khung (template) duy nhất sử dụng ImageMagick.
     * <p>
     * Quy trình hoạt động theo 3 bước:
     * 1. Xử lý trước (pre-process) từng ảnh con: resize, crop, áp dụng preset màu và lưu vào một tệp tạm.
     * 2. Ghép cuối cùng: Tạo một ảnh nền trong suốt, ghép tất cả các tệp tạm đã xử lý vào, sau đó ghép ảnh khung lên trên cùng.
     * 3. Dọn dẹp: Xóa tất cả các tệp tạm đã tạo.
     *
     * @param images         Danh sách các tệp ảnh nguồn cần ghép.
     * @param presetFile     Tệp preset màu (ảnh Hald CLUT .png) để áp dụng.
     * @param templateFile   Tệp ảnh khung (frame) sẽ được đặt lên trên cùng.
     * @param templateWidth  Chiều rộng của ảnh khung.
     * @param templateHeight Chiều cao của ảnh khung.
     * @param exportFolder   Thư mục để lưu ảnh kết quả.
     * @param positions      Danh sách vị trí và kích thước tương ứng cho mỗi ảnh nguồn.
     * @return Tệp ảnh kết quả đã được xử lý.
     * @throws IOException          Nếu có lỗi về file hoặc lỗi khi chạy ImageMagick.
     * @throws InterruptedException Nếu luồng bị ngắt.
     */
    public File processImages(List<File> images, File presetFile, File templateFile, int templateWidth, int templateHeight, File exportFolder, List<ImagePosition> positions) throws IOException, InterruptedException {

        // --- KIỂM TRA ĐIỀU KIỆN ĐẦU VÀO ---
        // Kiểm tra xem ImageMagick đã được cài đặt và có trong PATH hệ thống chưa
        if (!isImageMagickInstalled()) {
            throw new IOException("ImageMagick not found. Ensure 'magick' is installed and added to PATH.");
        }
        // Kiểm tra sự tồn tại của các tệp tin cần thiết
        if (templateFile == null || !templateFile.exists()) {
            throw new IOException("Template file not found or not specified.");
        }
        // Đảm bảo tất cả các ảnh trong danh sách đều tồn tại
        images.stream().filter(Objects::nonNull).forEach(image -> {
            if (!image.exists()) {
                // Ném ra một RuntimeException để dừng quá trình ngay lập tức nếu có file không tồn tại
                throw new RuntimeException(new IOException("Image file not found: " + image.getAbsolutePath()));
            }
        });

        // Danh sách để lưu trữ các tệp tạm thời sẽ được tạo ra
        List<File> tempFiles = new ArrayList<>();
        try {
            //--- BƯỚC 1: XỬ LÝ TRƯỚC TỪNG ẢNH VÀ LƯU VÀO FILE TẠM ---
            for (int i = 0; i < images.size(); i++) {
                File image = images.get(i);
                ImagePosition pos = positions.get(i);
                if (image == null) continue; // Bỏ qua nếu file ảnh không hợp lệ

                // Tạo một tệp tạm thời với tiền tố và hậu tố ".png" trong thư mục tạm của hệ thống
                File tempOut = Files.createTempFile("photobooth_temp_", ".png").toFile();
                tempFiles.add(tempOut); // Thêm vào danh sách để dọn dẹp sau này.

                // Xây dựng câu lệnh ImageMagick để xử lý một ảnh duy nhất
                List<String> singleImageCommand = new ArrayList<>();
                singleImageCommand.add("magick");
                singleImageCommand.add(image.getAbsolutePath()); // Ảnh đầu vào
                singleImageCommand.add("-resize");
                // Thay đổi kích thước: fill đầy vùng chứa mà không làm méo ảnh, phần thừa sẽ được cắt
                singleImageCommand.add((int) pos.width() + "x" + (int) pos.height() + "^");
                singleImageCommand.add("-gravity");
                singleImageCommand.add("center"); // Căn giữa ảnh
                singleImageCommand.add("-extent");
                // Cắt ảnh về đúng kích thước yêu cầu
                singleImageCommand.add((int) pos.width() + "x" + (int) pos.height());
                singleImageCommand.add("-strip"); // Xóa bỏ các metadata không cần thiết (EXIF, v.v.)

                // Nếu có tệp preset được cung cấp thì áp dụng nó
                if (presetFile != null && presetFile.exists() && presetFile.getName().toLowerCase().endsWith(".png")) {
                    singleImageCommand.add(presetFile.getAbsolutePath()); // Tệp LUT
                    singleImageCommand.add("-hald-clut"); // Toán tử áp dụng LUT
                }
                singleImageCommand.add(tempOut.getAbsolutePath()); // Tệp output tạm thời

                // Thực thi câu lệnh cho ảnh này
                executeCommand(singleImageCommand, "Processing " + image.getName());
            }

            //--- BƯỚC 2: GHÉP CÁC FILE TẠM ĐÃ XỬ LÝ VÀO ẢNH KHUNG ---
            List<String> finalCompositeCommand = new ArrayList<>();
            finalCompositeCommand.add("magick");

            // Tạo một lớp nền trong suốt có kích thước bằng ảnh khung
            finalCompositeCommand.add("-size");
            finalCompositeCommand.add(templateWidth + "x" + templateHeight);
            finalCompositeCommand.add("xc:transparent"); // "xc:transparent" là màu trong suốt trong ImageMagick.

            // Ghép lần lượt các ảnh tạm đã xử lý vào nền trong suốt tại đúng vị trí
            for (int i = 0; i < tempFiles.size(); i++) {
                File tempImage = tempFiles.get(i);
                ImagePosition pos = positions.get(i);

                finalCompositeCommand.add(tempImage.getAbsolutePath()); // Ảnh cần ghép
                finalCompositeCommand.add("-geometry");
                finalCompositeCommand.add("+" + (int) pos.x() + "+" + (int) pos.y()); // Vị trí (x,y)
                finalCompositeCommand.add("-composite"); // Toán tử ghép ảnh
            }

            // Cuối cùng, ghép ảnh khung lên trên tất cả các lớp đã có
            finalCompositeCommand.add(templateFile.getAbsolutePath());
            finalCompositeCommand.add("-composite");

            // Xác định tên và đường dẫn cho tệp output cuối cùng
            String outputFileName = "output_" + System.currentTimeMillis() + ".png";
            String outputFilePath = new File(exportFolder, outputFileName).getAbsolutePath();
            finalCompositeCommand.add(outputFilePath);

            // Thực thi câu lệnh ghép cuối cùng
            executeCommand(finalCompositeCommand, "Final compositing");

            File outputFile = new File(outputFilePath);
            // Kiểm tra lại xem file output có thực sự được tạo ra không
            if (!outputFile.exists() || outputFile.length() == 0) {
                throw new IOException("Final output file was not created or is empty.");
            }
            // Trả về đối tượng File của ảnh kết quả
            return outputFile;

        } finally {
            //--- BƯỚC 3: DỌN DẸP CÁC FILE TẠM ---
            // Khối finally đảm bảo rằng mã này sẽ luôn được thực thi, dù cho có lỗi xảy ra ở khối try hay không.
            for (File tempFile : tempFiles) {
                if (tempFile.exists()) {
                    tempFile.delete(); // Xóa tệp tạm để không làm đầy ổ cứng
                }
            }
        }
    }

    /**
     * Phương thức trợ giúp để thực thi một câu lệnh dòng lệnh bên ngoài (như ImageMagick).
     * Nó sẽ chạy lệnh, ghi lại output, và ném ra một ngoại lệ (exception) nếu lệnh thất bại.
     *
     * @param commandList Danh sách các chuỗi, trong đó mỗi chuỗi là một phần của câu lệnh (ví dụ: "magick", "input.png", "-resize", "100x100").
     * @param stepName    Tên của bước đang thực thi, dùng để ghi log cho dễ gỡ lỗi.
     * @throws IOException          Nếu có lỗi xảy ra trong quá trình thực thi lệnh (ví dụ: lệnh không thành công).
     * @throws InterruptedException Nếu luồng hiện tại bị ngắt trong khi đang chờ tiến trình kết thúc.
     */
    private void executeCommand(List<String> commandList, String stepName) throws IOException, InterruptedException {
        // In ra câu lệnh sắp được thực thi để dễ dàng theo dõi và gỡ lỗi
        System.out.println("Executing Step [" + stepName + "]: " + commandList);

        // Sử dụng ProcessBuilder để chuẩn bị chạy một tiến trình hệ thống bên ngoài
        ProcessBuilder pb = new ProcessBuilder(commandList);

        // Hợp nhất luồng output và luồng lỗi thành một
        // Điều này giúp chúng ta bắt được tất cả các thông báo, kể cả lỗi, từ tiến trình
        pb.redirectErrorStream(true);

        // Bắt đầu chạy tiến trình
        Process process = pb.start();

        // Dùng StringBuilder để thu thập toàn bộ output từ tiến trình
        StringBuilder processOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            // Đọc từng dòng output cho đến khi tiến trình kết thúc
            while ((line = reader.readLine()) != null) {
                processOutput.append(line).append("\n");
            }
        }

        // Chờ cho đến khi tiến trình bên ngoài thực sự kết thúc và lấy mã thoát (exit code)
        // Mã thoát 0 thường có nghĩa là thành công. Bất kỳ số nào khác đều là lỗi
        int exitCode = process.waitFor();

        // Nếu mã thoát khác 0, tức là đã có lỗi xảy ra
        if (exitCode != 0) {
            // In ra toàn bộ output đã thu thập được để giúp chẩn đoán lỗi
            System.err.println("--- ImageMagick Output for step [" + stepName + "] ---");
            System.err.println(processOutput);
            System.err.println("-------------------------------------------------");

            // Ném ra một ngoại lệ để báo cho phần còn lại của chương trình biết rằng bước này đã thất bại
            throw new IOException("ImageMagick step [" + stepName + "] failed with exit code: " + exitCode);
        }
    }

    private boolean isImageMagickInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("magick", "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}