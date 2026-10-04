import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {

        Label nameLabel = new Label("Enter your name:");
        TextField nameField = new TextField();
        Button button = new Button("Say Hello");
        Label resultLabel = new Label();

        button.setOnAction(event -> {
            String name = nameField.getText();
            resultLabel.setText("Hello, " + name + "!");
        });

        VBox vbox = new VBox(10, nameLabel, nameField, button, resultLabel);
        vbox.setAlignment(Pos.CENTER);

        Scene scene = new Scene(vbox, 300, 250);

        stage.setTitle("Hello App");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}