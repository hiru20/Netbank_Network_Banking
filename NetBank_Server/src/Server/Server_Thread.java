package Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**

 * Dedicated worker thread per connected client socket.
 */
public class Server_Thread extends Thread {

	private Socket client;
	private BufferedReader in;
	private PrintWriter out;
	private int client_id;

	private String loggedInUser = "Guest";
	private String role = "NONE";
	private String accountNumber = "-";
	private int loginLogId = 0;

	public Server_Thread(Socket client_socket, int cid) throws IOException {
		this.client = client_socket;
		in = new BufferedReader(new InputStreamReader(client.getInputStream()));
		out = new PrintWriter(client.getOutputStream(), true);
		this.client_id = cid;
	}

	public int getClientId() {
		return client_id;
	}

	public String getClientIp() {
		return client.getInetAddress().getHostAddress();
	}

	public String getLoggedInUser() {
		return loggedInUser;
	}

	public String getRole() {
		return role;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	@Override
	public void run() {
		try {
			out.println("Welcome to NetBank Banking System");

			while (true) {
				String input = in.readLine();
				if (input == null) {
					System.out.println("Client " + client_id + " (" + loggedInUser + ") disconnected.\n");
					break;
				}

				System.out.println("Client " + client_id + " (" + loggedInUser + ") says : " + input);

				String output = processCommand(input.trim());
				System.out.println("Server -> Client " + client_id + " : " + output + "\n");
				out.println(output);

				if ("LOGOUT_SUCCESS".equals(output)) {
					this.loggedInUser = "Guest";
					this.role = "NONE";
					this.accountNumber = "-";
				}
			}
		} catch (IOException e) {
			System.out.println("Client " + client_id + " connection closed: " + e.getMessage());
		} finally {
			if (loginLogId > 0) {
				BankingService.recordLogout(loginLogId);
			}
			DatabaseManager.closeThreadConnection();
			synchronized (BankServer.connectedClients) {
				BankServer.connectedClients.remove(this);
			}
			try {
				client.close();
				out.close();
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private String processCommand(String input) {
		if (input.isEmpty()) {
			return "ERROR|EMPTY_COMMAND";
		}

		String[] parts = input.split("\\|");
		String command = parts[0].trim().toUpperCase();

		try {
			switch (command) {

			case "REGISTER":
				// REGISTER|username|password|accountType|initialBalance
				if (parts.length < 5) {
					return "ERROR|INVALID_FORMAT|Usage: REGISTER|username|password|SAVINGS|initialBalance";
				}
				return BankingService.registerCustomer(parts[1], parts[2], parts[3], Double.parseDouble(parts[4]));

			case "LOGIN":
				// LOGIN|username|password
				if (parts.length < 3) {
					return "ERROR|INVALID_FORMAT|Usage: LOGIN|username|password";
				}
				String loginResult = BankingService.login(parts[1], parts[2], getClientIp());
				if (loginResult.startsWith("LOGIN_SUCCESS|")) {
					String[] resParts = loginResult.split("\\|");
					this.accountNumber = resParts[1];
					this.role = resParts[2];
					this.loggedInUser = parts[1].trim();
					this.loginLogId = Integer.parseInt(resParts[4]);
					return "LOGIN_SUCCESS|" + this.accountNumber + "|" + this.role;
				}
				return loginResult;

			case "BALANCE":
				// BALANCE|100234
				if ("NONE".equals(this.role)) {
					return "ERROR|UNAUTHORIZED|Please login first";
				}
				String balAcc = (parts.length >= 2 && !parts[1].isEmpty()) ? parts[1] : this.accountNumber;
				return BankingService.getBalance(balAcc);

			case "DEPOSIT":
				// DEPOSIT|100234|10000
				if ("NONE".equals(this.role)) {
					return "ERROR|UNAUTHORIZED|Please login first";
				}
				if (parts.length < 3) {
					return "ERROR|INVALID_FORMAT|Usage: DEPOSIT|accountNumber|amount";
				}
				return BankingService.deposit(parts[1], Double.parseDouble(parts[2]));

			case "WITHDRAW":
				// WITHDRAW|100234|5000
				if ("NONE".equals(this.role)) {
					return "ERROR|UNAUTHORIZED|Please login first";
				}
				if (parts.length < 3) {
					return "ERROR|INVALID_FORMAT|Usage: WITHDRAW|accountNumber|amount";
				}
				return BankingService.withdraw(parts[1], Double.parseDouble(parts[2]));

			case "TRANSFER":
				// TRANSFER|100234|100567|20000
				if ("NONE".equals(this.role)) {
					return "ERROR|UNAUTHORIZED|Please login first";
				}
				if (parts.length < 4) {
					return "ERROR|INVALID_FORMAT|Usage: TRANSFER|fromAccount|toAccount|amount";
				}
				return BankingService.transfer(parts[1], parts[2], Double.parseDouble(parts[3]));

			case "HISTORY":
				// HISTORY|100234
				if ("NONE".equals(this.role)) {
					return "ERROR|UNAUTHORIZED|Please login first";
				}
				String histAcc = (parts.length >= 2 && !parts[1].isEmpty()) ? parts[1] : this.accountNumber;
				return BankingService.getTransactionHistory(histAcc);

			case "ADMIN_ACCOUNTS":
				if (!"ADMIN".equals(this.role)) {
					return "ERROR|UNAUTHORIZED|Admin access required";
				}
				return BankingService.getAllAccountsForAdmin();

			case "ADMIN_TRANSACTIONS":
				if (!"ADMIN".equals(this.role)) {
					return "ERROR|UNAUTHORIZED|Admin access required";
				}
				return BankingService.getAllTransactionsForAdmin();

			case "ADMIN_CLIENTS":
				if (!"ADMIN".equals(this.role)) {
					return "ERROR|UNAUTHORIZED|Admin access required";
				}
				return BankServer.getConnectedClientsSummary();

			case "ADMIN_STATUS":
				// ADMIN_STATUS|accountNumber|ACTIVE_or_FROZEN
				if (!"ADMIN".equals(this.role)) {
					return "ERROR|UNAUTHORIZED|Admin access required";
				}
				if (parts.length < 3) {
					return "ERROR|INVALID_FORMAT|Usage: ADMIN_STATUS|accountNumber|ACTIVE_or_FROZEN";
				}
				return BankingService.updateAccountStatus(parts[1], parts[2]);

			case "LOGOUT":
				if (loginLogId > 0) {
					BankingService.recordLogout(loginLogId);
					loginLogId = 0;
				}
				return "LOGOUT_SUCCESS";

			default:
				return "ERROR|UNKNOWN_COMMAND|" + command;
			}
		} catch (NumberFormatException e) {
			return "ERROR|INVALID_AMOUNT|Amount must be a valid number";
		} catch (Exception e) {
			return "ERROR|SERVER_ERROR|" + e.getMessage();
		}
	}

}

