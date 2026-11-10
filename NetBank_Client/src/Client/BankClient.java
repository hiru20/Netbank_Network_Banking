package Client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

/**
 * Member 2 - Banking / Client Application
 * Console TCP Client for NetBank (runs on Client Laptop).
 * Follows the exact structure of ChatClientT.java & ClientChat.java from EC5205 lecture labs.
 */
public class BankClient {

	static int PORT = 6677;

	public static void main(String[] args) throws IOException {

		System.out.println("\t\t NetBank Client");
		System.out.println("\t\t====================\n\n");

		Scanner scan = new Scanner(System.in);
		String input;
		String output;

		System.out.print("Enter Server IP Address (Press Enter for localhost): ");
		String hostInput = scan.nextLine().trim();

		// Assigning server IP address (uses InetAddress.getLocalHost() by default)
		InetAddress ipAddress;
		if (hostInput.isEmpty()) {
			ipAddress = InetAddress.getLocalHost();
		} else {
			ipAddress = InetAddress.getByName(hostInput);
		}

		// Creating Client Socket
		Socket socket = new Socket(ipAddress, PORT);
		System.out.println("Server is connected....\n");

		PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

		String currentAccount = "";
		String currentRole = "NONE";

		try {
			// Reading the welcome message from Server_Thread
			input = in.readLine();
			System.out.println("Server says : " + input + "\n");

			while (true) {
				if ("NONE".equals(currentRole)) {
					System.out.println("----------------------------------------");
					System.out.println("1. Login");
					System.out.println("2. Register New Customer Account");
					System.out.println("3. Exit");
					System.out.print("Select option : ");
					String choice = scan.nextLine().trim();

					if ("1".equals(choice)) {
						System.out.print("Username : ");
						String user = scan.nextLine().trim();
						System.out.print("Password : ");
						String pass = scan.nextLine().trim();

						output = "LOGIN|" + user + "|" + pass;
						out.println(output);

						input = in.readLine();
						System.out.println("Server says : " + input + "\n");

						if (input != null && input.startsWith("LOGIN_SUCCESS|")) {
							String[] parts = input.split("\\|");
							currentAccount = parts[1];
							currentRole = parts.length > 2 ? parts[2] : "CUSTOMER";
						}
					} else if ("2".equals(choice)) {
						System.out.print("New Username : ");
						String user = scan.nextLine().trim();
						System.out.print("New Password : ");
						String pass = scan.nextLine().trim();
						System.out.print("Account Type (SAVINGS / CURRENT) : ");
						String type = scan.nextLine().trim();
						if (type.isEmpty()) type = "SAVINGS";
						System.out.print("Initial Deposit Amount : ");
						String amount = scan.nextLine().trim();

						output = "REGISTER|" + user + "|" + pass + "|" + type + "|" + amount;
						out.println(output);

						input = in.readLine();
						System.out.println("Server says : " + input + "\n");
					} else if ("3".equals(choice)) {
						System.out.println("Closing Connection.....");
						break;
					}

				} else if ("CUSTOMER".equalsIgnoreCase(currentRole)) {
					System.out.println("----------------------------------------");
					System.out.println("Customer Menu (Account: " + currentAccount + ")");
					System.out.println("1. Balance Inquiry");
					System.out.println("2. Deposit Money");
					System.out.println("3. Withdraw Money");
					System.out.println("4. Fund Transfer");
					System.out.println("5. Transaction History");
					System.out.println("6. Logout");
					System.out.print("Select option : ");
					String choice = scan.nextLine().trim();

					if ("1".equals(choice)) {
						output = "BALANCE|" + currentAccount;
						out.println(output);
						input = in.readLine();
						System.out.println("Server says : " + input + "\n");

					} else if ("2".equals(choice)) {
						System.out.print("Enter Deposit Amount : ");
						String amt = scan.nextLine().trim();
						output = "DEPOSIT|" + currentAccount + "|" + amt;
						out.println(output);
						input = in.readLine();
						System.out.println("Server says : " + input + "\n");

					} else if ("3".equals(choice)) {
						System.out.print("Enter Withdrawal Amount : ");
						String amt = scan.nextLine().trim();
						output = "WITHDRAW|" + currentAccount + "|" + amt;
						out.println(output);
						input = in.readLine();
						System.out.println("Server says : " + input + "\n");

					} else if ("4".equals(choice)) {
						System.out.print("Enter Receiver Account Number : ");
						String toAcc = scan.nextLine().trim();
						System.out.print("Enter Transfer Amount : ");
						String amt = scan.nextLine().trim();
						output = "TRANSFER|" + currentAccount + "|" + toAcc + "|" + amt;
						out.println(output);
						input = in.readLine();
						System.out.println("Server says : " + input + "\n");

					} else if ("5".equals(choice)) {
						output = "HISTORY|" + currentAccount;
						out.println(output);
						input = in.readLine();
						printFormattedResponse(input);

					} else if ("6".equals(choice)) {
						out.println("LOGOUT");
						input = in.readLine();
						System.out.println("Server says : " + input + "\n");
						currentRole = "NONE";
						currentAccount = "";
					}

				} else if ("ADMIN".equalsIgnoreCase(currentRole)) {
					System.out.println("----------------------------------------");
					System.out.println("Administrator Menu");
					System.out.println("1. View All Customer Accounts");
					System.out.println("2. View All Transactions");
					System.out.println("3. Monitor Connected Clients");
					System.out.println("4. Change Account Status (ACTIVE / FROZEN)");
					System.out.println("5. Logout");
					System.out.print("Select option : ");
					String choice = scan.nextLine().trim();

					if ("1".equals(choice)) {
						out.println("ADMIN_ACCOUNTS");
						input = in.readLine();
						printFormattedResponse(input);

					} else if ("2".equals(choice)) {
						out.println("ADMIN_TRANSACTIONS");
						input = in.readLine();
						printFormattedResponse(input);

					} else if ("3".equals(choice)) {
						out.println("ADMIN_CLIENTS");
						input = in.readLine();
						printFormattedResponse(input);

					} else if ("4".equals(choice)) {
						System.out.print("Enter Account Number : ");
						String acc = scan.nextLine().trim();
						System.out.print("Enter New Status (ACTIVE / FROZEN) : ");
						String st = scan.nextLine().trim();
						out.println("ADMIN_STATUS|" + acc + "|" + st);
						input = in.readLine();
						System.out.println("Server says : " + input + "\n");

					} else if ("5".equals(choice)) {
						out.println("LOGOUT");
						input = in.readLine();
						System.out.println("Server says : " + input + "\n");
						currentRole = "NONE";
						currentAccount = "";
					}
				}
			}
		} finally {
			scan.close();
			out.close();
			in.close();
			socket.close();
		}
	}

	private static void printFormattedResponse(String response) {
		if (response == null) return;
		String[] parts = response.split("\\|");
		System.out.println("Server says : " + parts[0]);
		for (int i = 1; i < parts.length; i++) {
			System.out.println("   " + parts[i]);
		}
		System.out.println();
	}
}

