package theWorst.votes;

import mindustry.entities.type.Player;

public abstract class VoteData {
    public Player by;
    public String reason;
    public Object target;

    public abstract void run();
}
