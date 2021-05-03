import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;

public class MessageManager extends Thread {
    ServerSocket server;
    boolean log = false;
    String moduleManagerHost;
    int moduleManagerPort;
    String configFile = "modulesConfig.txt";
    final HashMap<String, LinkedList<Message>> messages = new HashMap<>();

    static class Message {
        static long counter = 0;
        long id = counter++;
        String message;

        Message(String message) {
            this.message = message;
        }
    }

    public static void main(String[] args) throws IOException, MissingConfigException {
        new MessageManager(args).start();
    }

    public MessageManager(String[] args) throws IOException, MissingConfigException {
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
        return Common.getPort(configFile, "messageManager");
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        // Accepte en boucle toutes les connexions et crée un thread pour s'occuper de chaque connexion
        while (true) {
            try {
                new MessageManager.WorkerThread(server.accept()).start();
            } catch (IOException | MissingConfigException ignored) {
                System.out.println("crash du MeM");
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
                if (split.length != 3) {
                    Network.send(Common.Constants.badMessage, dos, log);
                    continue;
                }
                switch (split[0]) {
                    case "post":    // Envoi d'un message
                        synchronized (messages) {
                            LinkedList<Message> list = messages.get(split[1]);
                            if (list == null) { // Si le compte n'a encore rien posté
                                Network.send("accountManager" + Common.Constants.separator + "get" + Common.Constants.separator + split[1], toMM, log);
                                if (Boolean.parseBoolean(Network.getMessage(fromMM, log))) {
                                    list = new LinkedList<>();
                                    messages.put(split[1], list);
                                }
                            }
                            if (list != null) {
                                list.add(new Message(split[2]));
                                Network.send("ok" + Common.Constants.posted, dos, log);
                            } else {
                                Network.send(Common.Constants.couldNotSendMessage, dos, log);
                            }
                        }
                        break;
                    case "get": // Récupération de messages
                        int minID = Integer.parseInt(split[2]);
                        // Récupération de la liste des abonnements du demandeur
                        Network.send("followManager" + Common.Constants.separator + "get" + Common.Constants.separator + split[1], toMM, log);
                        String[] followed = Network.getMessage(fromMM, log).substring(("ok" + Common.Constants.separator).length()).split(Common.Constants.separator);
                        LinkedList<Message> newMessages = new LinkedList<>();
                        // Récupération de tous les messages qui doivent l'être
                        synchronized (messages) {
                            for (String account : followed) {
                                LinkedList<Message> me = messages.get(account);
                                for (Message m : me) {
                                    if (m.id >= minID) {
                                        newMessages.add(m);
                                    }
                                }
                            }
                        }
                        newMessages.sort(Comparator.comparingLong(o -> o.id));
                        StringBuilder res = new StringBuilder("ok");
                        for (Message m : newMessages) {
                            res.append(Common.Constants.separator).append(m.id).append(Common.Constants.separator).append(m.message);
                        }
                        Network.send(res.toString(), dos, log);
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