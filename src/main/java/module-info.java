module com.example.lab {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires javafx.graphics;
    requires jakarta.persistence;
    requires java.sql;
    requires org.hibernate.orm.core;

    opens com.example.lab to javafx.fxml, org.hibernate.orm.core;
    exports com.example.lab;
}