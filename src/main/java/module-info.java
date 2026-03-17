module com.example.lab1 {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires javafx.graphics;

    opens com.example.lab1 to javafx.fxml;
    exports com.example.lab1;
}