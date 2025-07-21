package com.photobooth.ui;

import com.photobooth.config.ConfigManager;
import com.photobooth.config.ImagePosition;
import com.photobooth.config.TemplateConfig;
import com.photobooth.watcher.FolderWatcher;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.scene.shape.SVGPath;
import javafx.scene.layout.Region;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.photobooth.processing.ImageProcessor;
import javafx.concurrent.Task;

import javax.imageio.ImageIO;
import java.util.stream.Collectors;

/**
 * Controller chính cho giao diện ứng dụng PhotoBooth.
 * Quản lý tất cả các tương tác của người dùng, trạng thái ứng dụng, và logic nghiệp vụ.
 */

public class PhotoBoothController {
    @FXML private ScrollPane scrollPane;
    @FXML private Pane templatePane; // Pane chính chứa template và các ảnh con
    @FXML private ImageView templateImageView; // Chỉ để hiển thị ảnh template
    @FXML private Pane overlayPane; // Pane trong suốt để kéo-thả và đặt ảnh
    @FXML private Slider zoomSlider;
    @FXML private Slider xPosSlider;
    @FXML private Slider yPosSlider;
    @FXML private TextField importFolderField;
    @FXML private TextField exportFolderField;
    @FXML private TextField presetField;
    @FXML private TextField psdFrameField;
    @FXML private Button selectImportButton;
    @FXML private Button selectExportButton;
    @FXML private Button selectPresetButton;
    @FXML private Button selectPsdButton;
    @FXML private Button selectImageButton;
    @FXML private Button startStopButton;
    @FXML private Button zoomInButton;
    @FXML private Button zoomOutButton;
    @FXML private Button processImagesButton;
    @FXML private Button saveTemplateConfigButton;
    @FXML private Label statusLabel;
    @FXML private ListView<File> imageGrid;
    @FXML private Button toggleFrameVisibilityButton;
    @FXML private ListView<String> configListView;
    @FXML private Button saveConfigButton;
    @FXML private Button clearCanvasButton;

    // Danh sách các tệp ảnh có sẵn, được hiển thị trong lưới bên trái
    private final ObservableList<File> imageFiles = FXCollections.observableArrayList();

    // private final List<ImagePosition> imagePositions = new ArrayList<>();
    private String currentImageName;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    // Cờ để theo dõi trạng thái hoạt động của FolderWatcher
    private volatile boolean isRunning = false;

    private ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor();
    private FolderWatcher folderWatcher;

    // Lưu tọa độ chuột ban đầu khi bắt đầu lia (pan) khung nhìn
    private final double[] pressTranslateX = new double[1];
    private final double[] pressTranslateY = new double[1];

    // Theo dõi Node đang được chọn
    private ResizableNode selectedNode;

    private boolean isFrameVisible = true;

    private ConfigManager configManager;

    // Danh sách tên các cấu hình đã lưu
    private final ObservableList<String> savedConfigNames = FXCollections.observableArrayList();

    // Hằng số ID để nhận dạng các ô placeholder khi tải một cấu hình
    private static final String PLACEHOLDER_ID = "config_placeholder";

    private ImageView iconViewVisible;
    private ImageView iconViewHidden;

    /**
     * Phương thức này được JavaFX tự động gọi sau khi tất cả các thành phần FXML đã được tải và inject.
     * Dùng để thiết lập các listener, binding, và trạng thái ban đầu cho giao diện.
     */
    @FXML
    private void initialize() {
        // 1. Khởi tạo và tải cấu hình đã lưu
        configManager = new ConfigManager();
        configListView.setItems(savedConfigNames);
        loadAndDisplayConfigs(); // Tải cấu hình khi khởi động

        // 2. Thiết lập listener cho ListView cấu hình: khi người dùng chọn một cấu hình, áp dụng nó lên vùng làm việc
        configListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                configManager.loadAllConfigs().stream()
                        .filter(c -> c.name().equals(newVal))
                        .findFirst()
                        .ifPresent(this::applyConfigAsPlaceholders);
            }
        });

        // 3. Thiết lập vùng làm việc có thể thu phóng (Zoom) và lia (Pan)
        Group zoomGroup = new Group();
        StackPane contentPane = new StackPane(templatePane);
        zoomGroup.getChildren().add(contentPane);
        scrollPane.setContent(zoomGroup);

        // Căn giữa nội dung khi kích thước thay đổi
        contentPane.minWidthProperty().bind(scrollPane.widthProperty());
        contentPane.minHeightProperty().bind(scrollPane.heightProperty());

        // 4. Thiết lập sự kiện Panning trên ScrollPane
        scrollPane.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                pressTranslateX[0] = event.getX();
                pressTranslateY[0] = event.getY();
                scrollPane.getScene().setCursor(Cursor.MOVE);
                //System.out.println("Panning started: x=" + pressTranslateX[0] + ", y=" + pressTranslateY[0]);
            }
        });

        enablePanePanning(); // Bật Panning mặc định

        scrollPane.setOnMouseReleased(event -> scrollPane.getScene().setCursor(Cursor.DEFAULT));

        // 5. Thiết lập sự kiện Zooming bằng con lăn chuột
        scrollPane.setOnScroll(event -> {
            if (event.getDeltaY() == 0) return;

            double scaleFactor = (event.getDeltaY() > 0) ? 1.1 : 1 / 1.1;
            double newScale = templatePane.getScaleX() * scaleFactor;

            // Giới hạn mức thu phóng
            if (newScale < zoomSlider.getMin()) newScale = zoomSlider.getMin();
            if (newScale > zoomSlider.getMax()) newScale = zoomSlider.getMax();

            // Lưu lại vị trí viewport hiện tại
            double oldHValue = scrollPane.getHvalue();
            double oldVValue = scrollPane.getVvalue();

            // Áp dụng tỷ lệ mới
            templatePane.setScaleX(newScale);
            templatePane.setScaleY(newScale);
            zoomSlider.setValue(newScale);

            // Chạy lại layout để cập nhật kích thước, sau đó khôi phục vị trí viewport
            Platform.runLater(() -> {
                scrollPane.setHvalue(oldHValue);
                scrollPane.setVvalue(oldVValue);
            });

            //System.out.println("Zooming: scale=" + newScale);
            event.consume();
        });

        // Liên kết thanh trượt Zoom với tỷ lệ
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            templatePane.setScaleX(newVal.doubleValue());
            templatePane.setScaleY(newVal.doubleValue());
            //System.out.println("Slider Zoom: scale=" + newVal.doubleValue());
        });

        // Thêm dòng này để xử lý việc bỏ chọn
        overlayPane.setOnMousePressed(event -> {
            selectNode(null); // Gọi phương thức mới để bỏ chọn
        });

        // Ban đầu đặt tỷ lệ và căn giữa
        templatePane.setScaleX(0.5);
        templatePane.setScaleY(0.5);
        zoomSlider.setValue(0.5);
        Platform.runLater(() -> {
            scrollPane.setHvalue(0.5);
            scrollPane.setVvalue(0.5);
        });

        // 6. Thiết lập Kéo và Thả (Drag and Drop)
        overlayPane.setOnDragOver(event -> {
            if (event.getGestureSource() != overlayPane && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        overlayPane.setOnDragDropped(this::handleSmartDrop);

        // 7. Thiết lập các slider X/Y Position
        xPosSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (selectedNode != null) {
                selectedNode.setLayoutX(newVal.doubleValue());
            }
        });

        yPosSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (selectedNode != null) {
                selectedNode.setLayoutY(newVal.doubleValue());
            }
        });

        // 8. Thiết lập lưới hiển thị ảnh (imageGrid)
        imageGrid.setItems(imageFiles);
        imageGrid.setCellFactory(listView -> new ListCell<>() {
            private final ImageView imageView = new ImageView();
            {
                imageView.setFitWidth(50);
                imageView.setFitHeight(50);
                imageView.setPreserveRatio(true);
            }
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                if (empty || file == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(file.getName());
                    imageView.setImage(new Image(file.toURI().toString()));
                    setGraphic(imageView);
                    setOnDragDetected(event -> {
                        Dragboard db = startDragAndDrop(TransferMode.MOVE);
                        ClipboardContent content = new ClipboardContent();
                        content.putString(file.getName());
                        db.setContent(content);
                        event.consume();
                    });
                    setOnMouseClicked(event -> {
                        if (event.getClickCount() == 1) {
                            currentImageName = file.getName();
                            findImageViewByName(file.getName()).ifPresent(iv -> {
                                updateSliders(iv.getX(), iv.getY());
                                highlightImage(file.getName());
                            });
                        }
                    });
                    setContextMenu(createContextMenu(file));
                }
            }
        });

        // 9. Thiết lập các giá trị ban đầu và binding
        statusLabel.setText("Initialized. Load a template to begin.");

        templateImageView.fitWidthProperty().bind(templatePane.widthProperty());
        templateImageView.fitHeightProperty().bind(templatePane.heightProperty());

        // 10. Tải và thiết lập icon cho nút ẩn/hiện
        try {
            // Tải ảnh từ thư mục resources
            Image iconVisible = new Image(getClass().getResourceAsStream("/icon/view.png"));
            Image iconHidden = new Image(getClass().getResourceAsStream("/icon/hide.png"));

            // Tạo các ImageView và thiết lập kích thước
            iconViewVisible = new ImageView(iconVisible);
            iconViewVisible.setFitWidth(16);
            iconViewVisible.setFitHeight(16);

            iconViewHidden = new ImageView(iconHidden);
            iconViewHidden.setFitWidth(16);
            iconViewHidden.setFitHeight(16);

        } catch (Exception e) {
            System.err.println("Không thể tải icon ẩn/hiện, sử dụng text thay thế.");
            e.printStackTrace();
        }

        // Cập nhật icon ban đầu cho button
        updateVisibilityButtonIcon();

    }

    // EventHandler để xử lý logic lia (pan) khung nhìn
    private final EventHandler<MouseEvent> panHandler = event -> {
        if (event.isPrimaryButtonDown()) {
            double deltaX = event.getX() - pressTranslateX[0];
            double deltaY = event.getY() - pressTranslateY[0];

            Node content = scrollPane.getContent();
            double newHValue = scrollPane.getHvalue() - deltaX / content.getLayoutBounds().getWidth();
            double newVValue = scrollPane.getVvalue() - deltaY / content.getLayoutBounds().getHeight();

            scrollPane.setHvalue(clamp(newHValue, 0, 1));
            scrollPane.setVvalue(clamp(newVValue, 0, 1));

            pressTranslateX[0] = event.getX();
            pressTranslateY[0] = event.getY();
        }
    };

    // Bật chức năng lia của ScrollPane
    private void enablePanePanning() {
        scrollPane.setOnMouseDragged(panHandler);
    }

    // Tắt chức năng lia của ScrollPane
    private void disablePanePanning() {
        scrollPane.setOnMouseDragged(null);
    }

    // Cập nhật giá trị của các thanh trượt X/Y theo vị trí của node đang được chọn
    private void updateSlidersForSelectedNode() {
        if (selectedNode == null) return;

        // Chỉ cần cập nhật giá trị của slider, binding sẽ cập nhật text field
        xPosSlider.setValue(selectedNode.getLayoutX());
        yPosSlider.setValue(selectedNode.getLayoutY());
    }

    /**
     * Tạo một đối tượng ảnh ResizableNode mới và thêm nó vào vùng làm việc.
     * @param imageFile File ảnh để hiển thị.
     * @param x Tọa độ X ban đầu.
     * @param y Tọa độ Y ban đầu.
     * @param width Chiều rộng ban đầu (-1 để tự động tính).
     * @param height Chiều cao ban đầu (-1 để tự động tính).
     */
    private void updateImagePosition(File imageFile, double x, double y, double width, double height) {
        Image image = new Image(imageFile.toURI().toString());

        double initialImageWidth = (width > 0) ? width : templatePane.getPrefWidth() / 4.0;
        double initialImageHeight = (height > 0) ? height : -1; // Để ResizableNode tự tính

        ResizableNode resizableNode = new ResizableNode(image, this::selectNode, templatePane.scaleXProperty(), initialImageWidth, this::disablePanePanning, this::enablePanePanning);

        if (initialImageHeight > 0) {
            resizableNode.setPrefHeight(initialImageHeight);
        }

        resizableNode.setLayoutX(x);
        resizableNode.setLayoutY(y);
        resizableNode.setId(imageFile.getName());
        overlayPane.getChildren().add(resizableNode);
        selectNode(resizableNode);
    }

    /**
     * Xử lý logic chọn một node ảnh. Bỏ chọn node cũ và chọn node mới.
     * @param node Node được chọn, hoặc null để bỏ chọn tất cả.
     */
    private void selectNode(ResizableNode node) {
        // Bỏ chọn node cũ nếu có
        if (selectedNode != null) {
            selectedNode.setSelected(false);
        }

        selectedNode = node; // Cập nhật node đang được chọn

        // Nếu có node mới được chọn
        if (selectedNode != null) {
            selectedNode.setSelected(true);
            selectedNode.toFront(); // FIX: Đưa node được chọn lên trên cùng!
            updateSlidersForSelectedNode();
        }
    }

    private java.util.Optional<ImageView> findImageViewByName(String name) {
        return overlayPane.getChildren().stream()
                .filter(node -> node instanceof ImageView && name.equals(node.getId()))
                .map(node -> (ImageView) node)
                .findFirst();
    }

    private File findFileByName(String name) {
        return imageFiles.stream()
                .filter(f -> f.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private void highlightImage(String imageName) {
        overlayPane.getChildren().forEach(node -> {
            if (node instanceof ImageView) {
                node.setEffect(node.getId().equals(imageName) ? new javafx.scene.effect.DropShadow() : null);
            }
        });
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateSliders(double x, double y) {
        xPosSlider.setValue(x);
        yPosSlider.setValue(y);
    }

    // --- Các phương thức xử lý sự kiện (handle) ---

    @FXML
    private void handleSelectPsd() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Files", "*.png"));
        File file = chooser.showOpenDialog(templatePane.getScene().getWindow());
        if (file != null) {
            psdFrameField.setText(file.getAbsolutePath());
            Image image = new Image(file.toURI().toString());

            // FIX: Đặt kích thước của vùng làm việc (pane) theo kích thước thực của ảnh
            double width = image.getWidth();
            double height = image.getHeight();
            templatePane.setPrefSize(width, height);
            overlayPane.setPrefSize(width, height); // Cả overlay cũng phải theo

            // FIX: Đảm bảo ImageView giữ đúng tỷ lệ khung hình
            templateImageView.setPreserveRatio(true);
            templateImageView.setImage(image);

            // ImageView sẽ tự động vừa với kích thước của templatePane nhờ binding
            // Chúng ta sẽ thêm binding này trong initialize()

            xPosSlider.setMax(width);
            yPosSlider.setMax(height);
            statusLabel.setText("Template loaded: " + width + "x" + height);

            Platform.runLater(() -> {
                scrollPane.setHvalue(0.5);
                scrollPane.setVvalue(0.5);
            });
        }
    }

    @FXML
    private void handleZoomIn() {
        zoomSlider.setValue(clamp(zoomSlider.getValue() + 0.1, zoomSlider.getMin(), zoomSlider.getMax()));
    }

    @FXML
    private void handleZoomOut() {
        zoomSlider.setValue(clamp(zoomSlider.getValue() - 0.1, zoomSlider.getMin(), zoomSlider.getMax()));
    }

    @FXML
    private void handleSelectImport() {
        DirectoryChooser chooser = new DirectoryChooser();
        File dir = chooser.showDialog(templatePane.getScene().getWindow());
        if (dir != null) {
            importFolderField.setText(dir.getAbsolutePath());
            // Load initial images
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png"));
            if (files != null) {
                Platform.runLater(() -> {
                    imageFiles.setAll(files);
                    System.out.println("Loaded " + files.length + " images from import folder: " + dir.getAbsolutePath());
                });
            } else {
                System.out.println("No images found in import folder: " + dir.getAbsolutePath());
            }
            // Initialize FolderWatcher
            if (folderWatcher != null) {
                folderWatcher.stop();
            }
            try {
                folderWatcher = new FolderWatcher(dir.getAbsolutePath(), file -> {
                    Platform.runLater(() -> {
                        File fileToAdd = file.toFile();
                        if (!imageFiles.contains(fileToAdd)) {
                            imageFiles.add(fileToAdd);
                            System.out.println("FolderWatcher added: " + file.getFileName().toString());
                        }
                    });
                });
                if (isRunning) {
                    folderWatcher.start();
                    System.out.println("FolderWatcher started for: " + dir.getAbsolutePath());
                }
            } catch (IOException e) {
                System.err.println("Failed to initialize FolderWatcher: " + e.getMessage());
                statusLabel.setText("Error: Failed to watch folder");
            }
        }
    }

    @FXML
    private void handleSelectExport() {
        DirectoryChooser chooser = new DirectoryChooser();
        File dir = chooser.showDialog(templatePane.getScene().getWindow());
        if (dir != null) {
            exportFolderField.setText(dir.getAbsolutePath());
            System.out.println("Export folder selected: " + dir.getAbsolutePath());
        }
    }

    @FXML
    private void handleSelectPreset() {
        FileChooser chooser = new FileChooser();

        // FIX: Thêm bộ lọc cho cả file .png và .cube
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Preset Files", "*.png", "*.cube"),
                new FileChooser.ExtensionFilter("Hald CLUT Images", "*.png"),
                new FileChooser.ExtensionFilter("Cube LUT Files", "*.cube")
        );

        File file = chooser.showOpenDialog(templatePane.getScene().getWindow());
        if (file != null) {
            presetField.setText(file.getAbsolutePath());
            System.out.println("Preset selected: " + file.getAbsolutePath());
        }
    }

    @FXML
    private void handleSelectImage() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.png"));
        List<File> files = chooser.showOpenMultipleDialog(templatePane.getScene().getWindow());
        if (files != null) {
            Platform.runLater(() -> {
                imageFiles.addAll(files);
                System.out.println("Manually added " + files.size() + " images");
            });
        }
    }

    @FXML
    private void handleStartStop() {
        isRunning = !isRunning;
        startStopButton.setText(isRunning ? "Stop Watching" : "Start Watching");
        statusLabel.setText(isRunning ? "Watching import folder..." : "Stopped watching");
        if (isRunning && folderWatcher != null && !importFolderField.getText().isEmpty()) {
            folderWatcher.start();
            System.out.println("FolderWatcher started for: " + importFolderField.getText());
        } else if (!isRunning && folderWatcher != null) {
            folderWatcher.stop();
            System.out.println("FolderWatcher stopped");
        } else if (isRunning && importFolderField.getText().isEmpty()) {
            statusLabel.setText("Error: Select an import folder first");
            isRunning = false;
            startStopButton.setText("Start Watching");
            System.out.println("Cannot start FolderWatcher: No import folder selected");
        }
    }

    @FXML
    private void handleProcessImages() {
        // Thu thập thông tin cần thiết từ giao diện
        File templateFile = new File(psdFrameField.getText());
        File presetFile = new File(presetField.getText());
        File exportFolder = new File(exportFolderField.getText());

        // Đọc kích thước của ảnh template
        final int templateWidth;
        final int templateHeight;
        try {
            if (!templateFile.exists()) {
                statusLabel.setText("Error: Template file not found!");
                return;
            }
            BufferedImage bimg = ImageIO.read(templateFile);
            templateWidth = bimg.getWidth();
            templateHeight = bimg.getHeight();
        } catch (IOException e) {
            statusLabel.setText("Error reading template file dimensions.");
            e.printStackTrace();
            return;
        }

        // Bước 1: Lấy danh sách tất cả các node ảnh hợp lệ
        List<ResizableNode> nodes = overlayPane.getChildren().stream()
                .filter(node -> node instanceof ResizableNode)
                .map(node -> (ResizableNode) node)
                .collect(Collectors.toList());

        if (nodes.isEmpty()) {
            statusLabel.setText("No images to process. Please drag images onto the template.");
            return;
        }

        // Bước 2: Từ danh sách nodes, tạo ra danh sách VỊ TRÍ (positions)
        List<ImagePosition> positions = nodes.stream()
                .map(node -> new ImagePosition(
                        node.getLayoutX(),
                        node.getLayoutY(),
                        node.getPrefWidth(),
                        node.getPrefHeight()
                ))
                .collect(Collectors.toList());

        // Bước 3: Từ danh sách nodes, tạo ra danh sách FILE ẢNH (imagesToProcess)
        List<File> imagesToProcess = nodes.stream()
                .map(node -> findFileByName(node.getId()))
                .filter(Objects::nonNull) // Loại bỏ các file không tìm thấy (null)
                .collect(Collectors.toList());

        // Kiểm tra lại sau khi đã lọc
        if (nodes.size() != imagesToProcess.size()){
            System.err.println("Warning: Some images on canvas could not be found in the image list.");
        }

        statusLabel.setText("Processing " + positions.size() + " images...");

        // Tạo một Task để chạy việc xử lý trên luồng nền
        Task<File> processingTask = new Task<>() {
            @Override
            protected File call() throws Exception {
                ImageProcessor processor = new ImageProcessor();
                // Truyền thêm templateWidth và templateHeight vào phương thức
                return processor.processImages(imagesToProcess, presetFile, templateFile, templateWidth, templateHeight, exportFolder, positions);
            }
        };

        // Xử lý khi Task thành công
        processingTask.setOnSucceeded(event -> {
            File outputFile = processingTask.getValue();
            statusLabel.setText("Success! Output saved to: " + outputFile.getName());
            System.out.println("Processing finished successfully. Output: " + outputFile.getAbsolutePath());
        });

        // Xử lý khi Task thất bại
        processingTask.setOnFailed(event -> {
            Throwable e = processingTask.getException();
            statusLabel.setText("Error: " + e.getMessage());
            System.err.println("Processing failed:");
            e.printStackTrace();
        });

        // Khởi chạy Task
        new Thread(processingTask).start();
    }

    // Phương thức xử lý sự kiện click
    @FXML
    private void handleToggleFrameVisibility() {
        isFrameVisible = !isFrameVisible; // Đảo ngược trạng thái
        templateImageView.setVisible(isFrameVisible); // Ẩn/hiện frame
        updateVisibilityButtonIcon(); // Cập nhật lại icon
    }

    private void updateVisibilityButtonIcon() {
        // Nếu các icon đã được tải thành công
        if (iconViewVisible != null && iconViewHidden != null) {
            if (isFrameVisible) {
                toggleFrameVisibilityButton.setGraphic(iconViewVisible);
            } else {
                toggleFrameVisibilityButton.setGraphic(iconViewHidden);
            }
            toggleFrameVisibilityButton.setText(""); // Xóa text nếu có
        } else {
            // Phương án dự phòng nếu không tải được ảnh
            toggleFrameVisibilityButton.setGraphic(null);
            toggleFrameVisibilityButton.setText(isFrameVisible ? "Ẩn" : "Hiện");
        }
    }

    @FXML
    private void handleSaveConfig() {
        List<ImagePosition> currentPositions = overlayPane.getChildren().stream()
                .filter(node -> node instanceof ResizableNode)
                .map(node -> (ResizableNode) node)
                .map(node -> new ImagePosition(node.getLayoutX(), node.getLayoutY(), node.getPrefWidth(), node.getPrefHeight()))
                .collect(Collectors.toList());

        if (currentPositions.isEmpty()) {
            statusLabel.setText("Nothing to save. Add images first.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("My Template");
        dialog.setTitle("Save Configuration");
        dialog.setHeaderText("Enter a name for your new configuration.");
        dialog.setContentText("Name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            try {
                TemplateConfig config = new TemplateConfig(name, currentPositions);
                configManager.saveConfig(config);
                statusLabel.setText("Configuration '" + name + "' saved.");
                loadAndDisplayConfigs(); // Tải lại danh sách
            } catch (IOException e) {
                statusLabel.setText("Error saving config: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @FXML
    private void handleClearCanvas() {
        // Xóa cả ảnh và placeholder
        overlayPane.getChildren().removeIf(node -> node instanceof ResizableNode || node.getId() != null && node.getId().equals(PLACEHOLDER_ID));
        statusLabel.setText("Canvas cleared.");
    }

    private void loadAndDisplayConfigs() {
        savedConfigNames.clear();
        List<String> names = configManager.loadAllConfigs().stream()
                .map(TemplateConfig::name)
                .collect(Collectors.toList());
        savedConfigNames.addAll(names);
    }

    private void applyConfigAsPlaceholders(TemplateConfig config) {
        handleClearCanvas(); // Dọn dẹp trước khi áp dụng config mới

        for (com.photobooth.config.ImagePosition pos : config.positions()) {
            Rectangle placeholder = new Rectangle(pos.x(), pos.y(), pos.width(), pos.height());
            placeholder.setFill(Color.web("#007bff", 0.3)); // Màu xanh mờ
            placeholder.setStroke(Color.web("#007bff"));
            placeholder.getStrokeDashArray().addAll(10d, 10d);
            placeholder.setId(PLACEHOLDER_ID); // Đặt ID để nhận dạng
            overlayPane.getChildren().add(placeholder);
        }
        statusLabel.setText("Applied config: " + config.name());
    }


    // --- PHƯƠNG THỨC XỬ LÝ DROP THÔNG MINH --

    private void handleSmartDrop(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasString()) {
            String imageName = db.getString();
            File imageFile = findFileByName(imageName);
            if (imageFile == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            Rectangle targetPlaceholder = null;
            double dropX = event.getX();
            double dropY = event.getY();

            // Duyệt qua các node để tìm placeholder bị thả vào
            for (Node node : overlayPane.getChildren()) {
                if (node instanceof Rectangle && PLACEHOLDER_ID.equals(node.getId())) {
                    if (node.getBoundsInParent().contains(dropX, dropY)) {
                        targetPlaceholder = (Rectangle) node;
                        break;
                    }
                }
            }

            final Rectangle finalTargetPlaceholder = targetPlaceholder; // Cần biến final để dùng trong lambda
            Platform.runLater(() -> {
                if (finalTargetPlaceholder != null) {
                    // SNAP vào vị trí placeholder
                    updateImagePosition(
                            imageFile,
                            finalTargetPlaceholder.getX(),
                            finalTargetPlaceholder.getY(),
                            finalTargetPlaceholder.getWidth(),
                            finalTargetPlaceholder.getHeight()
                    );
                    // Xóa placeholder sau khi đã snap thành công
                    overlayPane.getChildren().remove(finalTargetPlaceholder);
                } else {
                    Point2D localCoords = templatePane.sceneToLocal(event.getSceneX(), event.getSceneY());
                    updateImagePosition(imageFile, localCoords.getX(), localCoords.getY(), -1, -1);
                }
            });

            success = true;
        }
        event.setDropCompleted(success);
        event.consume();
    }

    private ContextMenu createContextMenu(File file) {
        ContextMenu menu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(e -> {
            imageFiles.remove(file);
            findImageViewByName(file.getName()).ifPresent(iv -> overlayPane.getChildren().remove(iv));
            System.out.println("Deleted image: " + file.getName());
        });
        menu.getItems().add(deleteItem);
        return menu;
    }

    public void cleanup() {
        if (debounceExecutor != null) {
            debounceExecutor.shutdownNow();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        if (folderWatcher != null) {
            folderWatcher.stop();
        }
    }

    /*
    @FXML
    private void handleSaveTemplateConfig() {
        // Logic lưu cấu hình
        statusLabel.setText("Template config saved.");
    }

    // Thêm 2 phương thức trợ giúp mới
    private void setupVisibilityButton() {
        updateVisibilityButtonIcon(); // Đặt icon ban đầu
    }*/

}