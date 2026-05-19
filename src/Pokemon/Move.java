package Pokemon;

public class Move {
    public final String name;
    public final String type;
    public final int basePower;
    public final int accuracy; // -1 means always hits
    public final boolean physical;
    // Earliest pokemon level at which this move qualifies for the type-based picker /
    // is offered by the Move Tutor.
    public final int minLevel;

    public Move(String name, String type, int basePower, int accuracy, boolean physical, int minLevel) {
        this.name = name;
        this.type = type;
        this.basePower = basePower;
        this.accuracy = accuracy;
        this.physical = physical;
        this.minLevel = minLevel;
    }
}
