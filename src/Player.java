public class Player {

    public String name = "";
    public int kills;
    public float secondsConnected;
    private int index;


    Player(int index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public int getKills() {
        return kills;
    }

    public float getSecondsConnected() {
        return secondsConnected;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(80);
        sb.append(name);
        for (int i = 0; i < 35-this.name.length(); i++)
            sb.append(" ");
        sb.append(kills);
        for (int i = 0; i < 5-Integer.toString(kills).length(); i++)
            sb.append(" ");

        int sec = (int)secondsConnected%60;
        int min = (int)secondsConnected/60;

        sb.append((min/10)<1 ? "0"+min : min);
        sb.append(":");
        sb.append((sec/10)<1 ? "0"+sec : sec);
        return sb.toString();
    }
}