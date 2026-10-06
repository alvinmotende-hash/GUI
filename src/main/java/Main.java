import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class Main extends Application {

    // Inner Data Model for Tasks
    public static class Task {
        private final String title;
        private final String category;
        private boolean completed;

        public Task(String title, String category) {
            this.title = title;
            this.category = category;
            this.completed = false;
        }

        public String getTitle() { return title; }
        public String getCategory() { return category; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }

        @Override
        public String toString() {
            return (completed ? "[✔] " : "[ ] ") + title + " (" + category + ")";
        }
    }

    private final ObservableList<Task> tasks = FXCollections.observableArrayList();
    private final ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
    private ProgressBar progressBar;
    private Label progressLabel;

    @Override
    public void start(Stage stage) {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Dashboard Tabs
        Tab taskTab = new Tab("📋 Task Manager", createTasksView());
        Tab chartTab = new Tab("📊 Analytics", createAnalyticsView());

        tabPane.getTabs().addAll(taskTab, chartTab);

        Scene scene = new Scene(tabPane, 650, 480);

        stage.setTitle("JavaFX Productivity Dashboard");
        stage.setScene(scene);
        stage.show();
    }

    // Tab 1: Task Management View
    private VBox createTasksView() {
        Label header = new Label("Task Manager");
        header.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        TextField taskInput = new TextField();
        taskInput.setPromptText("Enter new task title...");

        ComboBox<String> categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll("Work", "Personal", "Study");
        categoryBox.setValue("Work");

        Button addButton = new Button("Add Task");
        HBox inputRow = new HBox(10, taskInput, categoryBox, addButton);
        HBox.setHgrow(taskInput, Priority.ALWAYS);

        ListView<Task> listView = new ListView<>(tasks);

        Button toggleButton = new Button("Toggle Complete");
        Button deleteButton = new Button("Delete Task");
        HBox actionRow = new HBox(10, toggleButton, deleteButton);

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressLabel = new Label("Completion: 0%");

        // Action Handlers
        addButton.setOnAction(e -> {
            String title = taskInput.getText().trim();
            if (!title.isEmpty()) {
                tasks.add(new Task(title, categoryBox.getValue()));
                taskInput.clear();
                updateMetrics();
            }
        });

        toggleButton.setOnAction(e -> {
            Task selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selected.setCompleted(!selected.isCompleted());
                listView.refresh();
                updateMetrics();
            }
        });

        deleteButton.setOnAction(e -> {
            Task selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                tasks.remove(selected);
                updateMetrics();
            }
        });

        VBox layout = new VBox(12, header, inputRow, listView, actionRow, progressBar, progressLabel);
        layout.setPadding(new Insets(20));
        return layout;
    }

    // Tab 2: Analytics Pie Chart View
    private VBox createAnalyticsView() {
        Label header = new Label("Real-Time Overview");
        header.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        PieChart pieChart = new PieChart(pieData);
        pieChart.setTitle("Task Breakdown");

        VBox layout = new VBox(15, header, pieChart);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);
        return layout;
    }

    // Dynamic Data Updates
    private void updateMetrics() {
        if (tasks.isEmpty()) {
            progressBar.setProgress(0);
            progressLabel.setText("Completion: 0%");
            pieData.clear();
            return;
        }

        long completedCount = tasks.stream().filter(Task::isCompleted).count();
        long pendingCount = tasks.size() - completedCount;

        double ratio = (double) completedCount / tasks.size();
        progressBar.setProgress(ratio);
        progressLabel.setText(String.format("Completion: %.0f%% (%d/%d)", ratio * 100, completedCount, tasks.size()));

        pieData.setAll(
            new PieChart.Data("Completed", completedCount),
            new PieChart.Data("Pending", pendingCount)
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}