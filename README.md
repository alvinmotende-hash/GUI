# JavaFX Productivity Dashboard

A desktop productivity application built with JavaFX and SQLite. It combines a full-featured task manager, a Pomodoro focus timer, and a real-time analytics dashboard with Light and Dark mode support.

## How It Works

1. **Manage Tasks:** Add tasks with a category (*Work*, *Personal*, *Study*, *Health*), priority level (*High*, *Medium*, *Low*), and due date. Search or filter tasks in real time, check them off as you finish, double-click to edit titles, or clear completed items.
2. **Run Focus Sessions:** Switch to the **Focus Timer** tab, optionally select a task, and start a 25-minute Pomodoro session (followed by a 5-minute break). Completed focus sessions are automatically logged to the database.
3. **Track Analytics:** Open the **Analytics** tab to monitor live KPIs (**Total Tasks**, **Completion Rate**, **Overdue Tasks**, and **Focus Logged**) alongside a completion pie chart and category bar chart.
4. **Toggle Themes:** Click the top-right toggle button at any time to switch between **Light Mode** and **Dark Mode**.

## Code Structure

- **`Main.java`**: Entry point that builds the three-tab JavaFX interface (`TabPane`, `ListView`, `Timeline`, `PieChart`, `BarChart`), manages reactive lists (`ObservableList`, `FilteredList`), and recalculates live metrics.
- **`Main.Task`**: Data model storing a task's ID, title, category, priority, due date, completion status, and overdue check.
- **`DatabaseHandler.java`**: Handles SQLite database initialization and CRUD persistence for tasks and logged focus sessions.
- **`style.css`**: Defines custom styling for cards, priority/date badges, and Light/Dark theme switching.

## Running the Code

1. Make sure you have **Java JDK (17+)**, the **JavaFX SDK**, and the **SQLite JDBC driver** (`sqlite-jdbc`) configured in your project.
2. Place `style.css` in your resources root folder (`/style.css`).
3. Run `Main.java` inside your IDE (e.g., IntelliJ IDEA, Eclipse, or VS Code). A local `productivity.db` file will be generated automatically on first launch.