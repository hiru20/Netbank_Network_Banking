
package Client;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * NetBank - Banking System
 * Java Swing GUI Client
 *
 * Communicates with the central bank server using TCP sockets.
 */
public class BankClientGUI extends JFrame {

    private static final int PORT = 6677;

    // Network components
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // Current session
    private String currentAccount = "";
    private String currentRole = "NONE";

    // Main navigation
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel = new JPanel(cardLayout);

    // Server connection components
    private final JTextField serverIpField =
            new JTextField("localhost", 15);

    private final JButton connectBtn =
            new StyledButton("Connect to Server");

    private final JLabel statusLabel =
            new JLabel("Not connected");

    private volatile boolean commandInProgress;

    // Authentication fields
    private final JTextField loginUserField =
            new JTextField(20);

    private final JPasswordField loginPassField =
            new JPasswordField(20);

    private final JCheckBox showLoginPassword =
            new JCheckBox("Show password");

    private final JTextField regUserField =
            new JTextField(20);

    private final JPasswordField regPassField =
            new JPasswordField(20);

    private final JCheckBox showRegistrationPassword =
            new JCheckBox("Show password");

    private final JComboBox<String> regTypeBox =
            new JComboBox<>(new String[]{"SAVINGS", "CURRENT"});

    private final JTextField regBalanceField =
            new JTextField("10000", 20);

    // Customer components
    private final JLabel accountHeaderLabel =
            new JLabel("Account: -");

    private final JLabel balanceLabel =
            new JLabel("Balance: -");

    private final JTextField depositField =
            new JTextField(12);

    private final JTextField withdrawField =
            new JTextField(12);

    private final JTextField transferToField =
            new JTextField(12);

    private final JTextField transferAmountField =
            new JTextField(12);

    // Communication log
    private final JTextArea logArea =
            new JTextArea(10, 60);

    // -------------------------------------------------
    // CONSTRUCTOR
    // -------------------------------------------------

    public BankClientGUI() {

        super("NetBank | Banking System");

        setSize(1150, 750);
        setMinimumSize(new Dimension(950, 650));
        setResizable(true);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        getContentPane().setBackground(AppColors.BACKGROUND);

        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Create application screens
        mainPanel.setBackground(AppColors.BACKGROUND);

        mainPanel.add(createAuthPanel(), "AUTH");
        mainPanel.add(createCustomerPanel(), "CUSTOMER");
        mainPanel.add(createAdminPanel(), "ADMIN");

        cardLayout.show(mainPanel, "AUTH");

        setContentPane(mainPanel);

        // Close network connection when application closes
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeConnection();
                dispose();
                System.exit(0);
            }
        });
    }

    // -------------------------------------------------
    // AUTHENTICATION SCREEN
    // -------------------------------------------------

    private JPanel createAuthPanel() {

        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(AppColors.SURFACE);

        // LEFT BRANDING PANEL
        JPanel brandingPanel = new JPanel();

        brandingPanel.setLayout(
                new BoxLayout(brandingPanel, BoxLayout.Y_AXIS));

        brandingPanel.setBackground(AppColors.PRIMARY);

        brandingPanel.setBorder(
                new EmptyBorder(55, 40, 35, 40));

        JLabel logo = new JLabel("NETBANK");

        logo.setFont(new Font("SansSerif", Font.BOLD, 32));
        logo.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("BANKING SYSTEM");

        subtitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        subtitle.setForeground(new Color(147, 197, 253));

        JLabel welcome = new JLabel(
                "<html>Welcome to<br>smarter banking.</html>");

        welcome.setFont(new Font("SansSerif", Font.BOLD, 32));
        welcome.setForeground(Color.WHITE);

        JLabel description = new JLabel(
                "<html>Manage your accounts, transfer money,<br>"
                + "and monitor your transactions through<br>"
                + "a secure network-based banking system.</html>");

        description.setFont(
                new Font("SansSerif", Font.PLAIN, 14));

        description.setForeground(
                new Color(219, 234, 254));

        brandingPanel.add(logo);
        brandingPanel.add(Box.createVerticalStrut(5));
        brandingPanel.add(subtitle);

        brandingPanel.add(Box.createVerticalStrut(75));

        brandingPanel.add(welcome);
        brandingPanel.add(Box.createVerticalStrut(20));
        brandingPanel.add(description);

        brandingPanel.add(Box.createVerticalGlue());

        // SERVER CONNECTION SECTION

        JLabel connectionTitle =
                new JLabel("SERVER CONNECTION");

        connectionTitle.setFont(
                new Font("SansSerif", Font.BOLD, 12));

        connectionTitle.setForeground(Color.WHITE);

        serverIpField.setFont(AppFonts.BODY);
        serverIpField.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 40));

        connectBtn.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 42));

        connectBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        connectBtn.addActionListener(e -> connectToServerAsync());

        statusLabel.setFont(AppFonts.SMALL);
        statusLabel.setForeground(new Color(253, 224, 71));

        brandingPanel.add(connectionTitle);
        brandingPanel.add(Box.createVerticalStrut(10));
        brandingPanel.add(serverIpField);

        brandingPanel.add(Box.createVerticalStrut(10));
        brandingPanel.add(connectBtn);

        brandingPanel.add(Box.createVerticalStrut(12));
        brandingPanel.add(statusLabel);

        // RIGHT AUTHENTICATION PANEL

        JPanel authPanel = new JPanel(new BorderLayout());

        authPanel.setBackground(AppColors.SURFACE);

        authPanel.setBorder(new EmptyBorder(45, 55, 45, 55));

        JPanel formCards = new JPanel(new CardLayout());

        formCards.setBackground(AppColors.SURFACE);

        formCards.add(
                createLoginForm(formCards), "LOGIN");

        formCards.add(
                createRegistrationForm(formCards), "REGISTER");

        authPanel.add(formCards, BorderLayout.CENTER);

        // COMBINE LEFT AND RIGHT PANELS

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                brandingPanel,
                authPanel
        );

        splitPane.setResizeWeight(0.42);
        splitPane.setDividerSize(0);
        splitPane.setBorder(null);
        splitPane.setEnabled(false);

        container.add(splitPane, BorderLayout.CENTER);

        return container;
    }

    // -------------------------------------------------
    // LOGIN FORM
    // -------------------------------------------------

    private JPanel createLoginForm(JPanel formCards) {

        JPanel panel = new JPanel();

        panel.setLayout(
                new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.setBackground(AppColors.SURFACE);

        JLabel heading = new JLabel("Sign in to NetBank");

        heading.setFont(AppFonts.TITLE);
        heading.setForeground(AppColors.PRIMARY);

        JLabel description = new JLabel(
                "Enter your credentials to access your account.");

        description.setFont(AppFonts.BODY);
        description.setForeground(AppColors.TEXT_SECONDARY);

        styleInput(loginUserField);
        styleInput(loginPassField);

        JButton loginBtn = new StyledButton("Sign In");

        loginBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        loginBtn.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 46));

        loginBtn.addActionListener(e -> doLogin());

        JButton registerLink =
                new JButton("Create an account");

        styleLinkButton(registerLink);

        registerLink.addActionListener(e ->
                ((CardLayout) formCards.getLayout())
                        .show(formCards, "REGISTER"));

        JLabel registerText =
                new JLabel("Don't have an account?");

        registerText.setFont(AppFonts.BODY);
        registerText.setForeground(AppColors.TEXT_SECONDARY);

        JPanel registerRow = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 0, 0));

        registerRow.setBackground(AppColors.SURFACE);
        registerRow.add(registerText);
        registerRow.add(registerLink);

        panel.add(heading);
        panel.add(Box.createVerticalStrut(12));

        panel.add(description);
        panel.add(Box.createVerticalStrut(45));

        panel.add(createInputRow(
                "USERNAME", loginUserField));

        panel.add(Box.createVerticalStrut(22));

        panel.add(createPasswordInputRow(
                "PASSWORD", loginPassField, showLoginPassword));

        panel.add(Box.createVerticalStrut(32));

        panel.add(loginBtn);
        panel.add(Box.createVerticalStrut(25));

        panel.add(registerRow);

        return panel;
    }

    // -------------------------------------------------
    // REGISTRATION FORM
    // -------------------------------------------------

    private JPanel createRegistrationForm(JPanel formCards) {

        JPanel panel = new JPanel();

        panel.setLayout(
                new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.setBackground(AppColors.SURFACE);

        JLabel heading = new JLabel("Create your account");

        heading.setFont(AppFonts.TITLE);
        heading.setForeground(AppColors.PRIMARY);

        JLabel description = new JLabel(
                "Register to start using NetBank.");

        description.setFont(AppFonts.BODY);
        description.setForeground(AppColors.TEXT_SECONDARY);

        styleInput(regUserField);
        styleInput(regPassField);
        styleInput(regBalanceField);

        styleComboBox(regTypeBox);

        JButton registerBtn =
                new StyledButton("Create Account");

        registerBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        registerBtn.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 46));

        registerBtn.addActionListener(e -> doRegister());

        JButton loginLink =
                new JButton("Back to Sign In");

        styleLinkButton(loginLink);

        loginLink.addActionListener(e ->
                ((CardLayout) formCards.getLayout())
                        .show(formCards, "LOGIN"));

        panel.add(heading);
        panel.add(Box.createVerticalStrut(10));

        panel.add(description);
        panel.add(Box.createVerticalStrut(25));

        panel.add(createInputRow(
                "USERNAME", regUserField));

        panel.add(Box.createVerticalStrut(14));

        panel.add(createPasswordInputRow(
                "PASSWORD", regPassField, showRegistrationPassword));

        panel.add(Box.createVerticalStrut(14));

        panel.add(createInputRow(
                "ACCOUNT TYPE", regTypeBox));

        panel.add(Box.createVerticalStrut(14));

        panel.add(createInputRow(
                "INITIAL DEPOSIT", regBalanceField));

        panel.add(Box.createVerticalStrut(24));

        panel.add(registerBtn);
        panel.add(Box.createVerticalStrut(15));

        panel.add(loginLink);

        return panel;
    }

    // -------------------------------------------------
    // REUSABLE GUI COMPONENTS
    // -------------------------------------------------

    private JPanel createInputRow(
            String labelText,
            JComponent input) {

        JPanel row = new JPanel();

        row.setLayout(
                new BoxLayout(row, BoxLayout.Y_AXIS));

        row.setBackground(AppColors.SURFACE);

        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText);

        label.setFont(
                new Font("SansSerif", Font.BOLD, 12));

        label.setForeground(AppColors.TEXT_PRIMARY);

        input.setAlignmentX(Component.LEFT_ALIGNMENT);

        input.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 42));

        row.add(label);
        row.add(Box.createVerticalStrut(8));
        row.add(input);

        return row;
    }

    private JPanel createPasswordInputRow(
            String labelText,
            JPasswordField input,
            JCheckBox visibilityToggle) {

        JPanel row = createInputRow(labelText, input);
        visibilityToggle.setFont(AppFonts.SMALL);
        visibilityToggle.setForeground(AppColors.TEXT_SECONDARY);
        visibilityToggle.setBackground(AppColors.SURFACE);
        visibilityToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        visibilityToggle.addActionListener(e ->
                input.setEchoChar(visibilityToggle.isSelected() ? (char) 0 : '\u2022'));
        row.add(Box.createVerticalStrut(5));
        row.add(visibilityToggle);
        return row;
    }

    private void styleInput(JTextField field) {

        field.setFont(AppFonts.BODY);

        field.setForeground(AppColors.TEXT_PRIMARY);
        field.setBackground(Color.WHITE);

        field.setBorder(
                BorderFactory.createCompoundBorder(
                        new RoundedBorder(AppColors.BORDER, 10),
                        BorderFactory.createEmptyBorder(
                                8, 12, 8, 12)
                )
        );

        field.setPreferredSize(
                new Dimension(300, 42));

        field.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 42));
    }

    private void styleComboBox(JComboBox<String> box) {
        box.setFont(AppFonts.BODY);
        box.setForeground(AppColors.TEXT_PRIMARY);
        box.setBackground(Color.WHITE);
        box.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(AppColors.BORDER, 10),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        box.setPreferredSize(new Dimension(300, 42));
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
    }

    private void styleLinkButton(JButton button) {

        button.setFont(AppFonts.BUTTON);

        button.setForeground(AppColors.PRIMARY_LIGHT);
        button.setBackground(AppColors.SURFACE);

        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);

        button.setCursor(
                new Cursor(Cursor.HAND_CURSOR));
    }

    // -------------------------------------------------
    // CUSTOMER DASHBOARD
    // -------------------------------------------------

    private JPanel createCustomerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(AppColors.BACKGROUND);

        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(AppColors.PRIMARY);
        sidebar.setPreferredSize(new Dimension(225, 0));
        sidebar.setBorder(new EmptyBorder(30, 18, 25, 18));

        JLabel logo = new JLabel("NETBANK");
        logo.setFont(new Font("SansSerif", Font.BOLD, 27));
        logo.setForeground(Color.WHITE);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("PERSONAL BANKING");
        subtitle.setFont(new Font("SansSerif", Font.BOLD, 10));
        subtitle.setForeground(new Color(147, 197, 253));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        sidebar.add(logo);
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(subtitle);
        sidebar.add(Box.createVerticalStrut(55));

        JLabel menuLabel = new JLabel("MAIN MENU");
        menuLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        menuLabel.setForeground(new Color(180, 195, 215));
        menuLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(menuLabel);
        sidebar.add(Box.createVerticalStrut(16));

        JButton dashboardButton = createCustomerSidebarButton("Dashboard");
        JButton accountButton = createCustomerSidebarButton("My Account");
        JButton historyButton = createCustomerSidebarButton("Transaction History");
        JButton logoutButton = createCustomerSidebarButton("Logout");

        sidebar.add(dashboardButton);
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(accountButton);
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(historyButton);
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(logoutButton);
        logoutButton.addActionListener(e -> doLogout());

        JPanel workspace = new JPanel(new BorderLayout(0, 24));
        workspace.setBackground(AppColors.BACKGROUND);
        workspace.setBorder(new EmptyBorder(28, 35, 30, 35));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColors.BACKGROUND);

        JPanel welcome = new JPanel();
        welcome.setLayout(new BoxLayout(welcome, BoxLayout.Y_AXIS));
        welcome.setBackground(AppColors.BACKGROUND);

        JLabel title = new JLabel("Good day, welcome back");
        title.setFont(AppFonts.TITLE);
        title.setForeground(AppColors.PRIMARY);

        JLabel description = new JLabel(
                "Manage your money securely from one simple workspace.");
        description.setFont(AppFonts.BODY);
        description.setForeground(AppColors.TEXT_SECONDARY);

        welcome.add(title);
        welcome.add(Box.createVerticalStrut(6));
        welcome.add(description);

        JButton logoutHeaderButton = new StyledButton("Logout");
        styleAdminButton(logoutHeaderButton, AppColors.DANGER);
        logoutHeaderButton.addActionListener(e -> doLogout());

        header.add(welcome, BorderLayout.WEST);
        header.add(logoutHeaderButton, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBackground(AppColors.BACKGROUND);

        JPanel summaryCards = new JPanel(new GridLayout(1, 2, 18, 0));
        summaryCards.setBackground(AppColors.BACKGROUND);
        summaryCards.add(createCustomerSummaryCard("CURRENT BALANCE",
                balanceLabel, AppColors.SUCCESS));
        summaryCards.add(createCustomerSummaryCard("ACCOUNT",
                accountHeaderLabel, AppColors.PRIMARY_LIGHT));

        JPanel operations = new JPanel(new GridLayout(1, 3, 18, 0));
        operations.setBackground(AppColors.BACKGROUND);

        JPanel depositPanel = createOperationPanel("Deposit funds",
                "Add money to your active account.");
        styleInput(depositField);
        depositPanel.add(createOperationLabel("AMOUNT"));
        depositPanel.add(depositField);
        JButton depositBtn = new StyledButton("Deposit");
        depositBtn.addActionListener(e -> {
            String amount = depositField.getText().trim();
            if (!isPositiveAmount(amount, "Deposit amount")) return;
            runCommand("DEPOSIT|" + currentAccount + "|" + amount, response -> {
                if (isSuccess(response, "DEPOSIT_SUCCESS|")) {
                    balanceLabel.setText("Balance: " + response.split("\\|")[1]);
                    depositField.setText("");
                    showInfo("Deposit completed successfully.");
                } else {
                    showError(response, "Deposit failed");
                }
            });
        });
        depositPanel.add(depositBtn);

        JPanel withdrawPanel = createOperationPanel("Withdraw funds",
                "Withdraw money from your active account.");
        styleInput(withdrawField);
        withdrawPanel.add(createOperationLabel("AMOUNT"));
        withdrawPanel.add(withdrawField);
        JButton withdrawBtn = new StyledButton("Withdraw");
        withdrawBtn.addActionListener(e -> {
            String amount = withdrawField.getText().trim();
            if (!isPositiveAmount(amount, "Withdrawal amount")) return;
            runCommand("WITHDRAW|" + currentAccount + "|" + amount, response -> {
                if (isSuccess(response, "WITHDRAW_SUCCESS|")) {
                    balanceLabel.setText("Balance: " + response.split("\\|")[1]);
                    withdrawField.setText("");
                    showInfo("Withdrawal completed successfully.");
                } else {
                    showError(response, "Withdrawal failed");
                }
            });
        });
        withdrawPanel.add(withdrawBtn);

        JPanel transferPanel = createOperationPanel("Transfer money",
                "Send money securely to another account.");
        styleInput(transferToField);
        styleInput(transferAmountField);
        transferPanel.add(createOperationLabel("RECIPIENT ACCOUNT"));
        transferPanel.add(transferToField);
        transferPanel.add(createOperationLabel("AMOUNT"));
        transferPanel.add(transferAmountField);
        JButton transferBtn = new StyledButton("Transfer");
        transferBtn.addActionListener(e -> {
            String recipient = transferToField.getText().trim();
            String amount = transferAmountField.getText().trim();
            if (recipient.isEmpty() || recipient.contains("|")) {
                showError("Enter a valid recipient account number.", "Transfer failed");
                return;
            }
            if (!isPositiveAmount(amount, "Transfer amount")) return;
            int confirmation = JOptionPane.showConfirmDialog(this,
                    "Transfer " + amount + " to account " + recipient + "?",
                    "Confirm transfer", JOptionPane.YES_NO_OPTION);
            if (confirmation != JOptionPane.YES_OPTION) return;
            runCommand("TRANSFER|" + currentAccount + "|" + recipient + "|" + amount,
                    response -> {
                        if (isSuccess(response, "TRANSFER_SUCCESS|")) {
                            balanceLabel.setText("Balance: " + response.split("\\|")[1]);
                            transferToField.setText("");
                            transferAmountField.setText("");
                            showInfo("Transfer completed successfully.");
                        } else {
                            showError(response, "Transfer failed");
                        }
                    });
        });
        transferPanel.add(transferBtn);

        operations.add(depositPanel);
        operations.add(withdrawPanel);
        operations.add(transferPanel);

        content.add(summaryCards, BorderLayout.NORTH);
        content.add(operations, BorderLayout.CENTER);
        workspace.add(header, BorderLayout.NORTH);
        workspace.add(content, BorderLayout.CENTER);

        dashboardButton.addActionListener(e -> {
            sendBalanceInquiry();
        });
        accountButton.addActionListener(e -> showAccountDetails());
        historyButton.addActionListener(e -> showTransactionHistory());

        panel.add(sidebar, BorderLayout.WEST);
        panel.add(workspace, BorderLayout.CENTER);
        return panel;
    }

    private JButton createCustomerSidebarButton(String text) {
        JButton button = new JButton(text);
        button.setFont(AppFonts.BODY);
        button.setForeground(Color.WHITE);
        button.setBackground(AppColors.PRIMARY);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JPanel createCustomerSummaryCard(String caption, JLabel value,
                                             Color valueColor) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(AppColors.BORDER, 16),
                BorderFactory.createEmptyBorder(18, 20, 18, 20)));

        JLabel captionLabel = new JLabel(caption);
        captionLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        captionLabel.setForeground(AppColors.TEXT_SECONDARY);
        captionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        value.setFont(AppFonts.HEADING);
        value.setForeground(valueColor);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(captionLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(value);
        return card;
    }

    private JLabel createOperationLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 11));
        label.setForeground(AppColors.TEXT_SECONDARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel createOperationPanel(String title, String description) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(AppColors.BORDER, 16),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppFonts.SUBHEADING);
        titleLabel.setForeground(AppColors.PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descriptionLabel = new JLabel("<html>" + description + "</html>");
        descriptionLabel.setFont(AppFonts.SMALL);
        descriptionLabel.setForeground(AppColors.TEXT_SECONDARY);
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(6));
        panel.add(descriptionLabel);
        panel.add(Box.createVerticalStrut(20));
        return panel;
    }

    private void showAccountDetails() {
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBorder(new EmptyBorder(10, 10, 10, 10));
        details.setBackground(Color.WHITE);

        details.add(createDetailRow("Username", loginUserField.getText()));
        details.add(createDetailRow("Account number", currentAccount));
        details.add(createDetailRow("Role", currentRole));
        details.add(createDetailRow("Balance", balanceLabel.getText().replace("Balance: ", "")));

        JOptionPane.showMessageDialog(this, details, "My Account",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel createDetailRow(String labelText, String valueText) {
        JPanel row = new JPanel(new BorderLayout(20, 0));
        row.setBackground(Color.WHITE);
        row.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        JLabel label = new JLabel(labelText);
        label.setFont(AppFonts.BODY);
        label.setForeground(AppColors.TEXT_SECONDARY);

        JLabel value = new JLabel(valueText == null || valueText.isEmpty() ? "-" : valueText);
        value.setFont(AppFonts.SUBHEADING);
        value.setForeground(AppColors.TEXT_PRIMARY);

        row.add(label, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private void showTransactionHistory() {
        runCommand("HISTORY|" + currentAccount, response -> showResponse(response,
                "Transaction History"));
    }

    // -------------------------------------------------
    // ADMIN DASHBOARD
    // -------------------------------------------------

private JPanel createAdminPanel() {

    JPanel dashboard = new JPanel(new BorderLayout());
    dashboard.setBackground(AppColors.BACKGROUND);

    // =========================================
    // LEFT SIDEBAR
    // =========================================

    JPanel sidebar = new JPanel();
    sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
    sidebar.setBackground(AppColors.PRIMARY);
    sidebar.setPreferredSize(new Dimension(225, 0));
    sidebar.setBorder(BorderFactory.createEmptyBorder(30, 18, 25, 18));

    JLabel logo = new JLabel("NETBANK");
    logo.setFont(new Font("SansSerif", Font.BOLD, 27));
    logo.setForeground(Color.WHITE);
    logo.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel subtitle = new JLabel("ADMINISTRATION");
    subtitle.setFont(new Font("SansSerif", Font.BOLD, 10));
    subtitle.setForeground(new Color(147, 197, 253));
    subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

    sidebar.add(logo);
    sidebar.add(Box.createVerticalStrut(5));
    sidebar.add(subtitle);
    sidebar.add(Box.createVerticalStrut(55));

    JLabel menuLabel = new JLabel("MAIN MENU");
    menuLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
    menuLabel.setForeground(new Color(180, 195, 215));
    menuLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

    sidebar.add(menuLabel);
    sidebar.add(Box.createVerticalStrut(18));

    sidebar.add(createSidebarButton("Dashboard"));
    sidebar.add(Box.createVerticalStrut(8));

    sidebar.add(createSidebarButton("Manage Accounts"));
    sidebar.add(Box.createVerticalStrut(8));

    sidebar.add(createSidebarButton("Transactions"));
    sidebar.add(Box.createVerticalStrut(8));

    sidebar.add(createSidebarButton("Connected Clients"));

    sidebar.add(Box.createVerticalGlue());

    JLabel systemLabel = new JLabel("NetBank System v1.0");
    systemLabel.setFont(AppFonts.SMALL);
    systemLabel.setForeground(new Color(180, 195, 215));

    sidebar.add(systemLabel);

    // =========================================
    // MAIN WORKSPACE
    // =========================================

    JPanel workspace = new JPanel(new BorderLayout(0, 25));
    workspace.setBackground(AppColors.BACKGROUND);
    workspace.setBorder(
            BorderFactory.createEmptyBorder(28, 35, 30, 35));

    // =========================================
    // TOP HEADER
    // =========================================

    JPanel header = new JPanel(new BorderLayout());
    header.setBackground(AppColors.BACKGROUND);

    JPanel welcomePanel = new JPanel();
    welcomePanel.setLayout(new BoxLayout(
            welcomePanel, BoxLayout.Y_AXIS));
    welcomePanel.setBackground(AppColors.BACKGROUND);

    JLabel pageTitle = new JLabel("Dashboard");
    pageTitle.setFont(AppFonts.TITLE);
    pageTitle.setForeground(AppColors.PRIMARY);

    JLabel pageSubtitle = new JLabel(
            "Manage banking operations and monitor the system.");

    pageSubtitle.setFont(AppFonts.BODY);
    pageSubtitle.setForeground(AppColors.TEXT_SECONDARY);

    welcomePanel.add(pageTitle);
    welcomePanel.add(Box.createVerticalStrut(6));
    welcomePanel.add(pageSubtitle);

    JPanel profilePanel = new JPanel(
            new FlowLayout(FlowLayout.RIGHT, 12, 5));

    profilePanel.setBackground(AppColors.BACKGROUND);

    JLabel adminName = new JLabel("Administrator");
    adminName.setFont(AppFonts.SUBHEADING);
    adminName.setForeground(AppColors.PRIMARY);

    JButton logoutBtn = new StyledButton("Logout");
    styleAdminButton(logoutBtn, AppColors.DANGER);
    logoutBtn.addActionListener(e -> doLogout());

    profilePanel.add(adminName);
    profilePanel.add(logoutBtn);

    header.add(welcomePanel, BorderLayout.WEST);
    header.add(profilePanel, BorderLayout.EAST);

    // =========================================
    // DASHBOARD CONTENT
    // =========================================

    JPanel content = new JPanel(new BorderLayout(0, 18));
    content.setBackground(AppColors.BACKGROUND);

    JLabel sectionTitle = new JLabel("Banking Management");
    sectionTitle.setFont(AppFonts.HEADING);
    sectionTitle.setForeground(AppColors.PRIMARY);

    JPanel sectionHeader = new JPanel(new BorderLayout());
    sectionHeader.setBackground(AppColors.BACKGROUND);
    sectionHeader.add(sectionTitle, BorderLayout.WEST);

    // Four management cards

    JPanel cards = new JPanel(new GridLayout(2, 2, 18, 18));
    cards.setBackground(AppColors.BACKGROUND);

    JButton accountsBtn = new StyledButton("View Accounts");
    styleAdminButton(accountsBtn, AppColors.PRIMARY_LIGHT);
    accountsBtn.addActionListener(e ->
            sendAdminCommand("ADMIN_ACCOUNTS", "All Accounts"));

    JButton transactionsBtn = new StyledButton("View Transactions");
    styleAdminButton(transactionsBtn, AppColors.PRIMARY_LIGHT);
    transactionsBtn.addActionListener(e ->
            sendAdminCommand("ADMIN_TRANSACTIONS", "All Transactions"));

    JButton clientsBtn = new StyledButton("Monitor Clients");
    styleAdminButton(clientsBtn, AppColors.PRIMARY_LIGHT);
    clientsBtn.addActionListener(e ->
            sendAdminCommand("ADMIN_CLIENTS", "Connected Clients"));

    JButton statusBtn = new StyledButton("Change Status");
    styleAdminButton(statusBtn, AppColors.PRIMARY_LIGHT);
    statusBtn.addActionListener(e -> changeAccountStatus());

    cards.add(createAdminActionCard(
            "Account Management",
            "View registered customer accounts and their information.",
            accountsBtn));

    cards.add(createAdminActionCard(
            "Transaction Management",
            "Review banking transactions and transaction records.",
            transactionsBtn));

    cards.add(createAdminActionCard(
            "Client Monitoring",
            "Monitor clients currently connected to the server.",
            clientsBtn));

    cards.add(createAdminActionCard(
            "Account Status",
            "Activate or freeze an existing customer account.",
            statusBtn));

    content.add(sectionHeader, BorderLayout.NORTH);
    content.add(cards, BorderLayout.CENTER);

    workspace.add(header, BorderLayout.NORTH);
    workspace.add(content, BorderLayout.CENTER);

    dashboard.add(sidebar, BorderLayout.WEST);
    dashboard.add(workspace, BorderLayout.CENTER);

    return dashboard;
}


private JPanel createAdminActionCard(
        String title,
        String description,
        JButton actionButton) {

    JPanel card = new JPanel(new BorderLayout(0, 15));

    card.setBackground(Color.WHITE);

    card.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(AppColors.BORDER, 16),
            BorderFactory.createEmptyBorder(22, 22, 22, 22)
    ));

    JLabel titleLabel = new JLabel(title);
    titleLabel.setFont(AppFonts.SUBHEADING);
    titleLabel.setForeground(AppColors.PRIMARY);

    JTextArea descriptionLabel = new JTextArea(description);
    descriptionLabel.setFont(AppFonts.BODY);
    descriptionLabel.setForeground(AppColors.TEXT_SECONDARY);
    descriptionLabel.setBackground(Color.WHITE);
    descriptionLabel.setEditable(false);
    descriptionLabel.setLineWrap(true);
    descriptionLabel.setWrapStyleWord(true);
    descriptionLabel.setFocusable(false);

    JPanel textPanel = new JPanel();
    textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
    textPanel.setBackground(Color.WHITE);

    textPanel.add(titleLabel);
    textPanel.add(Box.createVerticalStrut(12));
    textPanel.add(descriptionLabel);

    actionButton.setPreferredSize(new Dimension(175, 40));
    actionButton.setMaximumSize(new Dimension(175, 40));

    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    buttonPanel.setBackground(Color.WHITE);
    buttonPanel.add(actionButton);

    card.add(textPanel, BorderLayout.CENTER);
    card.add(buttonPanel, BorderLayout.SOUTH);

    return card;
}


private JButton createSidebarButton(String text) {

    JButton button = new JButton(text);

    button.setFont(AppFonts.BODY);
    button.setForeground(Color.WHITE);
    button.setBackground(AppColors.PRIMARY);

    button.setHorizontalAlignment(SwingConstants.LEFT);

    button.setBorder(BorderFactory.createEmptyBorder(
            12, 10, 12, 10));

    button.setFocusPainted(false);
    button.setBorderPainted(false);
    button.setContentAreaFilled(false);

    button.setMaximumSize(
            new Dimension(Integer.MAX_VALUE, 45));

    button.setAlignmentX(Component.LEFT_ALIGNMENT);

    button.setCursor(
            new Cursor(Cursor.HAND_CURSOR));

    // Sidebar navigation actions

    button.addActionListener(e -> {

        switch (text) {

            case "Dashboard":
                JOptionPane.showMessageDialog(
                        this, "You are already on the dashboard.");
                break;

            case "Manage Accounts":
                sendAdminCommand(
                        "ADMIN_ACCOUNTS", "All Accounts");
                break;

            case "Transactions":
                sendAdminCommand(
                        "ADMIN_TRANSACTIONS", "All Transactions");
                break;

            case "Connected Clients":
                sendAdminCommand(
                        "ADMIN_CLIENTS", "Connected Clients");
                break;
        }
    });

    return button;
}


private void styleAdminButton(
        JButton button,
        Color backgroundColor) {

    button.setFont(AppFonts.BUTTON);

    button.setForeground(Color.WHITE);
    button.setBackground(backgroundColor);

    button.setFocusPainted(false);
    button.setBorderPainted(false);
    if (button instanceof StyledButton) {
        ((StyledButton) button).setButtonColor(backgroundColor);
    } else {
        button.setOpaque(true);
    }

    button.setCursor(
            new Cursor(Cursor.HAND_CURSOR));

    button.setPreferredSize(new Dimension(150, 40));
}


private void sendAdminCommand(String command, String title) {
    runCommand(command, response -> showResponse(response, title));
}


private void changeAccountStatus() {

    String account = JOptionPane.showInputDialog(
            this,
            "Enter the account number:",
            "Change Account Status",
            JOptionPane.QUESTION_MESSAGE);

    if (account == null || account.trim().isEmpty()) {
        return;
    }

    String[] options = {"ACTIVE", "FROZEN"};

    String status = (String) JOptionPane.showInputDialog(
            this,
            "Select the new account status:",
            "Account Status",
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);

    if (status == null) {
        return;
    }

    sendAdminCommand(
            "ADMIN_STATUS|" + account.trim() + "|" + status,
            "Account Status Update");
}


    // -------------------------------------------------
    // TCP SERVER CONNECTION
    // -------------------------------------------------

    private void connectToServerAsync() {

        if (socket != null && socket.isConnected()
                && !socket.isClosed()) {
            return;
        }

        final String host = serverIpField.getText().trim();
        if (host.isEmpty()) {
            showError("Please enter a server IP address.", "Connection failed");
            return;
        }

        connectBtn.setEnabled(false);
        statusLabel.setText("Connecting...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return connectToServerBlocking(host);
            }

            @Override
            protected void done() {
                try {
                    get();
                    statusLabel.setText("Connected to " + host + ":" + PORT);
                    statusLabel.setForeground(new Color(134, 239, 172));
                    connectBtn.setText("Connected");
                    serverIpField.setEnabled(false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    closeConnection();
                    showError("Connection was interrupted.", "Network error");
                } catch (ExecutionException e) {
                    closeConnection();
                    showError(messageFor(e), "Connection failed");
                }
                connectBtn.setEnabled(socket == null || socket.isClosed());
            }
        }.execute();
    }

    // -------------------------------------------------
    // SEND COMMAND TO SERVER
    // -------------------------------------------------

    private void runCommand(String command, Consumer<String> callback) {
        if (commandInProgress) {
            showError("Please wait for the current request to finish.", "Request in progress");
            return;
        }

        final String host = serverIpField.getText().trim();
        commandInProgress = true;
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                if (!isConnected()) {
                    if (host.isEmpty()) throw new IOException("Enter a server IP address.");
                    connectToServerBlocking(host);
                }
                appendLogOnEdt("Client: " + command);
                out.println(command);
                String response = in.readLine();
                if (response != null) {
                    appendLogOnEdt("Server says: " + response.replace("|", " | "));
                }
                return response;
            }

            @Override
            protected void done() {
                commandInProgress = false;
                try {
                    String response = get();
                    if (isConnected()) {
                        statusLabel.setText("Connected to " + host + ":" + PORT);
                        statusLabel.setForeground(new Color(134, 239, 172));
                        connectBtn.setText("Connected");
                        connectBtn.setEnabled(false);
                        serverIpField.setEnabled(false);
                    }
                    callback.accept(response);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("Request was interrupted.", "Network error");
                } catch (ExecutionException e) {
                    closeConnection();
                    showError(messageFor(e), "Communication error");
                }
            }
        }.execute();
    }

    // -------------------------------------------------
    // LOGIN
    // -------------------------------------------------

    private void doLogin() {

        String user = loginUserField.getText().trim();

        String pass = new String(loginPassField.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {

            JOptionPane.showMessageDialog(
                    this, "Please enter username and password.");

            return;
        }

        if (user.contains("|") || pass.contains("|")) {
            JOptionPane.showMessageDialog(
                    this, "Username and password cannot contain the | character.");
            return;
        }

        runCommand("LOGIN|" + user + "|" + pass, response -> {
            if (isSuccess(response, "LOGIN_SUCCESS|")) {
                String[] parts = response.split("\\|");
                if (parts.length < 2) {
                    showError("Invalid server login response.", "Login failed");
                    return;
                }
                currentAccount = parts[1];
                currentRole = parts.length > 2 ? parts[2] : "CUSTOMER";
                loginPassField.setText("");
                if ("ADMIN".equalsIgnoreCase(currentRole)) {
                    cardLayout.show(mainPanel, "ADMIN");
                } else {
                    accountHeaderLabel.setText("Account: " + currentAccount);
                    sendBalanceInquiry();
                    cardLayout.show(mainPanel, "CUSTOMER");
                }
            } else {
                showError(response, "Login failed");
            }
        });
    }

    // -------------------------------------------------
    // CUSTOMER REGISTRATION
    // -------------------------------------------------

    private void doRegister() {

        String user = regUserField.getText().trim();

        String pass = new String(regPassField.getPassword());

        String type = (String) regTypeBox.getSelectedItem();

        String balance = regBalanceField.getText().trim();

        if (user.isEmpty() || pass.isEmpty()
                || balance.isEmpty()) {

            JOptionPane.showMessageDialog(
                    this, "Please complete all registration fields.");

            return;
        }

        if (user.contains("|") || pass.contains("|")) {
            JOptionPane.showMessageDialog(
                    this, "Username and password cannot contain the | character.");
            return;
        }

        try {

            double amount = Double.parseDouble(balance);

            if (amount < 0) {
                JOptionPane.showMessageDialog(
                        this, "Initial deposit cannot be negative.");
                return;
            }
            if (Double.isNaN(amount) || Double.isInfinite(amount)) {
                JOptionPane.showMessageDialog(
                        this, "Initial deposit must be a finite amount.");
                return;
            }

        } catch (NumberFormatException e) {

            JOptionPane.showMessageDialog(
                    this, "Please enter a valid deposit amount.");

            return;
        }

        runCommand("REGISTER|" + user + "|" + pass + "|" + type + "|" + balance,
                response -> {
                    if (isSuccess(response, "REGISTER_SUCCESS|")) {
                        regUserField.setText("");
                        regPassField.setText("");
                        regBalanceField.setText("10000");
                        showInfo("Account created successfully.");
                    } else {
                        showError(response, "Registration failed");
                    }
                });
    }

    // -------------------------------------------------
    // BALANCE INQUIRY
    // -------------------------------------------------

    private void sendBalanceInquiry() {

        runCommand("BALANCE|" + currentAccount, response -> {
            if (isSuccess(response, "BALANCE_RESPONSE|")) {
                String[] parts = response.split("\\|");
                if (parts.length > 1) balanceLabel.setText("Balance: " + parts[1]);
            } else {
                showError(response, "Balance inquiry failed");
            }
        });
    }

    // -------------------------------------------------
    // LOGOUT
    // -------------------------------------------------

    private void doLogout() {

        if (socket != null && !socket.isClosed()) {
            runCommand("LOGOUT", response -> resetSession());
        } else {
            resetSession();
        }
    }

    private void resetSession() {
        currentAccount = "";
        currentRole = "NONE";

        loginUserField.setText("");
        loginPassField.setText("");

        cardLayout.show(mainPanel, "AUTH");
    }

    private String connectToServerBlocking(String host) throws IOException {
        InetAddress ipAddress = InetAddress.getByName(host);
        socket = new Socket(ipAddress, PORT);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String welcome = in.readLine();
        appendLogOnEdt("Server says: " + welcome);
        return welcome;
    }

    private boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed()
                && out != null && in != null;
    }

    private boolean isPositiveAmount(String value, String fieldName) {
        try {
            double amount = Double.parseDouble(value);
            if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
                showError(fieldName + " must be greater than zero.", "Invalid amount");
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            showError(fieldName + " must be a valid number.", "Invalid amount");
            return false;
        }
    }

    private boolean isSuccess(String response, String prefix) {
        return response != null && response.startsWith(prefix);
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "NetBank",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String response, String title) {
        String message = response == null ? "The server did not return a response."
                : response.replace("|", " | ");
        JOptionPane.showMessageDialog(this, message, title,
                JOptionPane.WARNING_MESSAGE);
    }

    private void showResponse(String response, String title) {
        if (response == null) {
            showError(null, title);
            return;
        }
        JTextArea resultArea = new JTextArea(response.replace("|", System.lineSeparator()),
                14, 55);
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        resultArea.setForeground(AppColors.TEXT_PRIMARY);
        resultArea.setBackground(Color.WHITE);
        resultArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        JScrollPane scrollPane = new JScrollPane(resultArea);
        scrollPane.setPreferredSize(new Dimension(700, 380));
        JOptionPane.showMessageDialog(this, scrollPane, title,
                response.startsWith("ERROR|")
                        ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    private String messageFor(ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null
                ? "Unable to communicate with the server." : cause.getMessage();
    }

    // -------------------------------------------------
    // COMMUNICATION LOG
    // -------------------------------------------------

    private void appendLog(String message) {

        logArea.append(message + "\n");

        logArea.setCaretPosition(
                logArea.getDocument().getLength());
    }

    private void appendLogOnEdt(String message) {
        SwingUtilities.invokeLater(() -> appendLog(message));
    }

    private static final class RoundedBorder implements Border {
        private final Color color;
        private final int radius;

        private RoundedBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(1, 1, 1, 1);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics,
                                 int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g.dispose();
        }
    }

    // -------------------------------------------------
    // CLOSE CONNECTION
    // -------------------------------------------------

    private void closeConnection() {

        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }

        if (out != null) {
            out.close();
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }

        socket = null;
        in = null;
        out = null;

        statusLabel.setText("Not connected");
        statusLabel.setForeground(new Color(253, 224, 71));

        connectBtn.setText("Connect to Server");
        connectBtn.setEnabled(true);

        serverIpField.setEnabled(true);
    }

    // -------------------------------------------------
    // MAIN METHOD
    // -------------------------------------------------

    public static void main(String[] args) {

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {
            // Use Swing's default look and feel when the platform theme is unavailable.
        }

        SwingUtilities.invokeLater(() -> {

            BankClientGUI gui = new BankClientGUI();

            gui.setVisible(true);
        });
    }
}
