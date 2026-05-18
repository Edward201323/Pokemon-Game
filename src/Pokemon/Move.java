package Pokemon;

public class Move {
    public final String name;
    public final String type;
    public final int basePower;
    public final int accuracy; // -1 means always hits
    public final boolean physical;

    public Move(String name, String type, int basePower, int accuracy, boolean physical) {
        this.name = name;
        this.type = type;
        this.basePower = basePower;
        this.accuracy = accuracy;
        this.physical = physical;
    }
}
