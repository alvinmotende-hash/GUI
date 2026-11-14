import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHandler {
    private static final String DB_URL = "jdbc:sqlite:productivity_tasks.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    // Creates tables and safely migrates existing productivity_tasks.db schema
    public static void initializeDatabase() {
        String createTasksSql = """
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                category TEXT NOT NULL,
                priority TEXT NOT NULL DEFAULT 'Medium',
                due_date TEXT,
                completed INTEGER NOT NULL DEFAULT 0
            );
        """;

        String createSessionsSql = """
            CREATE TABLE IF NOT EXISTS focus_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_title TEXT NOT NULL,
                minutes INTEGER NOT NULL,
                session_date TEXT NOT NULL
            );
        """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createTasksSql);
            stmt.execute(createSessionsSql);

            // Safe column migrations for existing databases
            try {
                stmt.execute("ALTER TABLE tasks ADD COLUMN priority TEXT NOT NULL DEFAULT 'Medium'");
            } catch (SQLException ignored) { /* Column already exists */ }

            try {
                stmt.execute("ALTER TABLE tasks ADD COLUMN due_date TEXT");
            } catch (SQLException ignored) { /* Column already exists */ }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Loads tasks sorted by status, priority, and due date
    public static List<Main.Task> loadTasks() {
        List<Main.Task> list = new ArrayList<>();
        String sql = """
            SELECT * FROM tasks
            ORDER BY completed ASC,
                     CASE priority
                         WHEN 'High' THEN 1
                         WHEN 'Medium' THEN 2
                         WHEN 'Low' THEN 3
                         ELSE 4
                     END ASC,
                     CASE WHEN due_date IS NULL OR due_date = '' THEN 1 ELSE 0 END ASC,
                     due_date ASC,
                     id DESC
        """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String title = rs.getString("title");
                String category = rs.getString("category");
                String priority = rs.getString("priority");
                if (priority == null || priority.isBlank()) priority = "Medium";

                String dueDateStr = rs.getString("due_date");
                LocalDate dueDate = (dueDateStr != null && !dueDateStr.isBlank())
                        ? LocalDate.parse(dueDateStr)
                        : null;

                boolean completed = rs.getInt("completed") == 1;

                list.add(new Main.Task(id, title, category, priority, dueDate, completed));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // Inserts new task and populates generated ID
    public static void addTask(Main.Task task) {
        String sql = "INSERT INTO tasks(title, category, priority, due_date, completed) VALUES(?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, task.getTitle());
            pstmt.setString(2, task.getCategory());
            pstmt.setString(3, task.getPriority());
            pstmt.setString(4, task.getDueDate() != null ? task.getDueDate().toString() : null);
            pstmt.setInt(5, task.isCompleted() ? 1 : 0);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    task.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Updates task completion state
    public static void updateTaskStatus(int id, boolean completed) {
        String sql = "UPDATE tasks SET completed = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, completed ? 1 : 0);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Updates task title (for double-click inline editing)
    public static void updateTaskTitle(int id, String newTitle) {
        String sql = "UPDATE tasks SET title = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newTitle);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Removes single task record from DB
    public static void deleteTask(int id) {
        String sql = "DELETE FROM tasks WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Bulk deletes all completed tasks
    public static void deleteCompletedTasks() {
        String sql = "DELETE FROM tasks WHERE completed = 1";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Logs a completed Pomodoro focus session
    public static void logFocusSession(String taskTitle, int minutes) {
        String sql = "INSERT INTO focus_sessions(task_title, minutes, session_date) VALUES(?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, taskTitle);
            pstmt.setInt(2, minutes);
            pstmt.setString(3, LocalDate.now().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Returns total logged focus minutes across all sessions
    public static int getTotalFocusMinutes() {
        String sql = "SELECT COALESCE(SUM(minutes), 0) AS total FROM focus_sessions";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}