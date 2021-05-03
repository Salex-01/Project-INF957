import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class ModuleManager extends Thread {
    ServerSocket server;
    boolean log = false;
    // Connexions vers les modules
    final HashMap<String, Pair<DataInputStream, DataOutputStream>> moduleConnections = new HashMap<>();
    String configFile = "modulesConfig.txt";    // Fichier de configuration

    public static void main(String[] args) throws IOException, MissingConfigException {
        new ModuleManager(args).start();
    }

    public ModuleManager(String[] args) throws IOException, MissingConfigException {
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
        server = new ServerSocket(Common.getPort(configFile,"moduleManager"));  // Ouverture du socket du service
        refreshServices();  // Connexion aux services
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        // Accepte en boucle toutes les connexions et crée un thread pour s'occuper de chaque connexion
        while (true) {
            try {
                new WorkerThread(server.accept()).start();
            } catch (IOException ignored) {
                System.out.println("crash du MoM");
            }
        }
    }

    private class WorkerThread extends Thread {
        Socket socket;
        DataInputStream dis;    // InputStream de la connexion
        DataOutputStream dos;   // OutputStream de la connexion

        public WorkerThread(Socket s) throws IOException {
            socket = s;
            dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        @Override
        public void run() {
            while (true) {
                String inMessage;
                try {
                    inMessage = Network.getMessage(dis, log);  // Récupération du message
                } catch (Exception e) {
                    return; // Connexion coupée
                }
                int index = inMessage.indexOf(Common.Constants.separator);
                String destination = inMessage.substring(0, index);
                String message = inMessage.substring(index + Common.Constants.separator.length());
                String outMessage;
                Pair<DataInputStream, DataOutputStream> service;
                synchronized (moduleConnections) {
                    // Récupération du service auquel le message est destiné
                    service = moduleConnections.get(destination);
                }
                // Envoi du message
                if (service == null || !Network.send(message, service.value, log)) {
                    refreshServices();
                    if (service == null || !Network.send(message, service.value, log)) {
                        outMessage = "failure : unreachable";
                    } else {
                        outMessage = Network.getMessage(service.key, log);
                    }
                } else {
                    outMessage = Network.getMessage(service.key, log);
                }
                // Envoi de la réponse
                Network.send(outMessage, dos, log);
            }
        }
    }

    // Connexion aux services
    @SuppressWarnings("deprecation")
    private void refreshServices() {
        boolean tem;
        do {
            tem = false;
            try {
                DataInputStream is = new DataInputStream(new FileInputStream(configFile));
                synchronized (moduleConnections) {
                    for (Pair<DataInputStream, DataOutputStream> p : moduleConnections.values()) {
                        p.key.close();
                        p.value.close();
                    }
                    moduleConnections.clear();
                    String line = is.readLine();
                    while (line != null) {
                        if (line.startsWith("//") || line.startsWith("moduleManager")) {
                            line = is.readLine();
                            continue;
                        }
                        String[] split = Common.splitOnSeparator(line, " ");
                        System.out.println("Connecting to " + split[0]);
                        String serviceAddress = (split.length == 3 ? split[1] : server.getInetAddress().getHostAddress());
                        int servicePort = Integer.parseInt(split[split.length - 1]);
                        Socket s = new Socket(serviceAddress, servicePort);
                        moduleConnections.put(split[0], new Pair<>(new DataInputStream(s.getInputStream()), new DataOutputStream(s.getOutputStream())));
                        System.out.println("Connected");
                        line = is.readLine();
                    }
                }
            } catch (IOException e) {
                tem = true;
            }
        } while (tem);
    }
}