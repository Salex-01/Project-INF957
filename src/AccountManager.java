import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

public class AccountManager extends Thread {
    ServerSocket server;
    boolean log = false;
    String moduleManagerHost;
    int moduleManagerPort;
    String configFile = "modulesConfig.txt";
    final LinkedList<String> accounts = new LinkedList<>();

    public static void main(String[] args) throws IOException, MissingConfigException {
        new AccountManager(args).start();
    }

    public AccountManager(String[] args) throws IOException, MissingConfigException {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "config":
                    configFile = args[i + 1];
                    i++;
                    break;
                case "log":
                    log = true;
                    break;
                default:
                    System.out.println("Argument inconnu : " + args[i]);
                    System.exit(-1);
            }
        }
        server = new ServerSocket(getPort());
        getMMparams();
    }

    private void getMMparams() throws IOException, MissingConfigException {
        Pair<String, Integer> p = Common.getMMparams(configFile, server);
        moduleManagerHost = p.key;
        moduleManagerPort = p.value;
    }

    private int getPort() throws IOException, MissingConfigException {
        return Common.getPort(configFile, "accountManager");
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        while (true) {
            try {
                new WorkerThread(server.accept()).start();
            } catch (IOException | MissingConfigException ignored) {
                System.out.println("crash du AM");
            }
        }
    }

    private class WorkerThread extends Thread {
        Socket socket;
        DataInputStream dis;
        DataOutputStream dos;
        DataInputStream fromMM;
        DataOutputStream toMM;

        public WorkerThread(Socket s) throws IOException, MissingConfigException {
            socket = s;
            dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            connectToMM();
        }

        @Override
        @SuppressWarnings("InfiniteLoopStatement")
        public void run() {
            while (true) {
                String message = Network.getMessage(dis, null, log);
                String[] split = Common.splitOnSeparator(message, Common.Constants.separator);
                if (split.length != 2) {
                    Network.send(Common.Constants.badMessage, dos, log);
                    continue;
                }
                switch (split[0]) {
                    case "new":
                        synchronized (accounts) {
                            if (accounts.contains(split[1])) {
                                Network.send(Common.Constants.couldNotCreateAccount, dos, log);
                            } else {
                                accounts.add(split[1]);
                                Network.send("ok" + Common.Constants.accountCreated, dos, log);
                            }
                        }
                        break;
                    case "delete":
                        synchronized (accounts) {
                            if (!accounts.remove(split[1])) {
                                Network.send(Common.Constants.couldNotDeleteAccount, dos, log);
                            } else {
                                Network.send("ok" + Common.Constants.accountDeleted, dos, log);
                            }
                        }
                        break;
                    case "get":
                        synchronized (accounts) {
                            Network.send("ok" + accounts.contains(split[1]), dos, log);
                        }
                        break;
                    default:
                        //TODO
                        break;
                }
            }
        }

        private void connectToMM() throws IOException, MissingConfigException {
            if (fromMM != null || toMM != null) {
                getMMparams();
            }
            Pair<DataInputStream, DataOutputStream> p = Common.connectToMM(fromMM, toMM, moduleManagerHost, moduleManagerPort);
            fromMM = p.key;
            toMM = p.value;
        }
    }
}