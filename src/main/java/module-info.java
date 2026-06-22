module com.example.cs202pzdopisivanje {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.sql;
    requires jdk.httpserver;
    requires ons;
    requires java.desktop;
    requires mysql.connector.j;
    requires javafx.base;
    requires junit;


    exports com.example.cs202pzdopisivanje;

    exports com.example.cs202pzdopisivanje.Controllers;
    opens com.example.cs202pzdopisivanje.Controllers to javafx.graphics, javafx.fxml;
    opens com.example.cs202pzdopisivanje to javafx.fxml, javafx.graphics;

}