import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class Main extends Application {

    // Enhanced Data Model for Tasks
    public static class Task {
        private int id;
        private String title;
        private final String category;
        private final String priority;
        private final LocalDate dueDate;
        private boolean completed;

        public Task(String title, String category, String priority, LocalDate dueDate) {
            this(-1, title, category, priority, dueDate, false);
        }

        public Task(int id, String title, String category, String priority, LocalDate dueDate, boolean completed) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.priority = priority;
            this.dueDate = dueDate;
            this.completed = completed;
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCategory() { return category; }
        public String getPriority() { return priority; }
        public LocalDate getDueDate() { return dueDate; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }

        public boolean isOverdue() {
            return !completed && dueDate != null && dueDate.isBefore(LocalDate.now());
        }

        @Override
        public String toString() {
            return title + " (" + category + ")";
        }
    }

    private static final List<String> CATEGORIES = List.of("Work", "Personal", "Study", "Health");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd");

    private final ObservableList<Task> tasks = FXCollections.observableArrayList();
    private FilteredList<Task> filteredTasks;
    private Runnable refreshFilterPredicate;

    // Dashboard & Analytics UI references
    private ProgressBar progressBar;
    private Label progressLabel;
    private Label totalKpiLabel;
    private Label rateKpiLabel;
    private Label overdueKpiLabel;
    private Label focusKpiLabel;
    private final ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
    private BarChart<String, Number> categoryBarChart;

    // Pomodoro state
    private static final int FOCUS_SECONDS = 25 * 60;
    private static final int BREAK_SECONDS = 5 * 60;
    private int remainingSeconds = FOCUS_SECONDS;
    private boolean isFocusMode = true;
    private Timeline timerTimeline;

    // Theme state
    private boolean darkMode = false;

    @Override
    public void start(Stage stage) {
        DatabaseHandler.initializeDatabase();
        tasks.addAll(DatabaseHandler.loadTasks());
        filteredTasks = new FilteredList<>(tasks, t -> true);

        // Top App Bar with Theme Toggle
        Label appTitle = new Label("Productivity Dashboard");
        appTitle.getStyleClass().add("app-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToggleButton themeToggle = new ToggleButton("Dark Mode");
        themeToggle.getStyleClass().add("theme-toggle");

        HBox topBar = new HBox(10, appTitle, spacer, themeToggle);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 20, 8, 20));

        // Main Tabs
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab taskTab = new Tab("Task Manager", createTasksView());
        Tab focusTab = new Tab("Focus Timer", createPomodoroView());
        Tab chartTab = new Tab("Analytics", createAnalyticsView());

        tabPane.getTabs().addAll(taskTab, focusTab, chartTab);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        VBox root = new VBox(topBar, tabPane);
        Scene scene = new Scene(root, 820, 580);
        applyTheme(scene);

        themeToggle.setOnAction(e -> {
            darkMode = themeToggle.isSelected();
            themeToggle.setText(darkMode ? "Light Mode" : "Dark Mode");
            applyTheme(scene);
        });

        stage.setTitle("JavaFX Productivity Dashboard");
        stage.setScene(scene);
        stage.show();

        updateMetrics();
    }

    // ==================== TAB 1: TASK MANAGER ====================
    private VBox createTasksView() {
        // 1. Task Creation Row
        TextField taskInput = new TextField();
        taskInput.setPromptText("What needs to get done?");
        HBox.setHgrow(taskInput, Priority.ALWAYS);

        ComboBox<String> categoryBox = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        categoryBox.setValue("Work");

        ComboBox<String> priorityBox = new ComboBox<>(FXCollections.observableArrayList("High", "Medium", "Low"));
        priorityBox.setValue("Medium");

        DatePicker dueDatePicker = new DatePicker();
        dueDatePicker.setPromptText("Due date");
        dueDatePicker.setPrefWidth(125);

        Button addButton = new Button("+ Add Task");
        addButton.getStyleClass().add("primary-btn");

        HBox inputRow = new HBox(8, taskInput, categoryBox, priorityBox, dueDatePicker, addButton);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        // 2. Search & Filter Row
        TextField searchField = new TextField();
        searchField.setPromptText("Search tasks...");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        ComboBox<String> statusFilter = new ComboBox<>(FXCollections.observableArrayList("All", "Active", "Completed"));
        statusFilter.setValue("All");

        ComboBox<String> catFilter = new ComboBox<>();
        catFilter.getItems().add("All Categories");
        catFilter.getItems().addAll(CATEGORIES);
        catFilter.setValue("All Categories");

        HBox filterRow = new HBox(8, searchField, statusFilter, catFilter);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        refreshFilterPredicate = () -> {
            String query = searchField.getText().trim().toLowerCase();
            String status = statusFilter.getValue();
            String cat = catFilter.getValue();

            filteredTasks.setPredicate(task -> {
                boolean matchesQuery = query.isEmpty() || task.getTitle().toLowerCase().contains(query);
                boolean matchesStatus = switch (status) {
                    case "Active" -> !task.isCompleted();
                    case "Completed" -> task.isCompleted();
                    default -> true;
                };
                boolean matchesCat = "All Categories".equals(cat) || task.getCategory().equals(cat);
                return matchesQuery && matchesStatus && matchesCat;
            });
        };

        searchField.textProperty().addListener((obs, o, n) -> refreshFilterPredicate.run());
        statusFilter.valueProperty().addListener((obs, o, n) -> refreshFilterPredicate.run());
        catFilter.valueProperty().addListener((obs, o, n) -> refreshFilterPredicate.run());

        // 3. Custom Interactive ListView
        ListView<Task> listView = new ListView<>(filteredTasks);
        listView.setPlaceholder(new Label("No tasks match your filter. Add one above!"));
        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);
                if (empty || task == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                CheckBox checkBox = new CheckBox();
                checkBox.setSelected(task.isCompleted());
                checkBox.setOnAction(e -> {
                    task.setCompleted(checkBox.isSelected());
                    DatabaseHandler.updateTaskStatus(task.getId(), task.isCompleted());
                    refreshFilterPredicate.run();
                    listView.refresh();
                    updateMetrics();
                });

                Label titleLabel = new Label(task.getTitle());
                titleLabel.getStyleClass().add("task-title");
                if (task.isCompleted()) {
                    titleLabel.getStyleClass().add("task-completed");
                }

                // Priority Badge
                Label priorityBadge = new Label(task.getPriority().toUpperCase());
                priorityBadge.getStyleClass().addAll("badge", "priority-" + task.getPriority().toLowerCase());

                // Category Badge
                Label categoryBadge = new Label(task.getCategory());
                categoryBadge.getStyleClass().addAll("badge", "category-badge");

                Region rowSpacer = new Region();
                HBox.setHgrow(rowSpacer, Priority.ALWAYS);

                HBox row = new HBox(10, checkBox, titleLabel, rowSpacer, priorityBadge, categoryBadge);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(4, 6, 4, 6));

                // Due Date Badge (if present)
                if (task.getDueDate() != null) {
                    String dateText = task.isOverdue()
                            ? "OVERDUE (" + task.getDueDate().format(DATE_FMT) + ")"
                            : task.getDueDate().format(DATE_FMT);
                    Label dateLabel = new Label(dateText);
                    dateLabel.getStyleClass().add("date-badge");
                    if (task.isOverdue()) {
                        dateLabel.getStyleClass().add("date-overdue");
                    }
                    row.getChildren().add(dateLabel);
                }

                // Quick Delete Button per row
                Button rowDeleteBtn = new Button("✕");
                rowDeleteBtn.getStyleClass().add("row-delete-btn");
                rowDeleteBtn.setOnAction(e -> {
                    DatabaseHandler.deleteTask(task.getId());
                    tasks.remove(task);
                    updateMetrics();
                });
                row.getChildren().add(rowDeleteBtn);

                setText(null);
                setGraphic(row);
            }
        });

        // Double-click task row to edit title
        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Task selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    TextInputDialog dialog = new TextInputDialog(selected.getTitle());
                    dialog.setTitle("Edit Task");
                    dialog.setHeaderText("Update task title");
                    dialog.setContentText("Title:");
                    dialog.showAndWait().ifPresent(newTitle -> {
                        String trimmed = newTitle.trim();
                        if (!trimmed.isEmpty()) {
                            selected.setTitle(trimmed);
                            DatabaseHandler.updateTaskTitle(selected.getId(), trimmed);
                            listView.refresh();
                        }
                    });
                }
            }
        });

        // Add Task Action (Button + Enter key)
        Runnable handleAddTask = () -> {
            String title = taskInput.getText().trim();
            if (!title.isEmpty()) {
                Task newTask = new Task(
                        title,
                        categoryBox.getValue(),
                        priorityBox.getValue(),
                        dueDatePicker.getValue()
                );
                DatabaseHandler.addTask(newTask);
                tasks.add(0, newTask);
                taskInput.clear();
                dueDatePicker.setValue(null);
                updateMetrics();
            }
        };

        addButton.setOnAction(e -> handleAddTask.run());
        taskInput.setOnAction(e -> handleAddTask.run());

        // 4. Footer: Progress Bar + Clear Completed
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        progressLabel = new Label("Completion: 0%");
        progressLabel.setMinWidth(145);

        Button clearCompletedBtn = new Button("Clear Completed");
        clearCompletedBtn.getStyleClass().add("secondary-btn");
        clearCompletedBtn.setOnAction(e -> {
            DatabaseHandler.deleteCompletedTasks();
            tasks.removeIf(Task::isCompleted);
            updateMetrics();
        });

        HBox footerRow = new HBox(12, progressBar, progressLabel, clearCompletedBtn);
        footerRow.setAlignment(Pos.CENTER_LEFT);

        VBox layout = new VBox(12, inputRow, filterRow, listView, footerRow);
        layout.setPadding(new Insets(16, 20, 20, 20));
        return layout;
    }

    // ==================== TAB 2: POMODORO FOCUS TIMER ====================
    private VBox createPomodoroView() {
        Label header = new Label("Deep Work Pomodoro Timer");
        header.getStyleClass().add("section-header");

        Label modeLabel = new Label("FOCUS SESSION (25 MIN)");
        modeLabel.getStyleClass().addAll("badge", "priority-high");

        ComboBox<Task> taskSelector = new ComboBox<>(tasks);
        taskSelector.setPromptText("Select a task to focus on (optional)...");
        taskSelector.setPrefWidth(340);
        taskSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(Task t) {
                return t == null ? "" : t.getTitle() + " [" + t.getCategory() + "]";
            }

            @Override
            public Task fromString(String s) {
                return null;
            }
        });

        Label timerDisplay = new Label(formatSeconds(remainingSeconds));
        timerDisplay.getStyleClass().add("timer-display");

        Button startPauseBtn = new Button("Start");
        startPauseBtn.getStyleClass().add("primary-btn");
        startPauseBtn.setPrefWidth(110);

        Button resetBtn = new Button("Reset");
        resetBtn.getStyleClass().add("secondary-btn");

        Button switchModeBtn = new Button("Switch to 5m Break");
        switchModeBtn.getStyleClass().add("secondary-btn");

        HBox controls = new HBox(12, startPauseBtn, resetBtn, switchModeBtn);
        controls.setAlignment(Pos.CENTER);

        Label statusNote = new Label("Complete a 25-minute session to automatically log focus minutes to Analytics.");
        statusNote.getStyleClass().add("muted-text");

        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (remainingSeconds > 0) {
                remainingSeconds--;
                timerDisplay.setText(formatSeconds(remainingSeconds));
            } else {
                timerTimeline.stop();
                startPauseBtn.setText("Start");

                if (isFocusMode) {
                    Task activeTask = taskSelector.getValue();
                    String focusedTitle = activeTask != null ? activeTask.getTitle() : "General Focus";
                    DatabaseHandler.logFocusSession(focusedTitle, 25);
                    updateMetrics();
                    statusNote.setText("Logged 25 focus minutes for: " + focusedTitle + "!");
                } else {
                    statusNote.setText("Break finished! Ready for another focus block.");
                }

                // Auto-flip mode
                isFocusMode = !isFocusMode;
                remainingSeconds = isFocusMode ? FOCUS_SECONDS : BREAK_SECONDS;
                modeLabel.setText(isFocusMode ? "FOCUS SESSION (25 MIN)" : "SHORT BREAK (5 MIN)");
                switchModeBtn.setText(isFocusMode ? "Switch to 5m Break" : "Switch to 25m Focus");
                timerDisplay.setText(formatSeconds(remainingSeconds));
            }
        }));
        timerTimeline.setCycleCount(Timeline.INDEFINITE);

        startPauseBtn.setOnAction(e -> {
            if (timerTimeline.getStatus() == Timeline.Status.RUNNING) {
                timerTimeline.pause();
                startPauseBtn.setText("Resume");
            } else {
                timerTimeline.play();
                startPauseBtn.setText("Pause");
            }
        });

        resetBtn.setOnAction(e -> {
            timerTimeline.stop();
            startPauseBtn.setText("Start");
            remainingSeconds = isFocusMode ? FOCUS_SECONDS : BREAK_SECONDS;
            timerDisplay.setText(formatSeconds(remainingSeconds));
        });

        switchModeBtn.setOnAction(e -> {
            timerTimeline.stop();
            startPauseBtn.setText("Start");
            isFocusMode = !isFocusMode;
            remainingSeconds = isFocusMode ? FOCUS_SECONDS : BREAK_SECONDS;
            modeLabel.setText(isFocusMode ? "FOCUS SESSION (25 MIN)" : "SHORT BREAK (5 MIN)");
            switchModeBtn.setText(isFocusMode ? "Switch to 5m Break" : "Switch to 25m Focus");
            timerDisplay.setText(formatSeconds(remainingSeconds));
        });

        VBox card = new VBox(18, header, modeLabel, taskSelector, timerDisplay, controls, statusNote);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(30));
        card.getStyleClass().add("card");

        VBox wrapper = new VBox(card);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(24));
        return wrapper;
    }

    private String formatSeconds(int totalSeconds) {
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    // ==================== TAB 3: ANALYTICS DASHBOARD ====================
    private VBox createAnalyticsView() {
        totalKpiLabel = new Label("0");
        rateKpiLabel = new Label("0%");
        overdueKpiLabel = new Label("0");
        focusKpiLabel = new Label("0m");

        HBox kpiRow = new HBox(14,
                createKpiCard("Total Tasks", totalKpiLabel),
                createKpiCard("Completion Rate", rateKpiLabel),
                createKpiCard("Overdue Tasks", overdueKpiLabel),
                createKpiCard("Focus Logged", focusKpiLabel)
        );
        kpiRow.setAlignment(Pos.CENTER);

        // Pie Chart: Completed vs Pending
        PieChart pieChart = new PieChart(pieData);
        pieChart.setTitle("Overall Completion");
        pieChart.setLabelsVisible(true);
        pieChart.setLegendVisible(true);
        HBox.setHgrow(pieChart, Priority.ALWAYS);

        // Bar Chart: Tasks by Category
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Category");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Tasks");
        yAxis.setTickUnit(1);
        yAxis.setMinorTickVisible(false);

        categoryBarChart = new BarChart<>(xAxis, yAxis);
        categoryBarChart.setTitle("Tasks by Category");
        categoryBarChart.setAnimated(false);
        HBox.setHgrow(categoryBarChart, Priority.ALWAYS);

        HBox chartsRow = new HBox(16, pieChart, categoryBarChart);
        VBox.setVgrow(chartsRow, Priority.ALWAYS);

        VBox layout = new VBox(18, kpiRow, chartsRow);
        layout.setPadding(new Insets(18, 20, 20, 20));
        return layout;
    }

    private VBox createKpiCard(String title, Label valueLabel) {
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("kpi-title");
        valueLabel.getStyleClass().add("kpi-value");

        VBox card = new VBox(4, titleLbl, valueLabel);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    // ==================== METRICS & CHARTS SYNC ====================
    private void updateMetrics() {
        int total = tasks.size();
        long completedCount = tasks.stream().filter(Task::isCompleted).count();
        long pendingCount = total - completedCount;
        long overdueCount = tasks.stream().filter(Task::isOverdue).count();
        int focusMinutes = DatabaseHandler.getTotalFocusMinutes();

        double ratio = total == 0 ? 0.0 : (double) completedCount / total;
        progressBar.setProgress(ratio);
        progressLabel.setText(String.format("Completion: %.0f%% (%d/%d)", ratio * 100, completedCount, total));

        // Update KPI Cards
        totalKpiLabel.setText(String.valueOf(total));
        rateKpiLabel.setText(String.format("%.0f%%", ratio * 100));
        overdueKpiLabel.setText(String.valueOf(overdueCount));
        focusKpiLabel.setText(focusMinutes + " min");

        // Update PieChart
        if (total == 0) {
            pieData.clear();
        } else {
            pieData.setAll(
                    new PieChart.Data("Completed (" + completedCount + ")", completedCount),
                    new PieChart.Data("Pending (" + pendingCount + ")", pendingCount)
            );
        }

        // Update Category BarChart
        XYChart.Series<String, Number> completedSeries = new XYChart.Series<>();
        completedSeries.setName("Completed");

        XYChart.Series<String, Number> pendingSeries = new XYChart.Series<>();
        pendingSeries.setName("Pending");

        for (String cat : CATEGORIES) {
            long catDone = tasks.stream().filter(t -> t.getCategory().equals(cat) && t.isCompleted()).count();
            long catOpen = tasks.stream().filter(t -> t.getCategory().equals(cat) && !t.isCompleted()).count();
            completedSeries.getData().add(new XYChart.Data<>(cat, catDone));
            pendingSeries.getData().add(new XYChart.Data<>(cat, catOpen));
        }

        categoryBarChart.getData().setAll(completedSeries, pendingSeries);
    }

    // ==================== LIGHT / DARK CSS THEME ====================
    private void applyTheme(Scene scene) {
        String bg = darkMode ? "#18181b" : "#f4f6f9";
        String surface = darkMode ? "#27272a" : "#ffffff";
        String border = darkMode ? "#3f3f46" : "#dce1e8";
        String text = darkMode ? "#f4f4f5" : "#1e293b";
        String muted = darkMode ? "#a1a1aa" : "#64748b";
        String accent = "#4f46e5";

        String css = """
            .root {
                -fx-background-color: %s;
                -fx-font-family: "Segoe UI", "Inter", sans-serif;
                -fx-font-size: 13px;
            }
            .label { -fx-text-fill: %s; }
            .app-title { -fx-font-size: 18px; -fx-font-weight: bold; }
            .section-header { -fx-font-size: 18px; -fx-font-weight: bold; }
            .muted-text { -fx-text-fill: %s; -fx-font-size: 12px; }
            .card {
                -fx-background-color: %s;
                -fx-background-radius: 10px;
                -fx-border-color: %s;
                -fx-border-radius: 10px;
            }
            .kpi-title { -fx-text-fill: %s; -fx-font-size: 12px; }
            .kpi-value { -fx-font-size: 22px; -fx-font-weight: bold; }
            .timer-display { -fx-font-size: 54px; -fx-font-weight: bold; -fx-font-family: "Consolas", monospace; }
            .text-field, .combo-box, .date-picker .text-field {
                -fx-background-color: %s;
                -fx-text-fill: %s;
                -fx-border-color: %s;
                -fx-border-radius: 6px;
                -fx-background-radius: 6px;
                -fx-padding: 6 10 6 10;
            }
            .list-view {
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-radius: 8px;
                -fx-background-radius: 8px;
            }
            .list-cell {
                -fx-background-color: transparent;
                -fx-text-fill: %s;
                -fx-border-color: transparent transparent %s transparent;
            }
            .list-cell:selected {
                -fx-background-color: rgba(79, 70, 229, 0.14);
            }
            .task-title { -fx-font-size: 14px; }
            .task-completed { -fx-strikethrough: true; -fx-opacity: 0.55; }
            .badge {
                -fx-padding: 2 8 2 8;
                -fx-background-radius: 999px;
                -fx-font-size: 10px;
                -fx-font-weight: bold;
            }
            .priority-high { -fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; }
            .priority-medium { -fx-background-color: #fef3c7; -fx-text-fill: #b45309; }
            .priority-low { -fx-background-color: #dcfce7; -fx-text-fill: #15803d; }
            .category-badge { -fx-background-color: #e0e7ff; -fx-text-fill: #3730a3; }
            .date-badge {
                -fx-padding: 2 7 2 7;
                -fx-background-radius: 6px;
                -fx-background-color: %s;
                -fx-font-size: 11px;
            }
            .date-overdue { -fx-background-color: #fecaca; -fx-text-fill: #991b1b; -fx-font-weight: bold; }
            .primary-btn {
                -fx-background-color: %s;
                -fx-text-fill: white;
                -fx-font-weight: bold;
                -fx-background-radius: 6px;
                -fx-padding: 7 14 7 14;
                -fx-cursor: hand;
            }
            .secondary-btn, .theme-toggle {
                -fx-background-color: %s;
                -fx-text-fill: %s;
                -fx-border-color: %s;
                -fx-border-radius: 6px;
                -fx-background-radius: 6px;
                -fx-padding: 6 12 6 12;
                -fx-cursor: hand;
            }
            .row-delete-btn {
                -fx-background-color: transparent;
                -fx-text-fill: %s;
                -fx-font-weight: bold;
                -fx-cursor: hand;
            }
            .row-delete-btn:hover { -fx-text-fill: #ef4444; }
            .tab-pane .tab-header-area .tab-header-background { -fx-background-color: %s; }
            .tab-pane .tab { -fx-background-color: transparent; -fx-padding: 8 16 8 16; }
            .tab-pane .tab:selected { -fx-border-color: transparent transparent %s transparent; -fx-border-width: 2px; }
            .chart-title, .axis-label, .axis { -fx-text-fill: %s; -fx-tick-label-fill: %s; }
            .chart-plot-background { -fx-background-color: %s; }
        """.formatted(
                bg, text, muted,
                surface, border,
                muted,
                surface, text, border,
                surface, border,
                text, border,
                bg,
                accent,
                surface, text, border,
                muted,
                bg, accent,
                text, muted, surface
        );

        String encoded = URLEncoder.encode(css, StandardCharsets.UTF_8).replace("+", "%20");
        scene.getStylesheets().setAll("data:text/css," + encoded);
    }

    public static void main(String[] args) {
        launch(args);
    }
}