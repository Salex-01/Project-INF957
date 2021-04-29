import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Network {
    static Integer getSize(DataInputStream dis, DataOutputStream dos, boolean log) {
        try {
            return dis.readInt();
        } catch (IOException e) {
            notifyError(Constants.couldNotReadSize, dos, log);
            return null;
        }
    }

    static String getMessage(int size, DataInputStream dis, DataOutputStream dos, boolean log) {
        byte[] tmp = new byte[size];
        for (int i = 0; i < size; ) {
            try {
                int l = dis.read(tmp, i, size - i);
                if (l < 0) {
                    throw new IOException();
                }
                i += l;
            } catch (IOException e) {
                notifyError(Constants.couldNotReadMessage, dos, log);
                return null;
            }
        }
        return new String(tmp);
    }

    static void send(String message, DataOutputStream dos, boolean log) {
        try {
            dos.writeInt(0);
            dos.writeInt(message.getBytes().length);
            dos.writeBytes(message);
            dos.flush();
        } catch (IOException e) {
            notifyError(Constants.couldNotSendMessage, dos, log);
        }
    }

    static void notifyError(String message, DataOutputStream dos, boolean log) {
        try {
            dos.writeInt(-1);
            dos.writeInt(message.getBytes().length);
            dos.writeBytes(message);
            dos.flush();
        } catch (IOException e) {
            if (log) {
                System.out.println(message);
            }
        }
    }
}