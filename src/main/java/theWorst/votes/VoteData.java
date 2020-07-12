package theWorst.votes;

import mindustry.entities.type.Player;
import theWorst.database.Perm;

public abstract class VoteData {
    public Player by;
    public String reason;
    public Object target;
    public Perm special;

    public abstract void run();
}
