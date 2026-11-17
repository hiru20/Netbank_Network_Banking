
package Server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Multi-threaded TCP Socket Server for NetBank.
 * Uses a bounded thread pool and TCP_NODELAY for low-latency communication.
 */
public class BankServer {

    private static final int PORT = 6677;

    private static final int MAX_CLIENT_THREADS = 10;
    private static final int CLIENT_QUEUE_CAPACITY = 50;

    public static ArrayList<Server_Thread> connectedClients =
            new ArrayList<Server_Thread>();

    public static void main(String[] args) throws IOException {

        System.out.println("\t\t NetBank Server");
        System.out.println("\t\t====================\n");

        // Initialize database
        DatabaseManager.initializeDatabase();

        displayNetworkAddresses();

        System.out.println("Listening on Port : " + PORT);
        System.out.println("Waiting for client connections............\n\n");

        // Create and configure the server socket
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(PORT));

        // Create a bounded thread pool
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
                MAX_CLIENT_THREADS,
                MAX_CLIENT_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(CLIENT_QUEUE_CAPACITY),
                new ThreadPoolExecutor.AbortPolicy()
        );

        int current_clients = 1;

        try {

            while (true) {

                // Wait for incoming client connections
                Socket client = serverSocket.accept();

                try {

                    client.setTcpNoDelay(true);
                    client.setKeepAlive(true);

                    Server_Thread ct =
                            new Server_Thread(client, current_clients);

                    // Add client to the connected clients list
                    synchronized (connectedClients) {
                        connectedClients.add(ct);
                    }

                    try {

                        // Submit client task to the thread pool
                        threadPool.execute(ct);

                        System.out.println(
                                "Client " + current_clients
                                        + " is Connected from "
                                        + client.getInetAddress().getHostAddress()
                                        + "............\n"
                        );

                        current_clients++;

                    } catch (RejectedExecutionException e) {

                        // Remove rejected client from the active list
                        synchronized (connectedClients) {
                            connectedClients.remove(ct);
                        }

                        client.close();

                        System.out.println(
                                "[WARNING] Server is busy. Connection rejected from "
                                        + client.getInetAddress().getHostAddress()
                        );
                    }

                } catch (IOException e) {

                    System.err.println(
                            "[ERROR] Failed to initialize client connection: "
                                    + e.getMessage()
                    );

                    try {
                        client.close();
                    } catch (IOException closeException) {
                        System.err.println(
                                "[ERROR] Failed to close client socket: "
                                        + closeException.getMessage()
                        );
                    }
                }
            }

        } finally {

            System.out.println("[INFO] Shutting down NetBank server...");

            // Stop accepting new client tasks
            threadPool.shutdown();

            try {

                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {

                    System.out.println(
                            "[WARNING] Some client tasks are still running."
                    );

                    threadPool.shutdownNow();
                }

            } catch (InterruptedException e) {

                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }

            serverSocket.close();

            System.out.println("[INFO] NetBank server stopped.");
        }
    }

    /**
     * Display available IPv4 addresses.
     */
    private static void displayNetworkAddresses() throws IOException {

        System.out.println("Available IPv4 Addresses:");

        Enumeration<NetworkInterface> interfaces =
                NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {

            NetworkInterface networkInterface = interfaces.nextElement();

            if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                continue;
            }

            Enumeration<InetAddress> addresses =
                    networkInterface.getInetAddresses();

            while (addresses.hasMoreElements()) {

                InetAddress address = addresses.nextElement();

                if (address instanceof Inet4Address
                        && !address.isLoopbackAddress()) {

                    System.out.println(
                            networkInterface.getDisplayName() + " : "
                                    + address.getHostAddress()
                    );
                }
            }
        }
    }

    /**
     * Return information about currently connected clients.
     */
    public static String getConnectedClientsSummary() {

        StringBuilder sb =
                new StringBuilder("ADMIN_CLIENTS_RESPONSE");

        synchronized (connectedClients) {

            if (connectedClients.isEmpty()) {

                sb.append("|No active clients");

            } else {

                for (Server_Thread ct : connectedClients) {

                    sb.append("|Client #").append(ct.getClientId())
                            .append(" [IP:").append(ct.getClientIp())
                            .append(" User:").append(ct.getLoggedInUser())
                            .append(" Role:").append(ct.getRole())
                            .append(" Acc:").append(ct.getAccountNumber())
                            .append("]");
                }
            }
        }

        return sb.toString();
    }
}
