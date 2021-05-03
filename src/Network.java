import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Network {
    // Retourne la taille du premier message disponible dans dis
    static Integer getSize(DataInputStream dis, DataOutputStream dos, boolean log) {
        try {
            return dis.readInt();
        } catch (IOException e) {
            notifyError(Common.Constants.couldNotReadSize, dos, log);
            return null;
        }
    }

    // Retourne le message composé des size premiers octets disponibles dans dis
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
                notifyError(Common.Constants.messageWrongSize, dos, log);
                return null;
            }
        }
        return new String(tmp);
    }

    // Retourne le premier message disponible dans dis
    @SuppressWarnings("ConstantConditions")
    static String getMessage(DataInputStream dis, boolean log) {
        return getMessage(getSize(dis, null, log), dis, null, log);
    }

    // Envoie message dans dos
    static boolean send(String message, DataOutputStream dos, boolean log) {
        try {
            dos.writeInt(message.getBytes().length);
            dos.writeBytes(message);
            dos.flush();
            return true;
        } catch (IOException e) {
            notifyError(Common.Constants.couldNotSendMessage, dos, log);
            return false;
        }
    }

    static void notifyError(String message, DataOutputStream dos, boolean log) {
        try {
            if (dos != null) {
                dos.writeInt(message.getBytes().length);
                dos.writeBytes(message);
                dos.flush();
            }
        } catch (IOException ignored) {
        } finally {
            if (log) {
                System.out.println(message);
            }
        }
    }
}