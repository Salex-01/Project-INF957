import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class CentralServer extends Thread {
    ServerSocket server;
    boolean log = false;
    String moduleManagerHost;
    int moduleManagerPort;
    String configFile = "modulesConfig.txt";

    public static void main(String[] args) throws IOException, MissingConfigException {
        new CentralServer(args).start();
    }

    public CentralServer(String[] args) throws IOException, MissingConfigException {
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
        server = new ServerSocket(getPort());   // Ouverture du socket du serveur
        getMMparams();  // Récupération des informations de connexion au module manager
    }

    private void getMMparams() throws IOException, MissingConfigException {
        Pair<String, Integer> p = Common.getMMparams(configFile, server);
        moduleManagerHost = p.key;
        moduleManagerPort = p.value;
    }

    // Récupère le port sur lequel le serveur doit s'ouvrir
    private int getPort() throws IOException, MissingConfigException {
        return Common.getPort(configFile, "central");
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        // Accepte en boucle toutes les connexions et crée un thread pour s'occuper de chaque connexion
        while (true) {
            try {
                new WorkerThread(server.accept()).start();
            } catch (IOException | MissingConfigException e) {
                System.out.println("crash du serveur");
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
            connectToMM();  // Connexion au module manager
        }

        /*
         * commande bien transmise : renvoie la réponse si elle existe
         * erreur de communication avec le MM : reconnexion au MM mais abandon de la requête (réponse erreur)
         * erreur de communication du MM avec le service : abandon de la requête (réponse erreur)
         */
        @Override
        public void run() {
            while (true) {
                Integer size = Network.getSize(dis, dos, log);
                if (size == null) {
                    return; // La connexion a été coupée
                }
                String inMessage = Network.getMessage(size, dis, dos, log);
                if (inMessage == null) {
                    continue;   // Si le message est mal formaté
                }
                String[] message = Common.splitOnSeparator(inMessage, Common.Constants.separator);
                String outMessage;
                switch (message[0]) {
                    case "new account": // Création d'un compte
                        outMessage = accountManagerExecute("new", message,
                                Common.Constants.couldNotCreateAccount, Common.Constants.accountCreated);
                        break;
                    case "delete account":  // Suppression d'un compte
                        outMessage = accountManagerExecute("delete", message,
                                Common.Constants.couldNotDeleteAccount, Common.Constants.accountDeleted);
                        break;
                    case "follow":  // Abonnement
                        outMessage = followManagerExecute("add", message,
                                Common.Constants.cantFollow + message[2], Common.Constants.followed + message[2]);
                        break;
                    case "unfollow":    // Désabonnement
                        outMessage = followManagerExecute("remove", message,
                                Common.Constants.cantUnfollow + message[2], Common.Constants.unfollowed + message[2]);
                        break;
                    case "post":    // Envoi d'un message
                        outMessage = messageManagerExecute("post", message,
                                Common.Constants.cantPostMessage, Common.Constants.posted);
                        break;
                    case "get": // Récupération de messages
                        outMessage = messageManagerExecute("get", message,
                                Common.Constants.cantGetMessages, null);
                        break;
                    case "close":   // Déconnexion
                        return;
                    default:
                        System.out.println("Unknown command : " + message[0]);
                        continue;
                }
                if (outMessage != null) {
                    Network.send(outMessage, dos, log);
                }
            }
        }

        // Envoi d'une commande à l'account manager
        private String accountManagerExecute(String s, String[] paramas, String failureMessage, String successMessage) {
            if (!Network.send("accountManager" + Common.Constants.separator + s + Common.Constants.separator + paramas[1], toMM, log)) {
                reconnectIfNull(null);
                return failureMessage;
            }
            String response = Network.getMessage(fromMM, log);
            if (response != null && response.startsWith("ok")) {
                return successMessage;
            } else {
                reconnectIfNull(response);
                return failureMessage;
            }
        }

        // Envoi d'une commande au follow manager
        private String followManagerExecute(String s, String[] params, String failureMessage, String successMessage) {
            if (!Network.send("followManager" + Common.Constants.separator + s + Common.Constants.separator + params[1] + Common.Constants.separator + params[2], toMM, log)) {
                reconnectIfNull(null);
                return failureMessage;
            }
            String response = Network.getMessage(fromMM, log);
            if (response != null && response.startsWith("ok")) {
                return successMessage;
            } else {
                reconnectIfNull(response);
                return failureMessage;
            }
        }

        // Envoi d'une commande au message manager
        private String messageManagerExecute(String s, String[] params, String failureMessage, String successMessage) {
            if (!Network.send("messageManager" + Common.Constants.separator + s + Common.Constants.separator + params[1] + Common.Constants.separator + params[2], toMM, log)) {
                reconnectIfNull(null);
                return failureMessage;
            }
            String response = Network.getMessage(fromMM, log);
            if (response != null && response.startsWith("ok")) {
                return (successMessage != null ? successMessage : response.substring(("ok" + Common.Constants.separator).length()));
            } else {
                reconnectIfNull(response);
                return failureMessage;
            }
        }

        // Rétablissement de la connexion avec le module manager si crash
        private void reconnectIfNull(String check) {
            if (check == null) {
                try {
                    connectToMM();
                } catch (IOException | MissingConfigException e) {
                    e.printStackTrace();
                }
            }
        }

        // Établissement d'une connexion vers le module manager
        private void connectToMM() throws IOException, MissingConfigException {
            if (fromMM != null || toMM != null) {
                getMMparams();
            }
            Pair<DataInputStream, DataOutputStream> p = null;
            boolean tem;
            do {
                tem = false;
                try {
                    p = Common.connectToMM(fromMM, toMM, moduleManagerHost, moduleManagerPort);
                } catch (IOException e) {
                    tem = true;
                    System.out.println("MM unreachable");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                    }
                }
            } while (tem);
            fromMM = p.key;
            toMM = p.value;
        }
    }
}