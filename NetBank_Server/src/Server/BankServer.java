package Server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.NetworkInterface;
import java.net.Inet4Address;
import java.util.Enumeration;

/**
 * Multi-threaded TCP Socket Server for NetBank (runs on Server Laptop).
 * Uses ExecutorService thread pooling and TCP_NODELAY for low-latency Wi-Fi communication.
 */
public class BankServer {

	private static final int PORT = 6677;
	public static ArrayList<Server_Thread> connectedClients = new ArrayList<Server_Thread>();

	public static void main(String[] args) throws IOException {

		System.out.println("\t\t NetBank Server");
		System.out.println("\t\t====================\n");

		// Initialize Database tables and indexes (users, accounts, transactions, login_logs)
		DatabaseManager.initializeDatabase();

		displayNetworkAddresses();
		System.out.println("Listening on Port : " + PORT);
		System.out.println("Waiting for client connections............\n\n");

		ServerSocket serverSocket = new ServerSocket(PORT);
		serverSocket.setReuseAddress(true);
		ExecutorService threadPool = Executors.newCachedThreadPool();
		int current_clients = 1;

		try {
			while (true) {
				// Listening for any client socket requests
				Socket client = serverSocket.accept();
				client.setTcpNoDelay(true);
				client.setKeepAlive(true);

				System.out.println("Client " + current_clients + " is Connected from "
						+ client.getInetAddress().getHostAddress() + "............\n");

				Server_Thread ct = new Server_Thread(client, current_clients);
				synchronized (connectedClients) {
					connectedClients.add(ct);
				}
				threadPool.execute(ct);
				current_clients++;
			}
		} finally {
			threadPool.shutdown();
			serverSocket.close();
		}

	}

	private static void displayNetworkAddresses() throws IOException {

        System.out.println("Available IPv4 Addresses:");

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {

                NetworkInterface networkInterface = interfaces.nextElement();

                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                        continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();

                while (addresses.hasMoreElements()) {

                        InetAddress address = addresses.nextElement();

                        if (address instanceof Inet4Address && !address.isLoopbackAddress()) {

                                System.out.println(
                                                networkInterface.getDisplayName() + " : "
                                                                + address.getHostAddress());
                        }
                }
        }
}

	public static String getConnectedClientsSummary() {
		StringBuilder sb = new StringBuilder("ADMIN_CLIENTS_RESPONSE");
		synchronized (connectedClients) {
			if (connectedClients.isEmpty()) {
				sb.append("|No active clients");
			} else {
				for (Server_Thread ct : connectedClients) {
					sb.append("|Client #").append(ct.getClientId())
							.append(" [IP:").append(ct.getClientIp())
							.append(" User:").append(ct.getLoggedInUser())
							.append(" Role:").append(ct.getRole())
							.append(" Acc:").append(ct.getAccountNumber()).append("]");
				}
			}
		}
		return sb.toString();
	}

}
