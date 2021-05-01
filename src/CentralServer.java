import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

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
        server = new ServerSocket(getPort());
        getMMparams();
    }

    private void getMMparams() throws IOException, MissingConfigException {
        Pair<String, Integer> p = Common.getMMparams(configFile, server);
        moduleManagerHost = p.key;
        moduleManagerPort = p.value;
    }

    private int getPort() throws IOException, MissingConfigException {
        return Common.getPort(configFile, "central");
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
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
                    return;
                }
                String inMessage = Network.getMessage(size, dis, dos, log);
                if (inMessage == null) {
                    continue;
                }
                String[] message = Common.splitOnSeparator(inMessage, Common.Constants.separator);
                String outMessage;
                switch (message[0]) {
                    case "new account":
                        outMessage = accountManagerExecute("new", message,
                                Common.Constants.couldNotCreateAccount, Common.Constants.accountCreated);
                        break;
                    case "delete account":
                        outMessage = accountManagerExecute("delete", message,
                                Common.Constants.couldNotDeleteAccount, Common.Constants.accountDeleted);
                        break;
                    case "follow":
                        outMessage = followManagerExecute("add", message,
                                Common.Constants.cantFollow + message[2], Common.Constants.followed + message[2]);
                        break;
                    case "unfollow":
                        outMessage = followManagerExecute("remove", message,
                                Common.Constants.cantUnfollow + message[2], Common.Constants.unfollowed + message[2]);
                        break;
                    case "post":
                        outMessage = messageManagerExecute("post", message,
                                Common.Constants.cantPostMessage, Common.Constants.posted);
                        break;
                    case "get":
                        outMessage = messageManagerExecute("get", message,
                                Common.Constants.cantGetMessages, null);
                        break;
                    case "close":
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

        private String accountManagerExecute(String s, String[] paramas, String failureMessage, String successMessage) {
            if (!Network.send("accountManager" + Common.Constants.separator + s + Common.Constants.separator + paramas[1], toMM, log)) {
                reconnectIfNull(null);
                return failureMessage;
            }
            String response = Network.getMessage(fromMM, null, log);
            if (response != null && response.startsWith("ok")) {
                return successMessage;
            } else {
                reconnectIfNull(response);
                return failureMessage;
            }
        }

        private String followManagerExecute(String s, String[] params, String failureMessage, String successMessage) {
            if (!Network.send("followManager" + Common.Constants.separator + s + Common.Constants.separator + params[1] + Common.Constants.separator + params[2], toMM, log)) {
                reconnectIfNull(null);
                return failureMessage;
            }
            String response = Network.getMessage(fromMM, null, log);
            if (response != null && response.startsWith("ok")) {
                return successMessage;
            } else {
                reconnectIfNull(response);
                return failureMessage;
            }
        }

        private String messageManagerExecute(String s, String[] params, String failureMessage, String successMessage) {
            if (!Network.send("messageManager" + Common.Constants.separator + s + Common.Constants.separator + params[1] + Common.Constants.separator + params[2], toMM, log)) {
                reconnectIfNull(null);
                return failureMessage;
            }
            String response = Network.getMessage(fromMM, null, log);
            if (response != null && response.startsWith("ok")) {
                return (successMessage != null ? successMessage : response.substring(2));
            } else {
                reconnectIfNull(response);
                return failureMessage;
            }
        }

        private void reconnectIfNull(String check) {
            if (check == null) {
                try {
                    connectToMM();
                } catch (IOException | MissingConfigException e) {
                    e.printStackTrace();
                }
            }
        }

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