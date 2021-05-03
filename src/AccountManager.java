import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

public class AccountManager extends Thread {
    ServerSocket server;
    boolean log = false;
    String moduleManagerHost;
    int moduleManagerPort;
    String configFile = "modulesConfig.txt";    // Fichier de configuration
    final LinkedList<String> accounts = new LinkedList<>(); // Liste des comptes existants

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
        server = new ServerSocket(getPort());   // Ouverture du socket du service
        getMMparams();  // Récupération des informations de connexion au module manager
    }

    // Définit les paramètres de connexion au module manager
    private void getMMparams() throws IOException, MissingConfigException {
        Pair<String, Integer> p = Common.getMMparams(configFile, server);
        moduleManagerHost = p.key;
        moduleManagerPort = p.value;
    }

    // Trouve le port sur lequel doit se lancer ce service
    private int getPort() throws IOException, MissingConfigException {
        return Common.getPort(configFile, "accountManager");
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        // Accepte en boucle toutes les connexions et crée un thread pour s'occuper de chaque connexion
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
        DataInputStream dis;    // InputStream de la connexion
        DataOutputStream dos;   // OutputStream de la connexion
        DataInputStream fromMM; // InputStream depuis le module manager
        DataOutputStream toMM;  // OutputStream vers le module manager

        public WorkerThread(Socket s) throws IOException, MissingConfigException {
            socket = s;
            dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            connectToMM();
        }

        @Override
        public void run() {
            while (true) {
                String message;
                try {
                    message = Network.getMessage(dis, log);   // Récupération du message reçu
                } catch (Exception e) {
                    return; // Si la connexion a été coupée
                }
                String[] split = Common.splitOnSeparator(message, Common.Constants.separator);
                if (split.length != 2) {
                    Network.send(Common.Constants.badMessage, dos, log);
                    continue;
                }
                switch (split[0]) {
                    case "new": // Création d'un compte
                        synchronized (accounts) {
                            if (accounts.contains(split[1])) {  // Si le compte existe déjà
                                Network.send(Common.Constants.couldNotCreateAccount, dos, log);
                            } else {    // Sinon
                                accounts.add(split[1]);
                                Network.send("ok" + Common.Constants.accountCreated, dos, log);
                            }
                        }
                        break;
                    case "delete":  // Suppression d'un compte
                        synchronized (accounts) {
                            if (accounts.remove(split[1])) {   // Si le compte existait
                                Network.send("ok" + Common.Constants.accountDeleted, dos, log);
                            } else {    // Sinon
                                Network.send(Common.Constants.couldNotDeleteAccount, dos, log);
                            }
                        }
                        break;
                    case "get": // Vérification de l'existence d'un compte
                        synchronized (accounts) {
                            Network.send("" + accounts.contains(split[1]), dos, log);
                        }
                        break;
                    default:
                        //TODO
                        break;
                }
            }
        }

        // Ouverture d'une connexion vers le module manager
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