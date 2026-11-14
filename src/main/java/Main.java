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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public class Main extends Application {

    public static class Task {
        private int id;
        private String title;
        private final String category, priority;
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
        public boolean isOverdue() { return !completed && dueDate != null && dueDate.isBefore(LocalDate.now()); }
        @Override public String toString() { return title + " (" + category + ")"; }
    }

    private static final List<String> CATEGORIES = List.of("Work", "Personal", "Study", "Health");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd");
    private static final int FOCUS_SECONDS = 25 * 60, BREAK_SECONDS = 5 * 60;

    private final ObservableList<Task> tasks = FXCollections.observableArrayList();
    private final ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
    private FilteredList<Task> filteredTasks;
    private Runnable refreshFilter;

    private ProgressBar progressBar = new ProgressBar(0);
    private Label progressLabel = new Label("Completion: 0%");
    private Label totalKpi = new Label("0"), rateKpi = new Label("0%"), overdueKpi = new Label("0"), focusKpi = new Label("0m");
    private BarChart<String, Number> categoryBarChart;

    private int remainingSeconds = FOCUS_SECONDS;
    private boolean isFocusMode = true;
    private Timeline timerTimeline;

    @Override
    public void start(Stage stage) {
        DatabaseHandler.initializeDatabase();
        tasks.addAll(DatabaseHandler.loadTasks());
        filteredTasks = new FilteredList<>(tasks, t -> true);

        ToggleButton themeToggle = new ToggleButton("Dark Mode");
        themeToggle.getStyleClass().add("theme-toggle");

        HBox topBar = new HBox(10, label("Productivity Dashboard", "app-title"), hSpacer(), themeToggle);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 20, 8, 20));

        TabPane tabs = new TabPane(
                new Tab("Task Manager", createTasksView()),
                new Tab("Focus Timer", createPomodoroView()),
                new Tab("Analytics", createAnalyticsView())
        );
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox root = new VBox(topBar, tabs);
        Scene scene = new Scene(root, 820, 580);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());

        themeToggle.setOnAction(e -> {
            boolean dark = themeToggle.isSelected();
            themeToggle.setText(dark ? "Light Mode" : "Dark Mode");
            if (dark) root.getStyleClass().add("dark");
            else root.getStyleClass().remove("dark");
        });

        stage.setTitle("JavaFX Productivity Dashboard");
        stage.setScene(scene);
        stage.show();
        updateMetrics();
    }

    // ==================== TAB 1: TASK MANAGER ====================
    private VBox createTasksView() {
        TextField taskInput = new TextField();
        taskInput.setPromptText("What needs to get done?");
        HBox.setHgrow(taskInput, Priority.ALWAYS);

        ComboBox<String> categoryBox = combo(CATEGORIES, "Work");
        ComboBox<String> priorityBox = combo(List.of("High", "Medium", "Low"), "Medium");
        DatePicker dueDatePicker = new DatePicker();
        dueDatePicker.setPromptText("Due date");
        dueDatePicker.setPrefWidth(125);

        Button addButton = button("+ Add Task", "primary-btn", null);
        HBox inputRow = new HBox(8, taskInput, categoryBox, priorityBox, dueDatePicker, addButton);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        TextField searchField = new TextField();
        searchField.setPromptText("Search tasks...");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        ComboBox<String> statusFilter = combo(List.of("All", "Active", "Completed"), "All");
        ComboBox<String> catFilter = combo(CATEGORIES, "All Categories");
        catFilter.getItems().add(0, "All Categories");

        refreshFilter = () -> {
            String q = searchField.getText().trim().toLowerCase();
            String status = statusFilter.getValue(), cat = catFilter.getValue();
            filteredTasks.setPredicate(t ->
                    (q.isEmpty() || t.getTitle().toLowerCase().contains(q)) &&
                    ("All".equals(status) || ("Active".equals(status) != t.isCompleted())) &&
                    ("All Categories".equals(cat) || t.getCategory().equals(cat))
            );
        };
        searchField.textProperty().addListener((o, a, b) -> refreshFilter.run());
        statusFilter.valueProperty().addListener((o, a, b) -> refreshFilter.run());
        catFilter.valueProperty().addListener((o, a, b) -> refreshFilter.run());

        ListView<Task> listView = new ListView<>(filteredTasks);
        listView.setPlaceholder(new Label("No tasks match your filter. Add one above!"));
        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);
                if (empty || task == null) { setText(null); setGraphic(null); return; }

                CheckBox cb = new CheckBox();
                cb.setSelected(task.isCompleted());
                cb.setOnAction(e -> {
                    task.setCompleted(cb.isSelected());
                    DatabaseHandler.updateTaskStatus(task.getId(), task.isCompleted());
                    refreshFilter.run();
                    listView.refresh();
                    updateMetrics();
                });

                Label titleLbl = label(task.getTitle(), task.isCompleted() ? new String[]{"task-title", "task-completed"} : new String[]{"task-title"});
                Label prioBadge = label(task.getPriority().toUpperCase(), "badge", "priority-" + task.getPriority().toLowerCase());
                Label catBadge = label(task.getCategory(), "badge", "category-badge");

                HBox row = new HBox(10, cb, titleLbl, hSpacer(), prioBadge, catBadge);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(4, 6, 4, 6));

                if (task.getDueDate() != null) {
                    String txt = task.isOverdue() ? "OVERDUE (" + task.getDueDate().format(DATE_FMT) + ")" : task.getDueDate().format(DATE_FMT);
                    row.getChildren().add(label(txt, task.isOverdue() ? new String[]{"date-badge", "date-overdue"} : new String[]{"date-badge"}));
                }

                row.getChildren().add(button("✕", "row-delete-btn", () -> {
                    DatabaseHandler.deleteTask(task.getId());
                    tasks.remove(task);
                    updateMetrics();
                }));

                setText(null);
                setGraphic(row);
            }
        });

        listView.setOnMouseClicked(e -> {
            Task sel = listView.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && sel != null) {
                TextInputDialog dlg = new TextInputDialog(sel.getTitle());
                dlg.setTitle("Edit Task");
                dlg.setHeaderText("Update task title");
                dlg.setContentText("Title:");
                dlg.showAndWait().map(String::trim).filter(s -> !s.isEmpty()).ifPresent(t -> {
                    sel.setTitle(t);
                    DatabaseHandler.updateTaskTitle(sel.getId(), t);
                    listView.refresh();
                });
            }
        });

        Runnable addAction = () -> {
            String title = taskInput.getText().trim();
            if (!title.isEmpty()) {
                Task t = new Task(title, categoryBox.getValue(), priorityBox.getValue(), dueDatePicker.getValue());
                DatabaseHandler.addTask(t);
                tasks.add(0, t);
                taskInput.clear();
                dueDatePicker.setValue(null);
                updateMetrics();
            }
        };
        addButton.setOnAction(e -> addAction.run());
        taskInput.setOnAction(e -> addAction.run());

        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        progressLabel.setMinWidth(145);

        HBox footer = new HBox(12, progressBar, progressLabel, button("Clear Completed", "secondary-btn", () -> {
            DatabaseHandler.deleteCompletedTasks();
            tasks.removeIf(Task::isCompleted);
            updateMetrics();
        }));
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox layout = new VBox(12, inputRow, new HBox(8, searchField, statusFilter, catFilter), listView, footer);
        layout.setPadding(new Insets(16, 20, 20, 20));
        return layout;
    }

    // ==================== TAB 2: POMODORO FOCUS TIMER ====================
    private VBox createPomodoroView() {
        Label modeLbl = label("FOCUS SESSION (25 MIN)", "badge", "priority-high");
        Label timerDisplay = label(formatTime(remainingSeconds), "timer-display");
        Label statusNote = label("Complete a 25-minute session to automatically log focus minutes to Analytics.", "muted-text");

        ComboBox<Task> taskSelector = new ComboBox<>(tasks);
        taskSelector.setPromptText("Select a task to focus on (optional)...");
        taskSelector.setPrefWidth(340);
        taskSelector.setConverter(new StringConverter<>() {
            @Override public String toString(Task t) { return t == null ? "" : t.getTitle() + " [" + t.getCategory() + "]"; }
            @Override public Task fromString(String s) { return null; }
        });

        Button startBtn = button("Start", "primary-btn", null);
        startBtn.setPrefWidth(110);
        Button resetBtn = button("Reset", "secondary-btn", null);
        Button switchBtn = button("Switch to 5m Break", "secondary-btn", null);

        Runnable syncTimerUI = () -> {
            remainingSeconds = isFocusMode ? FOCUS_SECONDS : BREAK_SECONDS;
            modeLbl.setText(isFocusMode ? "FOCUS SESSION (25 MIN)" : "SHORT BREAK (5 MIN)");
            switchBtn.setText(isFocusMode ? "Switch to 5m Break" : "Switch to 25m Focus");
            timerDisplay.setText(formatTime(remainingSeconds));
            startBtn.setText("Start");
        };

        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (--remainingSeconds > 0) {
                timerDisplay.setText(formatTime(remainingSeconds));
            } else {
                timerTimeline.stop();
                if (isFocusMode) {
                    String title = taskSelector.getValue() != null ? taskSelector.getValue().getTitle() : "General Focus";
                    DatabaseHandler.logFocusSession(title, 25);
                    updateMetrics();
                    statusNote.setText("Logged 25 focus minutes for: " + title + "!");
                } else {
                    statusNote.setText("Break finished! Ready for another focus block.");
                }
                isFocusMode = !isFocusMode;
                syncTimerUI.run();
            }
        }));
        timerTimeline.setCycleCount(Timeline.INDEFINITE);

        startBtn.setOnAction(e -> {
            if (timerTimeline.getStatus() == Timeline.Status.RUNNING) { timerTimeline.pause(); startBtn.setText("Resume"); }
            else { timerTimeline.play(); startBtn.setText("Pause"); }
        });
        resetBtn.setOnAction(e -> { timerTimeline.stop(); syncTimerUI.run(); });
        switchBtn.setOnAction(e -> { timerTimeline.stop(); isFocusMode = !isFocusMode; syncTimerUI.run(); });

        HBox controls = new HBox(12, startBtn, resetBtn, switchBtn);
        controls.setAlignment(Pos.CENTER);

        VBox card = new VBox(18, label("Deep Work Pomodoro Timer", "section-header"), modeLbl, taskSelector, timerDisplay, controls, statusNote);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(30));
        card.getStyleClass().add("card");

        VBox wrapper = new VBox(card);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(24));
        return wrapper;
    }

    // ==================== TAB 3: ANALYTICS DASHBOARD ====================
    private VBox createAnalyticsView() {
        HBox kpiRow = new HBox(14,
                kpiCard("Total Tasks", totalKpi),
                kpiCard("Completion Rate", rateKpi),
                kpiCard("Overdue Tasks", overdueKpi),
                kpiCard("Focus Logged", focusKpi)
        );

        PieChart pieChart = new PieChart(pieData);
        pieChart.setTitle("Overall Completion");
        HBox.setHgrow(pieChart, Priority.ALWAYS);

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

    // ==================== METRICS & HELPERS ====================
    private void updateMetrics() {
        int total = tasks.size();
        long done = tasks.stream().filter(Task::isCompleted).count();
        double ratio = total == 0 ? 0.0 : (double) done / total;

        progressBar.setProgress(ratio);
        progressLabel.setText(String.format("Completion: %.0f%% (%d/%d)", ratio * 100, done, total));
        totalKpi.setText(String.valueOf(total));
        rateKpi.setText(String.format("%.0f%%", ratio * 100));
        overdueKpi.setText(String.valueOf(tasks.stream().filter(Task::isOverdue).count()));
        focusKpi.setText(DatabaseHandler.getTotalFocusMinutes() + " min");

        if (total == 0) pieData.clear();
        else pieData.setAll(new PieChart.Data("Completed (" + done + ")", done), new PieChart.Data("Pending (" + (total - done) + ")", total - done));

        XYChart.Series<String, Number> doneSeries = new XYChart.Series<>(), openSeries = new XYChart.Series<>();
        doneSeries.setName("Completed");
        openSeries.setName("Pending");
        for (String cat : CATEGORIES) {
            doneSeries.getData().add(new XYChart.Data<>(cat, tasks.stream().filter(t -> t.getCategory().equals(cat) && t.isCompleted()).count()));
            openSeries.getData().add(new XYChart.Data<>(cat, tasks.stream().filter(t -> t.getCategory().equals(cat) && !t.isCompleted()).count()));
        }
        categoryBarChart.getData().setAll(doneSeries, openSeries);
    }

    private VBox kpiCard(String title, Label value) {
        value.getStyleClass().add("kpi-value");
        VBox card = new VBox(4, label(title, "kpi-title"), value);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Label label(String text, String... classes) {
        Label l = new Label(text);
        l.getStyleClass().addAll(classes);
        return l;
    }

    private Button button(String text, String styleClass, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add(styleClass);
        if (action != null) b.setOnAction(e -> action.run());
        return b;
    }

    private ComboBox<String> combo(List<String> items, String defaultVal) {
        ComboBox<String> cb = new ComboBox<>(FXCollections.observableArrayList(items));
        cb.setValue(defaultVal);
        return cb;
    }

    private Region hSpacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private String formatTime(int secs) {
        return String.format("%02d:%02d", secs / 60, secs % 60);
    }

    public static void main(String[] args) { launch(args); }
}