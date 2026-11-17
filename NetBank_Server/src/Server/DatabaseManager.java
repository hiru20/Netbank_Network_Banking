
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
 * Handles MySQL / SQLite database connections.
 *
 * Features:
 * - MySQL primary database
 * - Automatic SQLite fallback
 * - SQLite WAL mode and performance settings
 * - ThreadLocal JDBC connection reuse
 * - Database table and index initialization
 * - Default administrator account creation
 * - SHA-256 password hashing
 */
public class DatabaseManager {

    // --------------------------------------------------
    // MYSQL CONFIGURATION
    // --------------------------------------------------

    private static final String MYSQL_URL =
            "jdbc:mysql://localhost:3306/netbank"
            + "?useSSL=false"
            + "&allowPublicKeyRetrieval=true"
            + "&serverTimezone=UTC"
            + "&cachePrepStmts=true"
            + "&prepStmtCacheSize=250";

    private static final String MYSQL_USER = "netbank_app";

    private static final String MYSQL_PASSWORD =
            System.getenv("NETBANK_DB_PASSWORD");


    // --------------------------------------------------
    // SQLITE CONFIGURATION
    // --------------------------------------------------

    private static final String SQLITE_URL =
            "jdbc:sqlite:data/netbank.sqlite";

    private static volatile boolean useSqliteFallback = false;

    private static volatile boolean databaseInitialized = false;


    // --------------------------------------------------
    // THREAD-LOCAL CONNECTION MANAGEMENT
    // --------------------------------------------------

    private static final ThreadLocal<Connection> THREAD_CONNECTION =
            new ThreadLocal<Connection>();


    /**
     * Returns the database connection associated with
     * the current worker thread.
     */
    public static Connection getConnection() throws SQLException {

        Connection existing = THREAD_CONNECTION.get();

        if (existing != null) {

            if (!existing.isClosed()) {
                return existing;
            }

            THREAD_CONNECTION.remove();
        }

        Connection conn = openNewConnection();

        THREAD_CONNECTION.set(conn);

        return conn;
    }


    /**
     * Opens a new MySQL or SQLite connection.
     *
     * MySQL is attempted first.
     * SQLite is used automatically if MySQL is unavailable.
     */
    private static synchronized Connection openNewConnection()
            throws SQLException {

        // Try MySQL if SQLite fallback has not been activated.
        if (!useSqliteFallback) {

            if (MYSQL_PASSWORD == null || MYSQL_PASSWORD.isBlank()) {

                System.out.println(
                        "[INFO] NETBANK_DB_PASSWORD is not configured."
                );

                System.out.println(
                        "[INFO] Using SQLite database instead."
                );

                useSqliteFallback = true;

            } else {

                try {

                    Connection conn = DriverManager.getConnection(
                            MYSQL_URL,
                            MYSQL_USER,
                            MYSQL_PASSWORD
                    );

                    System.out.println(
                            "[SUCCESS] Connected to MySQL database."
                    );

                    return conn;

                } catch (SQLException e) {

                    useSqliteFallback = true;

                    System.out.println(
                            "[INFO] MySQL is unavailable. "
                            + "Switching to SQLite database."
                    );

                    System.out.println(
                            "[INFO] Reason: " + e.getMessage()
                    );
                }
            }
        }

        // --------------------------------------------------
        // SQLITE FALLBACK
        // --------------------------------------------------

        File dataDir = new File("data");

        if (!dataDir.exists() && !dataDir.mkdirs()) {

            throw new SQLException(
                    "Unable to create SQLite data directory."
            );
        }

        Connection conn = DriverManager.getConnection(SQLITE_URL);

        try (Statement st = conn.createStatement()) {

            st.execute("PRAGMA foreign_keys = ON");

            st.execute("PRAGMA journal_mode = WAL");

            st.execute("PRAGMA synchronous = NORMAL");

            st.execute("PRAGMA busy_timeout = 5000");
        }

        return conn;
    }


    // --------------------------------------------------
    // CLOSE THREAD CONNECTION
    // --------------------------------------------------

    /**
     * Closes the current worker thread's database connection.
     *
     * Call this when a client disconnects.
     */
    public static void closeThreadConnection() {

        Connection conn = THREAD_CONNECTION.get();

        THREAD_CONNECTION.remove();

        if (conn != null) {

            try {

                conn.close();

            } catch (SQLException e) {

                System.err.println(
                        "[WARNING] Unable to close database connection: "
                        + e.getMessage()
                );
            }
        }
    }


    // --------------------------------------------------
    // DATABASE INITIALIZATION
    // --------------------------------------------------

    /**
     * Initializes database tables, indexes and admin account.
     *
     * The server should not continue starting if this fails.
     */
    public static synchronized void initializeDatabase() {

        if (databaseInitialized) {
            return;
        }

        try {

            Connection conn = getConnection();

            try (Statement stmt = conn.createStatement()) {

                if (useSqliteFallback) {

                    createSqliteTables(stmt);

                } else {

                    createMysqlTables(stmt);
                }
            }

            // Create default admin account if necessary.
            ensureAdminAccount(conn);

            databaseInitialized = true;

            System.out.println();

            System.out.println(
                    "[SUCCESS] Database initialized successfully."
            );

            System.out.println(
                    "[SUCCESS] All database tables are ready."
            );

            System.out.println(
                    "[SUCCESS] Admin account initialization completed."
            );

            System.out.println(
                    "[INFO] Database mode: "
                    + (useSqliteFallback ? "SQLite" : "MySQL")
            );

        } catch (SQLException e) {

            System.err.println();

            System.err.println(
                    "[FATAL] Database initialization failed."
            );

            throw new IllegalStateException(
                    "NetBank cannot start without an initialized database.",
                    e
            );

        } finally {

            // The main thread does not need to retain this connection.
            closeThreadConnection();
        }
    }


    // --------------------------------------------------
    // SQLITE TABLE CREATION
    // --------------------------------------------------

    private static void createSqliteTables(Statement stmt)
            throws SQLException {

        // USERS TABLE
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS users ("
                + "user_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT NOT NULL UNIQUE, "
                + "password_hash TEXT NOT NULL, "
                + "role TEXT NOT NULL, "
                + "status TEXT NOT NULL)"
        );


        // ACCOUNTS TABLE
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS accounts ("
                + "account_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user_id INTEGER NOT NULL, "
                + "account_number TEXT NOT NULL UNIQUE, "
                + "balance DOUBLE NOT NULL DEFAULT 0.0, "
                + "account_type TEXT NOT NULL, "
                + "status TEXT NOT NULL, "
                + "FOREIGN KEY (user_id) REFERENCES users(user_id))"
        );


        // TRANSACTIONS TABLE
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS transactions ("
                + "transaction_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "from_account TEXT, "
                + "to_account TEXT, "
                + "transaction_type TEXT NOT NULL, "
                + "amount DOUBLE NOT NULL, "
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "status TEXT NOT NULL)"
        );


        // LOGIN LOGS TABLE
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS login_logs ("
                + "log_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user_id INTEGER NOT NULL, "
                + "login_time DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "logout_time DATETIME, "
                + "ip_address TEXT NOT NULL, "
                + "FOREIGN KEY (user_id) REFERENCES users(user_id))"
        );


        // SQLITE PERFORMANCE INDEXES

        stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_acc_num "
                + "ON accounts(account_number)"
        );

        stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_acc_user "
                + "ON accounts(user_id)"
        );

        stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_tx_from "
                + "ON transactions(from_account)"
        );

        stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_tx_to "
                + "ON transactions(to_account)"
        );
    }


    // --------------------------------------------------
    // MYSQL TABLE CREATION
    // --------------------------------------------------

    private static void createMysqlTables(Statement stmt)
            throws SQLException {

        // USERS TABLE
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS users ("
                + "user_id INT AUTO_INCREMENT PRIMARY KEY, "
                + "username VARCHAR(50) NOT NULL UNIQUE, "
                + "password_hash VARCHAR(255) NOT NULL, "
                + "role VARCHAR(20) NOT NULL, "
                + "status VARCHAR(20) NOT NULL)"
        );


        // ACCOUNTS TABLE
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS accounts ("
                + "account_id INT AUTO_INCREMENT PRIMARY KEY, "
                + "user_id INT NOT NULL, "
                + "account_number VARCHAR(20) NOT NULL UNIQUE, "
                + "balance DECIMAL(15,2) NOT NULL DEFAULT 0.00, "
                + "account_type VARCHAR(20) NOT NULL, "
                + "status VARCHAR(20) NOT NULL, "
                + "FOREIGN KEY (user_id) REFERENCES users(user_id))"
        );


        // TRANSACTIONS TABLE
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS transactions ("
                + "transaction_id INT AUTO_INCREMENT PRIMARY KEY, "
                + "from_account VARCHAR(20), "
                + "to_account VARCHAR(20), "
                + "transaction_type VARCHAR(20) NOT NULL, "
                + "amount DECIMAL(15,2) NOT NULL, "
                + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "status VARCHAR(20) NOT NULL)"
        );


        // LOGIN LOGS TABLE
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS login_logs ("
                + "log_id INT AUTO_INCREMENT PRIMARY KEY, "
                + "user_id INT NOT NULL, "
                + "login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "logout_time TIMESTAMP NULL, "
                + "ip_address VARCHAR(64) NOT NULL, "
                + "FOREIGN KEY (user_id) REFERENCES users(user_id))"
        );
    }


    // --------------------------------------------------
    // DEFAULT ADMIN ACCOUNT
    // --------------------------------------------------

    /**
     * Creates the default administrator account if it does not exist.
     */
    private static void ensureAdminAccount(Connection conn)
            throws SQLException {

        String checkSql =
                "SELECT user_id FROM users WHERE username = ?";

        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {

            ps.setString(1, "admin");

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return;
                }
            }
        }


        String insertAdmin =
                "INSERT INTO users "
                + "(username, password_hash, role, status) "
                + "VALUES (?, ?, 'ADMIN', 'ACTIVE')";

        try (PreparedStatement ps = conn.prepareStatement(insertAdmin)) {

            ps.setString(1, "admin");

            ps.setString(2, hashPassword("admin123"));

            ps.executeUpdate();
        }
    }


    // --------------------------------------------------
    // PASSWORD HASHING
    // --------------------------------------------------

    /**
     * Generates a SHA-256 password hash.
     */
    public static String hashPassword(String password) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashBytes =
                    digest.digest(
                            password.getBytes(StandardCharsets.UTF_8)
                    );

            StringBuilder hexString =
                    new StringBuilder(hashBytes.length * 2);

            for (byte b : hashBytes) {

                String hex =
                        Integer.toHexString(0xff & b);

                if (hex.length() == 1) {

                    hexString.append('0');
                }

                hexString.append(hex);
            }

            return hexString.toString();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error hashing password",
                    e
            );
        }
    }
}
