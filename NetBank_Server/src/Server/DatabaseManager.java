package Server;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Member 3 - Database & Security
 * Handles MySQL / SQLite JDBC connection reuse (per-thread persistent connection),
 * SQLite WAL mode & indexing for high performance, and SHA-256 password hashing.
 */
public class DatabaseManager {

	// MySQL Configuration (XAMPP / MySQL Server)
	private static final String MYSQL_URL = "jdbc:mysql://localhost:3306/netbank?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&cachePrepStmts=true&prepStmtCacheSize=250";
	private static final String MYSQL_USER = "root";
	private static final String MYSQL_PASSWORD = "";

	// Fallback SQLite Database File (used automatically if MySQL Server is not running)
	private static final String SQLITE_URL = "jdbc:sqlite:data/netbank.sqlite";

	private static volatile boolean useSqliteFallback = false;

	// Reuses one persistent JDBC Connection per client worker thread to eliminate reconnect overhead
	private static final ThreadLocal<Connection> THREAD_CONNECTION = new ThreadLocal<Connection>();

	public static Connection getConnection() throws SQLException {
		Connection existing = THREAD_CONNECTION.get();
		if (existing != null && !existing.isClosed()) {
			return existing;
		}

		Connection conn = openNewConnection();
		THREAD_CONNECTION.set(conn);
		return conn;
	}

	private static synchronized Connection openNewConnection() throws SQLException {
		if (!useSqliteFallback) {
			try {
				return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
			} catch (SQLException e) {
				useSqliteFallback = true;
				System.out.println("MySQL Server not detected on port 3306. Using high-speed SQLite WAL Database (data/netbank.sqlite).\n");
			}
		}

		File dataDir = new File("data");
		if (!dataDir.exists()) {
			dataDir.mkdirs();
		}
		Connection conn = DriverManager.getConnection(SQLITE_URL);
		try (Statement st = conn.createStatement()) {
			st.execute("PRAGMA foreign_keys = ON;");
			st.execute("PRAGMA journal_mode = WAL;");
			st.execute("PRAGMA synchronous = NORMAL;");
			st.execute("PRAGMA busy_timeout = 5000;");
		}
		return conn;
	}

	/**
	 * Closes the current worker thread's persistent database connection when a client disconnects.
	 */
	public static void closeThreadConnection() {
		Connection conn = THREAD_CONNECTION.get();
		THREAD_CONNECTION.remove();
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException ignored) {
			}
		}
	}

	/**
	 * Creates the 4 relational tables and performance indexes.
	 */
	public static void initializeDatabase() {
		try {
			Connection conn = getConnection();
			try (Statement stmt = conn.createStatement()) {
				if (useSqliteFallback) {
					stmt.execute("CREATE TABLE IF NOT EXISTS users ("
							+ "user_id INTEGER PRIMARY KEY AUTOINCREMENT, "
							+ "username TEXT NOT NULL UNIQUE, "
							+ "password_hash TEXT NOT NULL, "
							+ "role TEXT NOT NULL, "
							+ "status TEXT NOT NULL)");

					stmt.execute("CREATE TABLE IF NOT EXISTS accounts ("
							+ "account_id INTEGER PRIMARY KEY AUTOINCREMENT, "
							+ "user_id INTEGER NOT NULL, "
							+ "account_number TEXT NOT NULL UNIQUE, "
							+ "balance DOUBLE NOT NULL DEFAULT 0.0, "
							+ "account_type TEXT NOT NULL, "
							+ "status TEXT NOT NULL, "
							+ "FOREIGN KEY (user_id) REFERENCES users(user_id))");

					stmt.execute("CREATE TABLE IF NOT EXISTS transactions ("
							+ "transaction_id INTEGER PRIMARY KEY AUTOINCREMENT, "
							+ "from_account TEXT, "
							+ "to_account TEXT, "
							+ "transaction_type TEXT NOT NULL, "
							+ "amount DOUBLE NOT NULL, "
							+ "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, "
							+ "status TEXT NOT NULL)");

					stmt.execute("CREATE TABLE IF NOT EXISTS login_logs ("
							+ "log_id INTEGER PRIMARY KEY AUTOINCREMENT, "
							+ "user_id INTEGER NOT NULL, "
							+ "login_time DATETIME DEFAULT CURRENT_TIMESTAMP, "
							+ "logout_time DATETIME, "
							+ "ip_address TEXT NOT NULL, "
							+ "FOREIGN KEY (user_id) REFERENCES users(user_id))");

					// Performance indexes for fast lookups
					stmt.execute("CREATE INDEX IF NOT EXISTS idx_acc_num ON accounts(account_number)");
					stmt.execute("CREATE INDEX IF NOT EXISTS idx_acc_user ON accounts(user_id)");
					stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_from ON transactions(from_account)");
					stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_to ON transactions(to_account)");
				} else {
					stmt.execute("CREATE TABLE IF NOT EXISTS users ("
							+ "user_id INT AUTO_INCREMENT PRIMARY KEY, "
							+ "username VARCHAR(50) NOT NULL UNIQUE, "
							+ "password_hash VARCHAR(255) NOT NULL, "
							+ "role VARCHAR(20) NOT NULL, "
							+ "status VARCHAR(20) NOT NULL)");

					stmt.execute("CREATE TABLE IF NOT EXISTS accounts ("
							+ "account_id INT AUTO_INCREMENT PRIMARY KEY, "
							+ "user_id INT NOT NULL, "
							+ "account_number VARCHAR(20) NOT NULL UNIQUE, "
							+ "balance DECIMAL(15,2) NOT NULL DEFAULT 0.00, "
							+ "account_type VARCHAR(20) NOT NULL, "
							+ "status VARCHAR(20) NOT NULL, "
							+ "FOREIGN KEY (user_id) REFERENCES users(user_id))");

					stmt.execute("CREATE TABLE IF NOT EXISTS transactions ("
							+ "transaction_id INT AUTO_INCREMENT PRIMARY KEY, "
							+ "from_account VARCHAR(20), "
							+ "to_account VARCHAR(20), "
							+ "transaction_type VARCHAR(20) NOT NULL, "
							+ "amount DECIMAL(15,2) NOT NULL, "
							+ "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
							+ "status VARCHAR(20) NOT NULL)");

					stmt.execute("CREATE TABLE IF NOT EXISTS login_logs ("
							+ "log_id INT AUTO_INCREMENT PRIMARY KEY, "
							+ "user_id INT NOT NULL, "
							+ "login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
							+ "logout_time TIMESTAMP NULL, "
							+ "ip_address VARCHAR(64) NOT NULL, "
							+ "FOREIGN KEY (user_id) REFERENCES users(user_id))");
				}

				ensureAdminAccount(conn);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private static void ensureAdminAccount(Connection conn) throws SQLException {
		String checkSql = "SELECT user_id FROM users WHERE username = 'admin'";
		try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(checkSql)) {
			if (rs.next()) {
				return;
			}
		}

		String insertAdmin = "INSERT INTO users (username, password_hash, role, status) VALUES (?, ?, 'ADMIN', 'ACTIVE')";
		try (PreparedStatement ps = conn.prepareStatement(insertAdmin)) {
			ps.setString(1, "admin");
			ps.setString(2, hashPassword("admin123"));
			ps.executeUpdate();
		}
	}

	public static String hashPassword(String password) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
			StringBuilder hexString = new StringBuilder(hashBytes.length * 2);
			for (byte b : hashBytes) {
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}
			return hexString.toString();
		} catch (Exception e) {
			throw new RuntimeException("Error hashing password", e);
		}
	}
}
