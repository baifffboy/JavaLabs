module com.example.lab {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires javafx.graphics;

    opens com.example.lab to javafx.fxml;
    exports com.example.lab;
}