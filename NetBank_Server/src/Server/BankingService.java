package Server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Member 3 - Database / Security & Transaction Manager
 * Provides synchronized, thread-safe banking operations.
 */
public class BankingService {

	public static synchronized String registerCustomer(String username, String password, String accountType, double initialBalance) {
		if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
			return "ERROR|INVALID_FIELDS|Username and password are required";
		}
		if (Double.isNaN(initialBalance) || Double.isInfinite(initialBalance) || initialBalance < 0) {
			return "ERROR|INVALID_AMOUNT|Initial balance cannot be negative";
		}
		if (accountType == null || accountType.trim().isEmpty()) {
			return "ERROR|INVALID_FIELDS|Account type is required";
		}

		try (Connection conn = DatabaseManager.getConnection()) {
			conn.setAutoCommit(false);
			try {
				try (PreparedStatement checkPs = conn.prepareStatement("SELECT user_id FROM users WHERE username = ?")) {
					checkPs.setString(1, username.trim());
					try (ResultSet rs = checkPs.executeQuery()) {
						if (rs.next()) {
							conn.rollback();
							return "ERROR|USER_EXISTS|Username already exists";
						}
					}
				}

				int userId = 0;
				String insertUserSql = "INSERT INTO users (username, password_hash, role, status) VALUES (?, ?, 'CUSTOMER', 'ACTIVE')";
				try (PreparedStatement ps = conn.prepareStatement(insertUserSql, Statement.RETURN_GENERATED_KEYS)) {
					ps.setString(1, username.trim());
					ps.setString(2, DatabaseManager.hashPassword(password));
					ps.executeUpdate();
					try (ResultSet keys = ps.getGeneratedKeys()) {
						if (keys.next()) {
							userId = keys.getInt(1);
						}
					}
				}

				String accountNumber = generateNextAccountNumber(conn);

				String insertAccSql = "INSERT INTO accounts (user_id, account_number, balance, account_type, status) VALUES (?, ?, ?, ?, 'ACTIVE')";
				try (PreparedStatement ps = conn.prepareStatement(insertAccSql)) {
					ps.setInt(1, userId);
					ps.setString(2, accountNumber);
					ps.setDouble(3, initialBalance);
					ps.setString(4, accountType.toUpperCase());
					ps.executeUpdate();
				}

				if (initialBalance > 0) {
					recordTransaction(conn, null, accountNumber, "DEPOSIT", initialBalance, "SUCCESS");
				}

				conn.commit();
				return "REGISTER_SUCCESS|" + accountNumber + "|" + String.format("%.2f", initialBalance);
			} catch (SQLException e) {
				conn.rollback();
				return "ERROR|DB_ERROR|" + e.getMessage();
			} finally {
				conn.setAutoCommit(true);
			}
		} catch (SQLException e) {
			return "ERROR|DB_ERROR|" + e.getMessage();
		}
	}

	private static String generateNextAccountNumber(Connection conn) throws SQLException {
		int nextAcc = 100234;
		String sql = "SELECT COUNT(*) FROM accounts";
		try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
			if (rs.next()) {
				nextAcc = 100234 + rs.getInt(1);
			}
		}
		return String.valueOf(nextAcc);
	}

	public static synchronized String login(String username, String password, String ipAddress) {
		String sql = "SELECT user_id, password_hash, role, status FROM users WHERE username = ?";
		try (Connection conn = DatabaseManager.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {

			ps.setString(1, username.trim());
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return "ERROR|INVALID_CREDENTIALS|Invalid username or password";
				}

				int userId = rs.getInt("user_id");
				String storedHash = rs.getString("password_hash");
				String role = rs.getString("role");
				String status = rs.getString("status");

				if (!storedHash.equals(DatabaseManager.hashPassword(password))) {
					return "ERROR|INVALID_CREDENTIALS|Invalid username or password";
				}
				if (!"ACTIVE".equalsIgnoreCase(status)) {
					return "ERROR|ACCOUNT_BLOCKED|User account is " + status;
				}

				String accountNumber = "ADMIN";
				try (PreparedStatement accPs = conn.prepareStatement("SELECT account_number FROM accounts WHERE user_id = ?")) {
					accPs.setInt(1, userId);
					try (ResultSet accRs = accPs.executeQuery()) {
						if (accRs.next()) {
							accountNumber = accRs.getString("account_number");
						}
					}
				}

				int logId = 0;
				String logSql = "INSERT INTO login_logs (user_id, ip_address) VALUES (?, ?)";
				try (PreparedStatement logPs = conn.prepareStatement(logSql, Statement.RETURN_GENERATED_KEYS)) {
					logPs.setInt(1, userId);
					logPs.setString(2, ipAddress);
					logPs.executeUpdate();
					try (ResultSet keys = logPs.getGeneratedKeys()) {
						if (keys.next()) {
							logId = keys.getInt(1);
						}
					}
				}

				return "LOGIN_SUCCESS|" + accountNumber + "|" + role + "|" + userId + "|" + logId;
			}
		} catch (SQLException e) {
			return "ERROR|DB_ERROR|" + e.getMessage();
		}
	}

	public static synchronized void recordLogout(int logId) {
		if (logId <= 0) return;
		String sql = "UPDATE login_logs SET logout_time = CURRENT_TIMESTAMP WHERE log_id = ?";
		try (Connection conn = DatabaseManager.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, logId);
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static synchronized String getBalance(String accountNumber) {
		String sql = "SELECT balance, status FROM accounts WHERE account_number = ?";
		try (Connection conn = DatabaseManager.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, accountNumber.trim());
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return "ERROR|ACCOUNT_NOT_FOUND|Account " + accountNumber + " not found";
				}
				double balance = rs.getDouble("balance");
				return "BALANCE_RESPONSE|" + String.format("%.2f", balance);
			}
		} catch (SQLException e) {
			return "ERROR|DB_ERROR|" + e.getMessage();
		}
	}

	public static synchronized String deposit(String accountNumber, double amount) {
		if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
			return "ERROR|INVALID_AMOUNT|Deposit amount must be positive";
		}

		try (Connection conn = DatabaseManager.getConnection()) {
			conn.setAutoCommit(false);
			try {
				double currentBalance;
				String status;
				try (PreparedStatement ps = conn.prepareStatement("SELECT balance, status FROM accounts WHERE account_number = ?")) {
					ps.setString(1, accountNumber.trim());
					try (ResultSet rs = ps.executeQuery()) {
						if (!rs.next()) {
							conn.rollback();
							return "ERROR|ACCOUNT_NOT_FOUND|Account not found";
						}
						currentBalance = rs.getDouble("balance");
						status = rs.getString("status");
					}
				}

				if (!"ACTIVE".equalsIgnoreCase(status)) {
					conn.rollback();
					return "ERROR|ACCOUNT_INACTIVE|Account is " + status;
				}

				double newBalance = currentBalance + amount;
				try (PreparedStatement updatePs = conn.prepareStatement("UPDATE accounts SET balance = ? WHERE account_number = ?")) {
					updatePs.setDouble(1, newBalance);
					updatePs.setString(2, accountNumber.trim());
					updatePs.executeUpdate();
				}

				recordTransaction(conn, null, accountNumber.trim(), "DEPOSIT", amount, "SUCCESS");
				conn.commit();
				return "DEPOSIT_SUCCESS|" + String.format("%.2f", newBalance);
			} catch (SQLException e) {
				conn.rollback();
				return "ERROR|DB_ERROR|" + e.getMessage();
			} finally {
				conn.setAutoCommit(true);
			}
		} catch (SQLException e) {
			return "ERROR|DB_ERROR|" + e.getMessage();
		}
	}

	public static synchronized String withdraw(String accountNumber, double amount) {
		if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
			return "ERROR|INVALID_AMOUNT|Withdrawal amount must be positive";
		}

		try (Connection conn = DatabaseManager.getConnection()) {
			conn.setAutoCommit(false);
			try {
				double currentBalance;
				String status;
				try (PreparedStatement ps = conn.prepareStatement("SELECT balance, status FROM accounts WHERE account_number = ?")) {
					ps.setString(1, accountNumber.trim());
					try (ResultSet rs = ps.executeQuery()) {
						if (!rs.next()) {
							conn.rollback();
							return "ERROR|ACCOUNT_NOT_FOUND|Account not found";
						}
						currentBalance = rs.getDouble("balance");
						status = rs.getString("status");
					}
				}

				if (!"ACTIVE".equalsIgnoreCase(status)) {
					conn.rollback();
					return "ERROR|ACCOUNT_INACTIVE|Account is " + status;
				}

				if (currentBalance < amount) {
					conn.rollback();
					return "ERROR|INSUFFICIENT_FUNDS";
				}

				double newBalance = currentBalance - amount;
				try (PreparedStatement updatePs = conn.prepareStatement("UPDATE accounts SET balance = ? WHERE account_number = ?")) {
					updatePs.setDouble(1, newBalance);
					updatePs.setString(2, accountNumber.trim());
					updatePs.executeUpdate();
				}

				recordTransaction(conn, accountNumber.trim(), null, "WITHDRAW", amount, "SUCCESS");
				conn.commit();
				return "WITHDRAW_SUCCESS|" + String.format("%.2f", newBalance);
			} catch (SQLException e) {
				conn.rollback();
				return "ERROR|DB_ERROR|" + e.getMessage();
			} finally {
				conn.setAutoCommit(true);
			}
		} catch (SQLException e) {
			return "ERROR|DB_ERROR|" + e.getMessage();
		}
	}

	public static synchronized String transfer(String fromAccount, String toAccount, double amount) {
		if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
			return "ERROR|INVALID_AMOUNT|Transfer amount must be positive";
		}
		if (fromAccount.trim().equals(toAccount.trim())) {
			return "ERROR|SAME_ACCOUNT|Cannot transfer to the same account";
		}

		try (Connection conn = DatabaseManager.getConnection()) {
			conn.setAutoCommit(false);
			try {
				double senderBalance;
				String senderStatus;
				try (PreparedStatement ps = conn.prepareStatement("SELECT balance, status FROM accounts WHERE account_number = ?")) {
					ps.setString(1, fromAccount.trim());
					try (ResultSet rs = ps.executeQuery()) {
						if (!rs.next()) {
							conn.rollback();
							return "ERROR|ACCOUNT_NOT_FOUND|Sender account not found";
						}
						senderBalance = rs.getDouble("balance");
						senderStatus = rs.getString("status");
					}
				}

				double receiverBalance;
				String receiverStatus;
				try (PreparedStatement ps = conn.prepareStatement("SELECT balance, status FROM accounts WHERE account_number = ?")) {
					ps.setString(1, toAccount.trim());
					try (ResultSet rs = ps.executeQuery()) {
						if (!rs.next()) {
							conn.rollback();
							return "ERROR|ACCOUNT_NOT_FOUND|Receiver account not found";
						}
						receiverBalance = rs.getDouble("balance");
						receiverStatus = rs.getString("status");
					}
				}

				if (!"ACTIVE".equalsIgnoreCase(senderStatus) || !"ACTIVE".equalsIgnoreCase(receiverStatus)) {
					conn.rollback();
					return "ERROR|ACCOUNT_INACTIVE|Both accounts must be ACTIVE";
				}

				if (senderBalance < amount) {
					conn.rollback();
					return "ERROR|INSUFFICIENT_FUNDS";
				}

				double newSenderBalance = senderBalance - amount;
				double newReceiverBalance = receiverBalance + amount;

				try (PreparedStatement debitPs = conn.prepareStatement("UPDATE accounts SET balance = ? WHERE account_number = ?")) {
					debitPs.setDouble(1, newSenderBalance);
					debitPs.setString(2, fromAccount.trim());
					debitPs.executeUpdate();
				}

				try (PreparedStatement creditPs = conn.prepareStatement("UPDATE accounts SET balance = ? WHERE account_number = ?")) {
					creditPs.setDouble(1, newReceiverBalance);
					creditPs.setString(2, toAccount.trim());
					creditPs.executeUpdate();
				}

				recordTransaction(conn, fromAccount.trim(), toAccount.trim(), "TRANSFER", amount, "SUCCESS");
				conn.commit();

				return "TRANSFER_SUCCESS|" + String.format("%.2f", newSenderBalance);
			} catch (SQLException e) {
				conn.rollback();
				return "ERROR|DB_ERROR|" + e.getMessage();
			} finally {
				conn.setAutoCommit(true);
			}
		} catch (SQLException e) {
			return "ERROR|DB_ERROR|" + e.getMessage();
		}
	}

	public static synchronized String getTransactionHistory(String accountNumber) {
		String sql = "SELECT transaction_id, from_account, to_account, transaction_type, amount, timestamp, status "
				+ "FROM transactions WHERE from_account = ? OR to_account = ? ORDER BY transaction_id DESC LIMIT 20";
		StringBuilder sb = new StringBuilder("HISTORY_RESPONSE");
		try (Connection conn = DatabaseManager.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, accountNumber.trim());
			ps.setString(2, accountNumber.trim());
			try (ResultSet rs = ps.executeQuery()) {
				boolean found = false;
				while (rs.next()) {
					found = true;
					sb.append("|#").append(rs.getInt("transaction_id"))
							.append(" ").append(rs.getString("transaction_type"))
							.append(" [From:").append(rs.getString("from_account") != null ? rs.getString("from_account") : "-")
							.append(" To:").append(rs.getString("to_account") != null ? rs.getString("to_account") : "-")
							.append("] Amount:").append(String.format("%.2f", rs.getDouble("amount")))
							.append(" (").append(rs.getString("timestamp")).append(")");
				}
				if (!found) {
					sb.append("|No transactions found");
				}
			}
			return sb.toString();
		} catch (SQLException e) {
			return "ERROR|DB_ERROR|" + e.getMessage();
		}
	}

	public static synchronized String getAllAccountsForAdmin() {
		String sql = "SELECT a.account_number, u.username, a.account_type, a.balance, a.status "
				+ "FROM accounts a JOIN users u ON a.user_id = u.user_id ORDER BY a.account_id ASC";
		StringBuilder sb = new StringBuilder("ADMIN_ACCOUNTS_RESPONSE");
		try (Connection conn = DatabaseManager.getConnection();
		     Statement st = conn.createStatement();
		     ResultSet rs = st.executeQuery(sql)) {
			boolean found = false;
			while (rs.next()) {
				found = true;
				sb.append("|Acc:").append(rs.getString("account_number"))
						.append(" User:").append(rs.getString("username"))
						.append(" Type:").append(rs.getString("account_type"))
						.append(" Balance:").append(String.format("%.2f", rs.getDouble("balance")))
						.append(" Status:").append(rs.getString("status"));
			}
			if (!found) {
				sb.append("|No customer accounts registered yet");
			}
			return sb.toString();
		} catch (SQLException e) {
			return "ERROR|DB_ERROR|" + e.getMessage();
		}
	}

	public static synchronized String getAllTransactionsForAdmin() {
		String sql = "SELECT transaction_id, from_account, to_account, transaction_type, amount, timestamp, status "
				+ "FROM transactions ORDER BY transaction_id DESC LIMIT 30";
		StringBuilder sb = new StringBuilder("ADMIN_TRANSACTIONS_RESPONSE");
		try (Connection conn = DatabaseManager.getConnection();
		     Statement st = conn.createStatement();
		     ResultSet rs = st.executeQuery(sql)) {
			boolean found = false;
			while (rs.next()) {
				found = true;
				sb.append("|#").append(rs.getInt("transaction_id"))
						.append(" ").append(rs.getString("transaction_type"))
						.append(" [From:").append(rs.getString("from_account") != null ? rs.getString("from_account") : "-")
						.append(" To:").append(rs.getString("to_account") != null ? rs.getString("to_account") : "-")
						.append("] Amount:").append(String.format("%.2f", rs.getDouble("amount")))
						.append(" (").append(rs.getString("timestamp")).append(")");
			}
			if (!found) {
				sb.append("|No transactions recorded yet");
			}
			return sb.toString();
		} catch (SQLException e) {
			return "ERROR|DB_ERROR|" + e.getMessage();
		}
	}

	public static synchronized String updateAccountStatus(String accountNumber, String newStatus) {
		String sql = "UPDATE accounts SET status = ? WHERE account_number = ?";
		try (Connection conn = DatabaseManager.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, newStatus.trim().toUpperCase());
			ps.setString(2, accountNumber.trim());
			int updated = ps.executeUpdate();
			if (updated == 0) {
				return "ERROR|ACCOUNT_NOT_FOUND|Account " + accountNumber + " not found";
			}
			return "ADMIN_STATUS_UPDATED|" + accountNumber + "|" + newStatus.trim().toUpperCase();
		} catch (SQLException e) {
			return "ERROR|DB_ERROR|" + e.getMessage();
		}
	}

	private static void recordTransaction(Connection conn, String fromAcc, String toAcc,
	                                      String type, double amount, String status) throws SQLException {
		String sql = "INSERT INTO transactions (from_account, to_account, transaction_type, amount, status) VALUES (?, ?, ?, ?, ?)";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, fromAcc);
			ps.setString(2, toAcc);
			ps.setString(3, type);
			ps.setDouble(4, amount);
			ps.setString(5, status);
			ps.executeUpdate();
		}
	}
}

