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

    private final Socket client;

    private final BufferedReader in;

    private final PrintWriter out;

    private final Object outputLock = new Object();

    private final int client_id;

    // Session information shared with the admin monitoring thread

    private volatile String loggedInUser = "Guest";

    private volatile String role = "NONE";

    private volatile String accountNumber = "-";

    private volatile int loginLogId = 0;

    public Server_Thread(Socket client_socket, int cid) throws IOException {

        this.client = client_socket;

        this.in = new BufferedReader(

                new InputStreamReader(client.getInputStream()));

        this.out = new PrintWriter(client.getOutputStream(), true);

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

    public void sendNotification(String message) {

    synchronized (outputLock) {

        out.println("NOTIFICATION|" + message);

        if (out.checkError()) {

            System.out.println(

                    "[WARNING] Failed to send notification to Client #"

                            + client_id

            );

        }

    }

}

@Override

public void run() {

    try {

        synchronized (outputLock) {

            out.println("Welcome to NetBank Banking System");

        }

        while (true) {

            String input = in.readLine();

            if (input == null) {

                System.out.println(

                        "Client " + client_id + " (" + loggedInUser

                                + ") disconnected.\n");

                break;

            }

            System.out.println(

                    "Client " + client_id + " (" + loggedInUser

                            + ") says : " + input);

            String output = processCommand(input.trim());

            System.out.println(

                    "Server -> Client " + client_id + " : "

                            + output + "\n");

            synchronized (outputLock) {

                out.println(output);

            }

            if (out.checkError()) {

                System.out.println(

                        "[INFO] Communication error with Client #"

                                + client_id);

                break;

            }

            // Reset session information after successful logout

            if ("LOGOUT_SUCCESS".equals(output)) {

                this.loggedInUser = "Guest";

                this.role = "NONE";

                this.accountNumber = "-";

            }

        }

    } catch (IOException e) {

        System.out.println(

                "Client " + client_id

                        + " connection closed: " + e.getMessage());

    } finally {

        // 1. Record logout if the client was logged in

        try {

            if (loginLogId > 0) {

                BankingService.recordLogout(loginLogId);

                loginLogId = 0;

            }

        } catch (Exception e) {

            System.err.println(

                    "[ERROR] Failed to record client logout: "

                            + e.getMessage());

        }

        // 2. Close the database connection

        try {

            DatabaseManager.closeThreadConnection();

        } catch (Exception e) {

            System.err.println(

                    "[ERROR] Failed to close database connection: "

                            + e.getMessage());

        }

        // 3. Remove this client from the connected clients list

        synchronized (BankServer.connectedClients) {

            BankServer.connectedClients.remove(this);

        }

        // 4. Close the client socket and its associated streams

        try {

            client.close();

        } catch (IOException e) {

            System.err.println(

                    "[ERROR] Failed to close client socket: "

                            + e.getMessage());

        }

        System.out.println(

                "[INFO] Client #" + client_id

                        + " cleanup completed.");

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

                    return BankingService.registerCustomer(

                            parts[1],

                            parts[2],

                            parts[3],

                            Double.parseDouble(parts[4]));

                case "LOGIN":

                    // LOGIN|username|password

                    if (parts.length < 3) {

                        return "ERROR|INVALID_FORMAT|Usage: LOGIN|username|password";

                    }

                    if (!"NONE".equals(this.role)) {

                        return "ERROR|ALREADY_AUTHENTICATED|Please logout before logging in again";

                    }

                    String loginResult = BankingService.login(

                            parts[1],

                            parts[2],

                            getClientIp());

                    if (loginResult.startsWith("LOGIN_SUCCESS|")) {

                        String[] resParts = loginResult.split("\\|");

                        this.accountNumber = resParts[1];

                        this.role = resParts[2];

                        this.loggedInUser = parts[1].trim();

                        this.loginLogId = Integer.parseInt(resParts[4]);

                        return "LOGIN_SUCCESS|"

                                + this.accountNumber + "|"

                                + this.role;

                    }

                    return loginResult;

                case "BALANCE":

                    // BALANCE|100234

                    if ("NONE".equals(this.role)) {

                        return "ERROR|UNAUTHORIZED|Please login first";

                    }

                    String balAcc = (parts.length >= 2 && !parts[1].isEmpty())

                            ? parts[1]

                            : this.accountNumber;

                    if (!isAuthorizedAccount(balAcc)) {

                        return "ERROR|UNAUTHORIZED|You can only access your own account";

                    }

                    return BankingService.getBalance(balAcc);

                case "DEPOSIT":

                    // DEPOSIT|100234|10000

                    if ("NONE".equals(this.role)) {

                        return "ERROR|UNAUTHORIZED|Please login first";

                    }

                    if (parts.length < 3) {

                        return "ERROR|INVALID_FORMAT|Usage: DEPOSIT|accountNumber|amount";

                    }

                    if (!isAuthorizedAccount(parts[1])) {

                        return "ERROR|UNAUTHORIZED|You can only access your own account";

                    }

                    String depositResult = BankingService.deposit(
                            parts[1],
                            Double.parseDouble(parts[2]));

                    if (depositResult.startsWith("DEPOSIT_SUCCESS|")) {

                        String[] depositParts = depositResult.split("\\|");

                        String notification =
                                "Deposit of Rs. " + parts[2]
                                        + " completed successfully. New balance: Rs. "
                                        + depositParts[1];

                        // Notify the customer whose account received the deposit.
                        BankServer.notifyAccountHolder(parts[1], notification);

                        // Notify all connected administrators.
                        BankServer.notifyAdmins(
                                "Customer deposit: Account " + parts[1]
                                        + " deposited Rs. " + parts[2]);
                    }

                    return depositResult;

                case "WITHDRAW":

                    // WITHDRAW|100234|5000

                    if ("NONE".equals(this.role)) {

                        return "ERROR|UNAUTHORIZED|Please login first";

                    }

                    if (parts.length < 3) {

                        return "ERROR|INVALID_FORMAT|Usage: WITHDRAW|accountNumber|amount";

                    }

                    if (!isAuthorizedAccount(parts[1])) {

                        return "ERROR|UNAUTHORIZED|You can only access your own account";

                    }

                    String withdrawResult = BankingService.withdraw(
                            parts[1],
                            Double.parseDouble(parts[2]));

                    if (withdrawResult.startsWith("WITHDRAW_SUCCESS|")) {

                        String[] withdrawParts = withdrawResult.split("\\|");

                        String notification =
                                "Withdrawal of Rs. " + parts[2]
                                        + " completed successfully. New balance: Rs. "
                                        + withdrawParts[1];

                        // Notify the customer whose account was debited.
                        BankServer.notifyAccountHolder(parts[1], notification);

                        // Notify all connected administrators.
                        BankServer.notifyAdmins(
                                "Customer withdrawal: Account " + parts[1]
                                        + " withdrew Rs. " + parts[2]);
                    }

                    return withdrawResult;

                case "TRANSFER":

                    // TRANSFER|100234|100567|20000

                    if ("NONE".equals(this.role)) {

                        return "ERROR|UNAUTHORIZED|Please login first";

                    }

                    if (parts.length < 4) {

                        return "ERROR|INVALID_FORMAT|Usage: TRANSFER|fromAccount|toAccount|amount";

                    }

                    if (!isAuthorizedAccount(parts[1])) {

                        return "ERROR|UNAUTHORIZED|You can only transfer from your own account";

                    }

                    String transferResult = BankingService.transfer(
                            parts[1],
                            parts[2],
                            Double.parseDouble(parts[3]));

                    if (transferResult.startsWith("TRANSFER_SUCCESS|")) {

                        String[] transferParts = transferResult.split("\\|");

                        // Notify the sender.
                        BankServer.notifyAccountHolder(
                                parts[1],
                                "Transfer of Rs. " + parts[3]
                                        + " completed successfully to account "
                                        + parts[2]
                                        + ". New balance: Rs. "
                                        + transferParts[1]);

                        // Notify the recipient.
                        BankServer.notifyAccountHolder(
                                parts[2],
                                "You received Rs. " + parts[3]
                                        + " from account " + parts[1]);

                        // Notify all connected administrators.
                        BankServer.notifyAdmins(
                                "Customer transfer: Rs. " + parts[3]
                                        + " transferred from account "
                                        + parts[1] + " to account " + parts[2]);
                    }

                    return transferResult;

                case "HISTORY":

                    // HISTORY|100234

                    if ("NONE".equals(this.role)) {

                        return "ERROR|UNAUTHORIZED|Please login first";

                    }

                    String histAcc = (parts.length >= 2 && !parts[1].isEmpty())

                            ? parts[1]

                            : this.accountNumber;

                    if (!isAuthorizedAccount(histAcc)) {

                        return "ERROR|UNAUTHORIZED|You can only access your own account";

                    }

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

                    return BankingService.updateAccountStatus(

                            parts[1],

                            parts[2]);

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

            System.err.println(

                    "[ERROR] Client #" + client_id

                            + " command processing failed: "

                            + e.getMessage());

            return "ERROR|SERVER_ERROR|An internal server error occurred";

        }

    }

    private boolean isAuthorizedAccount(String requestedAccount) {

        return "ADMIN".equals(this.role)

                || (requestedAccount != null

                        && requestedAccount.trim().equals(this.accountNumber));

    }

}
