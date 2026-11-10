# NetBank — Multi-Client Network Banking System

**Module:** EC5205 – Operating Systems & Network Programming (Semester 5)  
**University of Ruhuna — Faculty of Engineering**

---

## Project Folder Separation (Server vs. Client)

The project is separated into two independent Eclipse/Java project folders so you can place `NetBank_Server` on **Laptop 1 (Server)** and `NetBank_Client` on **Laptop 2 (Client)**:

```text
NetBank_Network_Banking/
├── NetBank_Server/                 <-- Put on Server Laptop (Member 1 & Member 3)
│   ├── src/Server/
│   │   ├── BankServer.java         (Member 1: ServerSocket on port 6677)
│   │   ├── Server_Thread.java      (Member 1: Worker Thread per client socket)
│   │   ├── DatabaseManager.java    (Member 3: MySQL/SQLite JDBC + SHA-256 hashing)
│   │   └── BankingService.java     (Member 3: Synchronized Deposit, Withdraw, Atomic Transfer)
│   ├── lib/                        (JDBC drivers)
│   ├── sql/schema.sql              (MySQL database schema)
│   └── run-server.bat
│
└── NetBank_Client/                 <-- Put on Client Laptop (Member 2)
    ├── src/Client/
    │   ├── BankClient.java         (Member 2: Console TCP Client)
    │   └── BankClientGUI.java      (Member 2: Swing GUI TCP Client)
    ├── run-client-cli.bat
    └── run-client-gui.bat
```

---

## How to Run

### 1. On Laptop 1 (Server)
* **In Eclipse:** `File -> Open Projects from File System...` $\rightarrow$ select `NetBank_Server` $\rightarrow$ run `src/Server/BankServer.java`.
* **Or via Script:** Double-click `NetBank_Server\run-server.bat`.
* Note the **Server IP Address** printed in the console (e.g. `192.168.8.104`) and Port `6677`.

### 2. On Laptop 2 (Client)
* **In Eclipse:** `File -> Open Projects from File System...` $\rightarrow$ select `NetBank_Client` $\rightarrow$ run `src/Client/BankClient.java` (or `BankClientGUI.java`).
* **Or via Script:** Double-click `NetBank_Client\run-client-cli.bat` or `NetBank_Client\run-client-gui.bat`.
* Enter Laptop 1's IP Address when prompted.
* *(Default Administrator Login: `admin` / `admin123`)*
