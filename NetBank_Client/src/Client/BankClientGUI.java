package Client;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Member 2 - Banking / Client Application (GUI)
 * Graphical Swing Client that communicates with BankServer over TCP port 6677.
 */
public class BankClientGUI extends JFrame {

	private static final int PORT = 6677;

	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;

	private String currentAccount = "";
	private String currentRole = "NONE";

	private final JTextField serverIpField = new JTextField("localhost", 12);
	private final JButton connectBtn = new JButton("Connect to Server");
	private final JLabel statusLabel = new JLabel("Not Connected");

	private final CardLayout cardLayout = new CardLayout();
	private final JPanel mainPanel = new JPanel(cardLayout);

	// Login / Register fields
	private final JTextField loginUserField = new JTextField(14);
	private final JPasswordField loginPassField = new JPasswordField(14);
	private final JTextField regUserField = new JTextField(14);
	private final JPasswordField regPassField = new JPasswordField(14);
	private final JComboBox<String> regTypeBox = new JComboBox<String>(new String[]{"SAVINGS", "CURRENT"});
	private final JTextField regBalanceField = new JTextField("10000", 14);

	// Customer fields
	private final JLabel accountHeaderLabel = new JLabel("Account: -");
	private final JLabel balanceLabel = new JLabel("Balance: -");
	private final JTextField depositField = new JTextField(10);
	private final JTextField withdrawField = new JTextField(10);
	private final JTextField transferToField = new JTextField(10);
	private final JTextField transferAmountField = new JTextField(10);

	// Output Log Area
	private final JTextArea logArea = new JTextArea(10, 60);

	public BankClientGUI() {
		super("NetBank Client Application");
		setSize(780, 560);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout(8, 8));

		// Top Connection Bar
		JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
		topBar.setBorder(BorderFactory.createTitledBorder("TCP Server Connection (Port " + PORT + ")"));
		topBar.add(new JLabel("Server IP:"));
		topBar.add(serverIpField);
		topBar.add(connectBtn);
		topBar.add(statusLabel);
		connectBtn.addActionListener(e -> connectToServer());
		add(topBar, BorderLayout.NORTH);

		// Center Cards: AUTH, CUSTOMER, ADMIN
		mainPanel.add(createAuthPanel(), "AUTH");
		mainPanel.add(createCustomerPanel(), "CUSTOMER");
		mainPanel.add(createAdminPanel(), "ADMIN");
		cardLayout.show(mainPanel, "AUTH");
		add(mainPanel, BorderLayout.CENTER);

		// Bottom Server Response Log
		logArea.setEditable(false);
		logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		JScrollPane logScroll = new JScrollPane(logArea);
		logScroll.setBorder(BorderFactory.createTitledBorder("Server Communication Log"));
		add(logScroll, BorderLayout.SOUTH);
	}

	private JPanel createAuthPanel() {
		JPanel panel = new JPanel(new GridLayout(1, 2, 12, 12));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// Login Box
		JPanel loginBox = new JPanel(new GridLayout(4, 2, 6, 10));
		loginBox.setBorder(BorderFactory.createTitledBorder("Login"));
		loginBox.add(new JLabel("Username:"));
		loginBox.add(loginUserField);
		loginBox.add(new JLabel("Password:"));
		loginBox.add(loginPassField);
		loginBox.add(new JLabel(""));
		JButton loginBtn = new JButton("Login");
		loginBtn.addActionListener(e -> doLogin());
		loginBox.add(loginBtn);

		// Register Box
		JPanel regBox = new JPanel(new GridLayout(5, 2, 6, 8));
		regBox.setBorder(BorderFactory.createTitledBorder("Register New Customer"));
		regBox.add(new JLabel("Username:"));
		regBox.add(regUserField);
		regBox.add(new JLabel("Password:"));
		regBox.add(regPassField);
		regBox.add(new JLabel("Account Type:"));
		regBox.add(regTypeBox);
		regBox.add(new JLabel("Initial Deposit:"));
		regBox.add(regBalanceField);
		regBox.add(new JLabel(""));
		JButton regBtn = new JButton("Register");
		regBtn.addActionListener(e -> doRegister());
		regBox.add(regBtn);

		panel.add(loginBox);
		panel.add(regBox);
		return panel;
	}

	private JPanel createCustomerPanel() {
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 6));
		accountHeaderLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
		balanceLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
		JButton checkBalBtn = new JButton("Check Balance");
		checkBalBtn.addActionListener(e -> sendBalanceInquiry());
		JButton historyBtn = new JButton("View Transaction History");
		historyBtn.addActionListener(e -> sendCommand("HISTORY|" + currentAccount));
		JButton logoutBtn = new JButton("Logout");
		logoutBtn.addActionListener(e -> doLogout());

		header.add(accountHeaderLabel);
		header.add(balanceLabel);
		header.add(checkBalBtn);
		header.add(historyBtn);
		header.add(logoutBtn);
		panel.add(header, BorderLayout.NORTH);

		JPanel ops = new JPanel(new GridLayout(3, 1, 8, 8));

		JPanel depPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		depPanel.setBorder(BorderFactory.createTitledBorder("Deposit"));
		depPanel.add(new JLabel("Amount:"));
		depPanel.add(depositField);
		JButton depBtn = new JButton("Deposit");
		depBtn.addActionListener(e -> {
			String resp = sendCommand("DEPOSIT|" + currentAccount + "|" + depositField.getText().trim());
			if (resp != null && resp.startsWith("DEPOSIT_SUCCESS|")) {
				balanceLabel.setText("Balance: " + resp.split("\\|")[1]);
			}
		});
		depPanel.add(depBtn);

		JPanel wdPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		wdPanel.setBorder(BorderFactory.createTitledBorder("Withdraw"));
		wdPanel.add(new JLabel("Amount:"));
		wdPanel.add(withdrawField);
		JButton wdBtn = new JButton("Withdraw");
		wdBtn.addActionListener(e -> {
			String resp = sendCommand("WITHDRAW|" + currentAccount + "|" + withdrawField.getText().trim());
			if (resp != null && resp.startsWith("WITHDRAW_SUCCESS|")) {
				balanceLabel.setText("Balance: " + resp.split("\\|")[1]);
			}
		});
		wdPanel.add(wdBtn);

		JPanel tfPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		tfPanel.setBorder(BorderFactory.createTitledBorder("Fund Transfer"));
		tfPanel.add(new JLabel("To Account #:"));
		tfPanel.add(transferToField);
		tfPanel.add(new JLabel("Amount:"));
		tfPanel.add(transferAmountField);
		JButton tfBtn = new JButton("Transfer");
		tfBtn.addActionListener(e -> {
			String resp = sendCommand("TRANSFER|" + currentAccount + "|" + transferToField.getText().trim()
					+ "|" + transferAmountField.getText().trim());
			if (resp != null && resp.startsWith("TRANSFER_SUCCESS|")) {
				balanceLabel.setText("Balance: " + resp.split("\\|")[1]);
			}
		});
		tfPanel.add(tfBtn);

		ops.add(depPanel);
		ops.add(wdPanel);
		ops.add(tfPanel);
		panel.add(ops, BorderLayout.CENTER);
		return panel;
	}

	private JPanel createAdminPanel() {
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
		actions.setBorder(BorderFactory.createTitledBorder("Administrator Controls"));

		JButton viewAccBtn = new JButton("View All Accounts");
		viewAccBtn.addActionListener(e -> sendCommand("ADMIN_ACCOUNTS"));

		JButton viewTxBtn = new JButton("View All Transactions");
		viewTxBtn.addActionListener(e -> sendCommand("ADMIN_TRANSACTIONS"));

		JButton viewClientsBtn = new JButton("Monitor Connected Clients");
		viewClientsBtn.addActionListener(e -> sendCommand("ADMIN_CLIENTS"));

		JButton freezeBtn = new JButton("Change Account Status...");
		freezeBtn.addActionListener(e -> {
			String acc = JOptionPane.showInputDialog(this, "Enter Account Number:");
			if (acc == null || acc.trim().isEmpty()) return;
			String st = JOptionPane.showInputDialog(this, "Enter Status (ACTIVE or FROZEN):", "FROZEN");
			if (st == null || st.trim().isEmpty()) return;
			sendCommand("ADMIN_STATUS|" + acc.trim() + "|" + st.trim());
		});

		JButton logoutBtn = new JButton("Logout");
		logoutBtn.addActionListener(e -> doLogout());

		actions.add(viewAccBtn);
		actions.add(viewTxBtn);
		actions.add(viewClientsBtn);
		actions.add(freezeBtn);
		actions.add(logoutBtn);

		panel.add(actions, BorderLayout.NORTH);
		return panel;
	}

	private void connectToServer() {
		try {
			InetAddress ipAddress = InetAddress.getByName(serverIpField.getText().trim());
			socket = new Socket(ipAddress, PORT);
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			String welcome = in.readLine();
			appendLog("Server says : " + welcome);
			statusLabel.setText("Connected to " + ipAddress.getHostAddress() + ":" + PORT);
			connectBtn.setEnabled(false);
			serverIpField.setEnabled(false);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "Connection Failed: " + e.getMessage());
		}
	}

	private String sendCommand(String command) {
		if (socket == null || socket.isClosed()) {
			connectToServer();
			if (socket == null || socket.isClosed()) return null;
		}
		try {
			appendLog("Client : " + command);
			out.println(command);
			String response = in.readLine();
			if (response != null) {
				for (String part : response.split("\\|")) {
					appendLog("Server says : " + part);
				}
			}
			return response;
		} catch (IOException e) {
			appendLog("Error : " + e.getMessage());
			return null;
		}
	}

	private void doLogin() {
		String user = loginUserField.getText().trim();
		String pass = new String(loginPassField.getPassword()).trim();
		String resp = sendCommand("LOGIN|" + user + "|" + pass);
		if (resp != null && resp.startsWith("LOGIN_SUCCESS|")) {
			String[] parts = resp.split("\\|");
			currentAccount = parts[1];
			currentRole = parts.length > 2 ? parts[2] : "CUSTOMER";
			if ("ADMIN".equalsIgnoreCase(currentRole)) {
				cardLayout.show(mainPanel, "ADMIN");
			} else {
				accountHeaderLabel.setText("Account: " + currentAccount);
				sendBalanceInquiry();
				cardLayout.show(mainPanel, "CUSTOMER");
			}
		} else if (resp != null) {
			JOptionPane.showMessageDialog(this, resp);
		}
	}

	private void doRegister() {
		String user = regUserField.getText().trim();
		String pass = new String(regPassField.getPassword()).trim();
		String type = (String) regTypeBox.getSelectedItem();
		String bal = regBalanceField.getText().trim();
		String resp = sendCommand("REGISTER|" + user + "|" + pass + "|" + type + "|" + bal);
		if (resp != null) {
			JOptionPane.showMessageDialog(this, resp);
		}
	}

	private void sendBalanceInquiry() {
		String resp = sendCommand("BALANCE|" + currentAccount);
		if (resp != null && resp.startsWith("BALANCE_RESPONSE|")) {
			balanceLabel.setText("Balance: " + resp.split("\\|")[1]);
		}
	}

	private void doLogout() {
		sendCommand("LOGOUT");
		currentAccount = "";
		currentRole = "NONE";
		cardLayout.show(mainPanel, "AUTH");
	}

	private void appendLog(String msg) {
		logArea.append(msg + "\n");
		logArea.setCaretPosition(logArea.getDocument().getLength());
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			BankClientGUI gui = new BankClientGUI();
			gui.setVisible(true);
		});
	}
}

