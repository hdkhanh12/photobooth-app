package com.photobooth.ui;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

/**
 * Một thành phần giao diện (Node) tùy chỉnh, hoạt động như một lớp vỏ bao bọc ImageView.
 * Cung cấp các chức năng tương tác nâng cao như kéo-thả để di chuyển và thay đổi kích thước,
 * mô phỏng hành vi của các phần mềm thiết kế chuyên nghiệp như Canva.
 */
public class ResizableNode extends Pane {

    /** ImageView để hiển thị hình ảnh thực tế */
    private final ImageView imageView;
    /** Thuộc tính chỉ đọc (read-only) để lấy tỷ lệ thu phóng hiện tại của khung nhìn chính */
    private final ReadOnlyDoubleProperty viewScale;

    //--- Callbacks để giao tiếp với Controller ---
    /** Callback được gọi khi node này được người dùng chọn */
    private final Consumer<ResizableNode> onSelected;
    /** Callback được gọi khi người dùng bắt đầu kéo (nhấn chuột và di chuyển) */
    private final Runnable onDragStarted;
    /** Callback được gọi khi người dùng kết thúc kéo (nhả chuột) */
    private final Runnable onDragFinished;

    /** Lưu trữ khoảng cách (offset) từ góc trên-trái của node đến vị trí con trỏ khi bắt đầu kéo */
    private double mouseOffsetX;
    private double mouseOffsetY;

    /**
     * Hàm khởi tạo (constructor) cho một đối tượng ảnh có thể tương tác.
     * @param image Ảnh để hiển thị.
     * @param onSelected Callback để thông báo cho controller khi node được chọn.
     * @param viewScale Thuộc tính tỷ lệ thu phóng của canvas.
     * @param initialWidth Chiều rộng ban đầu của node.
     * @param onDragStarted Callback để thông báo bắt đầu kéo.
     * @param onDragFinished Callback để thông báo kết thúc kéo.
     */
    public ResizableNode(Image image, Consumer<ResizableNode> onSelected, ReadOnlyDoubleProperty viewScale, double initialWidth, Runnable onDragStarted, Runnable onDragFinished) {
        this.onSelected = onSelected;
        this.viewScale = viewScale;
        this.onDragStarted = onDragStarted;
        this.onDragFinished = onDragFinished;

        // Khởi tạo ImageView và gắn kết (bind) kích thước của nó với kích thước của Pane này
        // Khi Pane thay đổi kích thước, ImageView sẽ tự động thay đổi theo
        this.imageView = new ImageView(image);
        imageView.fitWidthProperty().bind(this.prefWidthProperty());
        imageView.fitHeightProperty().bind(this.prefHeightProperty());

        // Tính toán và thiết lập kích thước ban đầu trong khi vẫn giữ đúng tỷ lệ khung hình
        double ratio = image.getWidth() / image.getHeight();
        setPrefWidth(initialWidth);
        setPrefHeight(initialWidth / ratio);

        // Thêm ImageView vào làm con của Pane này
        getChildren().add(imageView);

        // Kích hoạt tất cả các trình xử lý sự kiện chuột
        enableInteraction();
    }

    /**
     * Thiết lập các trình xử lý sự kiện chuột chính cho việc kéo-thả di chuyển.
     */
    private void enableInteraction() {
        // Sự kiện khi người dùng nhấn chuột xuống trên node
        this.setOnMousePressed(event -> {
            /* Thông báo cho controller rằng một hành động kéo sắp bắt đầu,
               để nó có thể vô hiệu hóa việc lia (pan) toàn bộ khung vẽ */
            if (onDragStarted != null) {
                onDragStarted.run();
            }
            // Ghi lại vị trí con trỏ chuột tương đối so với góc trên-trái của node
            mouseOffsetX = event.getX();
            mouseOffsetY = event.getY();

            // Thông báo cho controller rằng node này đã được chọn
            if (this.onSelected != null) {
                this.onSelected.accept(this);
            }
            // Đưa node này lên lớp trên cùng để nó không bị các node khác che khuất
            this.toFront();
            event.consume();
        });

        // Sự kiện khi người dùng nhả chuột
        this.setOnMouseReleased(event -> {
            /* Thông báo cho controller rằng hành động kéo đã kết thúc,
               để nó có thể kích hoạt lại việc lia (pan) toàn bộ khung vẽ */
            if (onDragFinished != null) {
                onDragFinished.run();
            }
        });

        // Sự kiện khi người dùng kéo chuột (nhấn giữ và di chuyển)
        this.setOnMouseDragged(event -> {
            // Lấy tọa độ của con trỏ trong hệ tọa độ của node cha (overlayPane)
            // Phương thức sceneToLocal đã tự động xử lý các vấn đề về thu phóng và lia
            Point2D mouseInParent = getParent().sceneToLocal(event.getSceneX(), event.getSceneY());

            // Thiết lập vị trí mới cho node bằng cách lấy tọa độ chuột trừ đi khoảng cách offset đã lưu
            setLayoutX(mouseInParent.getX() - mouseOffsetX);
            setLayoutY(mouseInParent.getY() - mouseOffsetY);
            event.consume();
        });
    }

    /**
     * Hiển thị hoặc ẩn các điểm neo (anchor) thay đổi kích thước.
     * @param selected true để hiển thị, false để ẩn.
     */
    public void setSelected(boolean selected) {
        // Luôn xóa các điểm neo cũ trước để tránh bị trùng lặp
        getChildren().removeIf(node -> node instanceof Circle);
        if (selected) {
            // Tạo 8 điểm neo tại các góc và các cạnh.
            Anchor se = new Anchor(Cursor.SE_RESIZE, getPrefWidth(), getPrefHeight());
            Anchor sw = new Anchor(Cursor.SW_RESIZE, 0, getPrefHeight());
            Anchor ne = new Anchor(Cursor.NE_RESIZE, getPrefWidth(), 0);
            Anchor nw = new Anchor(Cursor.NW_RESIZE, 0, 0);
            Anchor n = new Anchor(Cursor.N_RESIZE, getPrefWidth() / 2, 0);
            Anchor s = new Anchor(Cursor.S_RESIZE, getPrefWidth() / 2, getPrefHeight());
            Anchor e = new Anchor(Cursor.E_RESIZE, getPrefWidth(), getPrefHeight() / 2);
            Anchor w = new Anchor(Cursor.W_RESIZE, 0, getPrefHeight() / 2);

            // Gắn kết (bind) vị trí của các điểm neo với kích thước của node
            // Khi node được resize, các điểm neo sẽ tự động di chuyển theo
            se.centerXProperty().bind(this.prefWidthProperty());
            se.centerYProperty().bind(this.prefHeightProperty());
            sw.centerYProperty().bind(this.prefHeightProperty());
            ne.centerXProperty().bind(this.prefWidthProperty());
            n.centerXProperty().bind(this.prefWidthProperty().divide(2));
            s.centerXProperty().bind(this.prefWidthProperty().divide(2));
            s.centerYProperty().bind(this.prefHeightProperty());
            e.centerXProperty().bind(this.prefWidthProperty());
            e.centerYProperty().bind(this.prefHeightProperty().divide(2));
            w.centerYProperty().bind(this.prefHeightProperty().divide(2));

            // Thêm tất cả các điểm neo vào node
            getChildren().addAll(se, sw, ne, nw, n, s, e, w);
        }
    }

    /**
     * Lớp nội tại (inner class) đại diện cho một điểm neo (hình tròn) dùng để thay đổi kích thước.
     */
    private class Anchor extends Circle {
        // Lưu trữ trạng thái (vị trí, kích thước) của node chính khi bắt đầu kéo điểm neo.
        private double startX, startY, startWidth, startHeight;

        public Anchor(Cursor cursor, double initialX, double initialY) {
            // Khởi tạo hình tròn với bán kính ban đầu.
            super(initialX, initialY, 6 / viewScale.get());
            setFill(Color.DODGERBLUE);
            setStroke(Color.WHITE);
            setStrokeWidth(1.5 / viewScale.get());
            setCursor(cursor); // Đổi hình con trỏ chuột khi di qua.

            // Một mẹo nhỏ: gắn kết bán kính và độ dày viền ngược với tỷ lệ thu phóng.
            // Điều này làm cho các điểm neo trông có vẻ luôn có cùng kích thước trên màn hình,
            // bất kể người dùng phóng to hay thu nhỏ.
            radiusProperty().bind(viewScale.multiply(6.0).divide(viewScale.get() * viewScale.get()));
            strokeWidthProperty().bind(viewScale.multiply(1.5).divide(viewScale.get() * viewScale.get()));

            setupMouseEvents();
        }

        /** Thiết lập các trình xử lý sự kiện cho riêng điểm neo này. */
        private void setupMouseEvents() {
            this.setOnMousePressed(event -> {
                if (onDragStarted != null) onDragStarted.run(); // Tắt panning

                // Ghi lại trạng thái của node cha (ResizableNode) khi bắt đầu resize.
                startX = getLayoutX();
                startY = getLayoutY();
                startWidth = getPrefWidth();
                startHeight = getPrefHeight();
                event.consume();
            });

            this.setOnMouseReleased(event -> {
                if (onDragFinished != null) onDragFinished.run(); // Bật lại panning
            });

            this.setOnMouseDragged(event -> {
                // Lấy tọa độ chuột trong hệ tọa độ của node cha của cha (tức là overlayPane).
                Point2D mouseInParent = getParent().getParent().sceneToLocal(event.getSceneX(), event.getSceneY());

                double newWidth = startWidth;
                double newHeight = startHeight;
                double newX = startX;
                double newY = startY;

                Cursor cursor = getCursor();
                // Logic tính toán kích thước/vị trí mới dựa trên loại con trỏ (loại điểm neo).
                // Mục tiêu là giữ cho góc đối diện của điểm neo đang kéo được cố định.
                if (cursor == Cursor.SE_RESIZE || cursor == Cursor.E_RESIZE || cursor == Cursor.NE_RESIZE) {
                    newWidth = mouseInParent.getX() - startX;
                }
                if (cursor == Cursor.SE_RESIZE || cursor == Cursor.S_RESIZE || cursor == Cursor.SW_RESIZE) {
                    newHeight = mouseInParent.getY() - startY;
                }
                if (cursor == Cursor.NW_RESIZE || cursor == Cursor.W_RESIZE || cursor == Cursor.SW_RESIZE) {
                    newWidth = startX + startWidth - mouseInParent.getX();
                    newX = mouseInParent.getX();
                }
                if (cursor == Cursor.NW_RESIZE || cursor == Cursor.N_RESIZE || cursor == Cursor.NE_RESIZE) {
                    newHeight = startY + startHeight - mouseInParent.getY();
                    newY = mouseInParent.getY();
                }

                // Duy trì tỷ lệ khung hình gốc của ảnh.
                double ratio = imageView.getImage().getWidth() / imageView.getImage().getHeight();
                if (cursor == Cursor.N_RESIZE || cursor == Cursor.S_RESIZE) {
                    newWidth = newHeight * ratio; // Kéo dọc -> chiều cao quyết định chiều rộng.
                } else if (cursor == Cursor.E_RESIZE || cursor == Cursor.W_RESIZE) {
                    newHeight = newWidth / ratio; // Kéo ngang -> chiều rộng quyết định chiều cao.
                } else { // Kéo góc
                    if (newWidth / newHeight > ratio) {
                        newWidth = newHeight * ratio; // Nếu hình mới quá rộng -> giảm chiều rộng.
                    } else {
                        newHeight = newWidth / ratio; // Nếu hình mới quá cao -> giảm chiều cao.
                    }
                }

                // Áp dụng các giá trị mới, với điều kiện kích thước không quá nhỏ.
                if (newWidth > 20) {
                    setPrefWidth(newWidth);
                    setLayoutX(newX);
                }
                if (newHeight > 20) {
                    setPrefHeight(newHeight);
                    setLayoutY(newY);
                }
                event.consume();
            });
        }
    }
}