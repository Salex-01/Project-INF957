import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class CentralServer extends Thread {
    ServerSocket server;
    boolean log = false;
    String moduleManagerHost;
    int moduleManagerPort;
    String configFile = "./modulesConfig.txt";

    public static void main(String[] args) throws IOException, MissingConfigException {
        new CentralServer(args).start();
    }

    @SuppressWarnings("deprecation")
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
        int port;
        DataInputStream cf = new DataInputStream(new FileInputStream(configFile));
        String line = cf.readLine();
        while (line != null && !line.startsWith("central")) {
            line = cf.readLine();
        }
        if (line == null) {
            throw new MissingConfigException();
        }
        String[] split = (String[]) Arrays.stream(line.trim().split(" ")).filter(s -> !s.contentEquals("")).toArray();
        port = Integer.parseInt(split[split.length - 1]);
        cf.close();
        cf = new DataInputStream(new FileInputStream(configFile));
        line = cf.readLine();
        while (line != null && !line.startsWith("moduleManager")) {
            line = cf.readLine();
        }
        if (line == null) {
            throw new MissingConfigException();
        }
        split = (String[]) Arrays.stream(line.trim().split(" ")).filter(s -> !s.contentEquals("")).toArray();
        moduleManagerHost = (split.length == 3 ? split[1] : server.getInetAddress().getHostAddress());
        moduleManagerPort = Integer.parseInt(split[split.length - 1]);
        server = new ServerSocket(port);
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        while (true) {
            try {
                new WorkerThread(server.accept()).start();
            } catch (IOException ignored) {
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

        public WorkerThread(Socket s) throws IOException {
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
                String[] message = inMessage.split(Constants.separator);
                String outMessage;
                switch (message[0]) {
                    case "new account":
                        outMessage = accountManagerExecute("new ", message,
                                Constants.couldNotCreateAccount, Constants.accountCreated);
                        break;
                    case "delete account":
                        outMessage = accountManagerExecute("delete ", message,
                                Constants.couldNotDeleteAccount, Constants.accountDeleted);
                        break;
                    case "follow":
                        outMessage = followManagerExecute("add ", message,
                                Constants.cantFollow + message[2], Constants.followed + message[2]);
                        break;
                    case "unfollow":
                        outMessage = followManagerExecute("remove ", message,
                                Constants.cantUnfollow + message[2], Constants.unfollowed + message[2]);
                        break;
                    case "post":
                        outMessage = messageManagerExecute("post ", message,
                                Constants.cantPostMessage, Constants.posted);
                        break;
                    case "get":
                        outMessage = messageManagerExecute("get ", message,
                                Constants.cantGetMessages, null);
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
            if (!Network.send("accountManager" + Constants.separator + s + paramas[1], toMM, log)) {
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
            if (!Network.send("followManager" + Constants.separator + s + params[1] + Constants.separator + params[2], toMM, log)) {
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
            if (!Network.send("messageManager" + Constants.separator + s + params[1] + Constants.separator + params[2], toMM, log)) {
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
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void connectToMM() throws IOException {
            if (fromMM != null) {
                fromMM.close();
            }
            if (toMM != null) {
                toMM.close();
            }
            Socket moduleManagerSocket = null;
            boolean tem;
            do {
                tem = false;
                try {
                    moduleManagerSocket = new Socket(moduleManagerHost, moduleManagerPort);
                } catch (Exception e) {
                    System.out.println("Module manager unreachable at " + moduleManagerHost + ":" + moduleManagerPort);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                    }
                    tem = true;
                }
            } while (tem);
            fromMM = new DataInputStream(new BufferedInputStream(moduleManagerSocket.getInputStream()));
            toMM = new DataOutputStream(new BufferedOutputStream(moduleManagerSocket.getOutputStream()));
        }
    }
}