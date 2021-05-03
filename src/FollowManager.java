import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedList;

public class FollowManager extends Thread {
    ServerSocket server;
    boolean log = false;
    String moduleManagerHost;
    int moduleManagerPort;
    String configFile = "modulesConfig.txt";    // Fichier de configuration
    final HashMap<String, LinkedList<String>> follows = new HashMap<>();    // Liste des listes d'abonnements

    public static void main(String[] args) throws IOException, MissingConfigException {
        new FollowManager(args).start();
    }

    public FollowManager(String[] args) throws IOException, MissingConfigException {
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
        return Common.getPort(configFile, "followManager");
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        // Accepte en boucle toutes les connexions et crée un thread pour s'occuper de chaque connexion
        while (true) {
            try {
                new FollowManager.WorkerThread(server.accept()).start();
            } catch (IOException | MissingConfigException ignored) {
                System.out.println("crash du FM");
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
                if (split.length < 2 || split.length > 3) {
                    Network.send(Common.Constants.badMessage, dos, log);
                    continue;
                }
                switch (split[0]) {
                    case "add": // Ajout d'un abonnement
                        boolean ok = false;
                        synchronized (follows) {
                            // Vérification de l'existence du compte du demandeur
                            boolean doFollow = follows.containsKey(split[1]);
                            if (!doFollow) {
                                Network.send("accountManager" + Common.Constants.separator + "get" + Common.Constants.separator + split[1], toMM, log);
                                doFollow = Boolean.parseBoolean(Network.getMessage(fromMM, log));
                            }
                            if (doFollow) {
                                // Vérification de l'existence du compte du demandé
                                doFollow = follows.containsKey(split[2]);
                                if (!doFollow) {
                                    Network.send("accountManager" + Common.Constants.separator + "get" + Common.Constants.separator + split[2], toMM, log);
                                    doFollow = Boolean.parseBoolean(Network.getMessage(fromMM, log));
                                }
                                if (doFollow) {
                                    // Ajout de l'abonnement
                                    LinkedList<String> list = follows.computeIfAbsent(split[1], k -> new LinkedList<>());
                                    if (!list.contains(split[2])) {
                                        list.add(split[2]);
                                        ok = true;
                                    }
                                }
                            }
                        }
                        if (ok) {   // Si l'abonnement a été fait
                            Network.send("ok" + Common.Constants.followed + split[2], dos, log);
                        } else {    // Sinon
                            Network.send(Common.Constants.cantFollow + split[2], dos, log);
                        }
                        break;
                    case "remove":  // Désabonnement
                        synchronized (follows) {
                            LinkedList<String> list = follows.get(split[1]);
                            if (list != null && list.remove(split[2])) {
                                Network.send("ok" + Common.Constants.unfollowed + split[2], dos, log);
                            } else {
                                Network.send(Common.Constants.cantUnfollow + split[2], dos, log);
                            }
                        }
                        break;
                    case "get": // Récupération de la liste des abonnements d'un compte
                        synchronized (follows) {
                            LinkedList<String> list = follows.get(split[1]);
                            StringBuilder res = new StringBuilder("ok");
                            if (list != null) {
                                for (String s : list) {
                                    res.append(Common.Constants.separator).append(s);
                                }
                            }
                            Network.send(res.toString(), dos, log);
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