import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHandler {
    private static final String DB_URL = "jdbc:sqlite:productivity_tasks.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    // Creates tasks table upon application launch
    public static void initializeDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                category TEXT NOT NULL,
                completed INTEGER NOT NULL DEFAULT 0
            );
        """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Loads stored tasks on app startup
    public static List<Main.Task> loadTasks() {
        List<Main.Task> list = new ArrayList<>();
        String sql = "SELECT * FROM tasks";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String title = rs.getString("title");
                String category = rs.getString("category");
                boolean completed = rs.getInt("completed") == 1;

                list.add(new Main.Task(id, title, category, completed));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // Inserts new task and populates generated ID
    public static void addTask(Main.Task task) {
        String sql = "INSERT INTO tasks(title, category, completed) VALUES(?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, task.getTitle());
            pstmt.setString(2, task.getCategory());
            pstmt.setInt(3, task.isCompleted() ? 1 : 0);

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

    // Removes task record from DB
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
}