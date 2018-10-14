import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

public class ReplyStream {
    ByteBuffer buffer;

    public ReplyStream(byte[] data) {
        this(ByteBuffer.wrap(data));
    }

    public ReplyStream(ByteBuffer buffer) {
        this.buffer = buffer;
        buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public int getPosition() {
        return this.buffer.position();
    }

    public int readByte() {
        return this.buffer.get();
    }

    public int readUnsignedByte() {
        int data = this.readByte();
        if (data < 0) {
            data += 256;
        }

        return data;
    }

    public float readFloat() {
        return this.buffer.getFloat();
    }

    public int readInt() {
        return this.buffer.getInt();
    }

    public int readShort() {
        return this.buffer.getShort();
    }

    public String readString() {
        byte[] buff = new byte[1400];

        int x;
        for(x = 0; x < buff.length && this.buffer.hasRemaining(); ++x) {
            buff[x] = this.buffer.get();
            if (buff[x] == 0) {
                break;
            }
        }

        return new String(buff, 0, x, Charset.forName("UTF-8"));
    }

    public String toString() {
        return this.buffer.toString();
    }
}
