import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;

public class ModuleManager extends Thread {
    ServerSocket server;
    boolean log = false;
    final HashMap<String, Pair<DataInputStream, DataOutputStream>> moduleConnections = new HashMap<>();
    String configFile = "./modulesConfig.txt";

    public static void main(String[] args) throws IOException, MissingConfigException {
        new ModuleManager(args).start();
    }

    @SuppressWarnings("deprecation")
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
        int port;
        DataInputStream cf = new DataInputStream(new FileInputStream(configFile));
        String line = cf.readLine();
        while (line != null && !line.startsWith("moduleManager")) {
            line = cf.readLine();
        }
        if (line == null) {
            throw new MissingConfigException();
        }
        String[] split = line.split(" ");
        port = Integer.parseInt(split[split.length - 1]);
        server = new ServerSocket(port);
        refreshServices();
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        while (true) {
            try {
                new WorkerThread(server.accept()).start();
            } catch (IOException ignored) {
                System.out.println("crash du MM");
            }
        }
    }

    private class WorkerThread extends Thread {
        Socket socket;
        DataInputStream dis;
        DataOutputStream dos;

        public WorkerThread(Socket s) throws IOException {
            socket = s;
            dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        @Override
        public void run() {
            int consecutiveFailures = 0;
            while (true) {
                String inMessage;
                try {
                    inMessage = Network.getMessage(dis, dos, log);
                } catch (Exception e) {
                    consecutiveFailures++;
                    Network.send(Common.Constants.badMessage, dos, log);
                    if (consecutiveFailures == 10) {
                        return;
                    }
                    continue;
                }
                consecutiveFailures = 0;
                int index = inMessage.indexOf(Common.Constants.separator);
                String destination = inMessage.substring(0, index);
                String message = inMessage.substring(index + Common.Constants.separator.length());
                String outMessage;
                Pair<DataInputStream, DataOutputStream> service;
                synchronized (moduleConnections) {
                    service = moduleConnections.get(destination);
                }
                if (service == null || !Network.send(message, service.value, log)) {
                    refreshServices();
                    if (service == null || !Network.send(message, service.value, log)) {
                        outMessage = "failure : unreachable";
                    } else {
                        outMessage = Network.getMessage(service.key, null, log);
                    }
                } else {
                    outMessage = Network.getMessage(service.key, null, log);
                }
                Network.send(outMessage, dos, log);
            }
        }
    }

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
                        String[] split = (String[]) Arrays.stream(line.split(" ")).filter(s -> !s.contentEquals("")).toArray();
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