
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.Thread.sleep;

class Helper {
    private byte[] A2S_INFO = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x54, 0x53, 0x6F, 0x75, 0x72, 0x63, 0x65, 0x20, 0x45, 0x6E, 0x67, 0x69,
            0x6E, 0x65, 0x20, 0x51, 0x75, 0x65, 0x72, 0x79, 0x00};
    private byte[] A2S_CHALLENGE = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x55, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};


    private DatagramSocket socket;
    private long lastUpdate = System.currentTimeMillis();
    private int numberOfPlayers = 0;
    private boolean isRunning = true;

    private String ipPort = "46.174.54.137:1337"; //46.174.48.44:27288 //46.174.48.48:27243
    private InetAddress serverIP;
    private int serverPort;

    private String name;
    private String map;
    private int maximumPlayers;
    private boolean passwordRequired;

    private String lastMap = "";

    private boolean isNotif = true;

    private int timeout = 45; //connection timeout in seconds

    Helper() {
        initialize();
        go();
    }

    private void go() {
        inputThread.start();
        p(getTime() + " App started\n");
        addTrayIcon();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                isRunning = false;
                SystemTray.getSystemTray().remove(trayIcon);
                System.out.println("Running Shutdown Hook");
                try {
                    socket.close();
                } catch (Exception e) {
                    System.out.println("hmmm");
                }
                System.out.println("STOPPING Shutdown Hook");
            }
        });

        Thread.currentThread().setName("MY MAIN THREAD");
        while(isRunning) {
            sendBaseInfoRequest();
            try {
                sleep(15*1000);
            } catch (Exception e) { e.printStackTrace();}
            new Thread(() -> {
                Thread.currentThread().setName("MY PRINT THREAD");
                printServerInfo();
            }).start();
        }


    }

    private void writeToLog(String s) {
        try {
            Files.write(Paths.get("log.txt"), s.getBytes(), StandardOpenOption.APPEND);
        }catch (FileNotFoundException ex) {

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Thread inputThread = new Thread( () -> {
        Thread.currentThread().setName("MY INPUT THREAD");

        byte[] buff = new byte[1400];
        while (isRunning) {
            try {
                DatagramPacket dp = new DatagramPacket(buff, buff.length, serverIP, serverPort);

                socket.receive(dp);
                new Thread(() -> {
                    Thread.currentThread().setName("MY HANDLE THREAD");
                    handleInputDatagram(buff);
                }).start();

            } catch (SocketTimeoutException te) {
                p("No response from server for "+ timeout + " seconds");
                sendNotification("Connection timed out!", TrayIcon.MessageType.ERROR);
            } catch (SocketException socketEx) {
                System.out.println("Disconnected");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    });

    private void handleInputDatagram(byte[] buff) {
        new Thread(() -> {
            try {
                parseData(buff);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void printServerInfo() {
        if (map != null && !map.equals(lastMap)) {
            StringBuilder sb = new StringBuilder(getTime());
            sb.append("\t");
            sb.append(name);
            sb.append("\t");
            sb.append(numberOfPlayers);
            sb.append("/");
            sb.append(maximumPlayers);
            sb.append("\t");
            sb.append(map);
            sb.append("\n");
            String info = sb.toString();
            p(info);
            writeToLog(info);

            lastMap = map;
            if (isNotif && trayIcon != null) {
                sendNotification("Map changed to "+map, TrayIcon.MessageType.INFO);
            }
        }
    }

    private void sendPlayersRequest() {
        DatagramPacket dp = new DatagramPacket(A2S_CHALLENGE, A2S_CHALLENGE.length, serverIP, serverPort);
        try {
            socket.send(dp);
        } catch (Exception e) {e.printStackTrace();}
    }

    private void sendBaseInfoRequest() {
        try {
            socket.send(new DatagramPacket(A2S_INFO, A2S_INFO.length, serverIP, serverPort));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initialize() {
        try {
            int index = ipPort.indexOf(':');
            serverIP = InetAddress.getByName(ipPort.substring(0, index));
            serverPort = Integer.parseInt(ipPort.substring(index + 1));
            socket = new DatagramSocket();
            socket.setSoTimeout(timeout*1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendNotification(String message, TrayIcon.MessageType type) {
        trayIcon.displayMessage("HappyMG monitor", message, type);
    }

    private void handleMessage(int code, ReplyStream stream) {
        switch (code) {
            case 'A':
                handleChallenge(stream);
                break;
            case 'D':
                handlePlayers(stream);
                break;
            case 'E':
                handleRules(stream);
                break;
            case 'I':
                handleInformation(stream);
                break;
            default:
                handleError(new Exception("Unexpected message: " + code));
        }
    }

    private void parseData(byte[] data) throws IOException {
        ReplyStream stream = new ReplyStream(data);
        if (stream.readInt() == -1) {
            handleMessage(stream.readUnsignedByte(), stream);
        }
    }

    private void handlePlayers(ReplyStream stream) {
        int playerCount = stream.readUnsignedByte();
        ArrayList<Player> players = new ArrayList<>(playerCount);
        for (int x = 0; x < playerCount; x++) {
            Player player = new Player(stream.readUnsignedByte());
            String name = stream.readString();
            if (name.length() > 0) {
                player.name = name;
            }
            player.kills = stream.readInt();
            player.secondsConnected = stream.readFloat();
            players.add(player);
        }
        Collections.sort(players, new Comparator<Player>(){
            public int compare(Player o1, Player o2){
                if(o1.secondsConnected == o2.secondsConnected)
                    return 0;
                return o1.secondsConnected > o2.secondsConnected ? -1 : 1;
            }
        });
    }

    private void handleChallenge(ReplyStream stream) {
        int challenge = stream.readInt();
        if (challenge > 0) {
            ByteBuffer buffer = ByteBuffer.wrap(new byte[9]);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(-1);
            byte data = 0x55;
            buffer.put(data);
            buffer.putInt(challenge);
            sendData(buffer.array());
        }
    }

    private void handleRules(ReplyStream stream) {
        int numberOfRules = stream.readShort();
        HashMap rules = new HashMap<String, String>(numberOfRules);
        for (int x = 0; x < numberOfRules; x++) {
            rules.put(stream.readString(), stream.readString());
        }
    }

    private void handleInformation(ReplyStream stream) {
        stream.readByte();	//Version
        name = stream.readString();
        map = stream.readString();
        String gameDirectory = stream.readString();
        String gameDescription = stream.readString();
        short applicationId = (short)stream.readShort();
        numberOfPlayers = stream.readUnsignedByte();
        maximumPlayers = stream.readUnsignedByte();
        int botCount = stream.readUnsignedByte();
        String type;
        switch (stream.readByte()) {
            case 'd': type = "Dedicated"; break;
            case 'l': type = "Listen"; break;
            case 'p': type = "TV";
        }
        String operatingSystem;
        switch (stream.readByte()) {
            case 'l': operatingSystem = "Linux"; break;
            case 'w': operatingSystem = "Windows";
        }
        passwordRequired = stream.readByte() == 1;
        boolean vacSecure = stream.readByte() == 1;
        String version = stream.readString();
        lastUpdate = System.currentTimeMillis();
    }

    private void handleError(Exception e) {
        e.printStackTrace();
    }


    private void sendData(byte[] data) {
        try {
            socket.send(new DatagramPacket(data, data.length, serverIP, serverPort));
        } catch (IOException e) {e.printStackTrace();}
    }


    private TrayIcon trayIcon;
    private void addTrayIcon() {
        //Check the SystemTray is supported
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray is not supported");
            return;
        }
        final PopupMenu popup = new PopupMenu();
        Image image = null;
        try {
            File pathToFile = new File("css_icon.png");
            image = ImageIO.read(pathToFile);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        trayIcon = new TrayIcon(image);
        final SystemTray tray = SystemTray.getSystemTray();

        // Create a pop-up menu components
        MenuItem aboutItem = new MenuItem("About");
        CheckboxMenuItem cb1 = new CheckboxMenuItem("Notifications");
        cb1.setState(isNotif);

        cb1.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                isNotif = cb1.getState();
            }
        });

        //Add components to pop-up menu
        popup.add(cb1);
        popup.addSeparator();
        popup.add(aboutItem);

        trayIcon.setPopupMenu(popup);

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.out.println("TrayIcon could not be added.");
        }
    }


    //SMALL HELPING METHODS
    private void p(String s) {
        System.out.print(s);
    }
    private void closeCSS() {
        try {
            Runtime rt = Runtime.getRuntime();
            Process p = rt.exec("C:\\css_closer.exe"); // css_closer.exe - C++ program for closing CSS (was lost somewhere)
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private String getTime() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(cal.getTime());
    }
    private static void clearScreen() {
        try {
            new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
        } catch (Exception e) {
        }
    }
    private static long getMinFromMs(int min) {
        return (long)(min * 60 * 1000);
    }

    protected static Image createImage(String path, String description) {
        URL imageURL = Helper.class.getResource(path);

        if (imageURL == null) {
            System.err.println("Resource not found: " + path);
            return null;
        } else {
            return (new ImageIcon(imageURL, description)).getImage();
        }
    }
}