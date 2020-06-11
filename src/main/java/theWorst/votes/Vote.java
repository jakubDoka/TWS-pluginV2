package theWorst.votes;

import arc.math.Mathf;
import arc.util.Timer;
import mindustry.entities.type.Player;
import mindustry.gen.Call;
import theWorst.Tools;
import theWorst.database.Database;
import theWorst.database.Perm;
import theWorst.database.PlayerD;
import theWorst.database.Rank;
import theWorst.helpers.Destroyable;
import theWorst.helpers.Displayable;
import theWorst.helpers.Hud;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static mindustry.Vars.playerGroup;
import static theWorst.Tools.sendErrMessage;

public class Vote implements Displayable, Destroyable {
    VoteData voteData;
    String message;
    String[] args;
    final String mode;

    Set<String> voted = new HashSet<>();
    HashMap<String,Integer> recent = new HashMap<>();


    public boolean voting = false;

    Integer maxReq = null;

    final int voteDuration = 60;
    int no, yes;
    int time;

    public Vote(String mode){
        this.mode = mode;
        Hud.addDisplayable(this);
    }

    public void aVote(VoteData voteData, Integer maxReq, String ... args) {
        Player player=voteData.by;
        Database.getData(player).onAction(player);
        if (voting) {
            sendErrMessage(player,"vote-in-process");
            return;
        }
        if (Tools.getRank(Database.getData(player)).equals(Rank.griefer)){
            sendErrMessage(player, "griefer-no-perm");
            return;
        }
        if(recent.containsKey(player.uuid)){
            int time=recent.get(player.uuid);
            sendErrMessage(player,"vote-is-recent",Tools.secToTime(time));
            return;
        }
        this.voteData = voteData;
        this.message = voteData.reason;
        this.maxReq = maxReq;
        this.args = args;
        voted.clear();
        yes=0;
        no=0;
        time=voteDuration;
        voting = true;
        addVote(player,"y");
        Call.sendMessage(voteData.reason);
    }

    public void addToRecent(Player player){
        if(Database.hasPerm(player, Perm.high)) return;
        sendErrMessage(player,"vote-your-failed");
        recent.put(player.uuid, voteDuration);
        Timer.schedule(new Timer.Task(){
            @Override
            public void run() {
                int time=recent.get(player.uuid);
                if(time==0){
                    recent.remove(player.uuid);
                    this.cancel();
                    return;
                }
                recent.put(player.uuid, time-1);
            }
        },0,1);
    }

    public int getRequired() {
        int count = 0;
        for(Player p:playerGroup){
            PlayerD pd = Database.getData(p);
            if(pd.rank.equals(Rank.griefer.name()) || pd.afk) continue;
            count+=1;
        }
        if (count == 2) {
            return 2;
        }
        int res = (int) Math.ceil(count / 2.0);
        if(maxReq != null) res =Mathf.clamp(res,1,maxReq);
        return  res;
    }

    public void addVote(Player player, String vote) {
        PlayerD pd=Database.getData(player);
        pd.onAction(player);
        if (voted.contains(player.uuid)) {
            sendErrMessage( player, "vote-already-voted");
            return;
        }
        if(pd.rank.equals(Rank.griefer.name())){
            sendErrMessage(player,"griefer-no-perm");
            return;
        }
        voted.add(player.uuid);
        int req=getRequired();
        if (vote.equals("y")) {
            yes += 1;
            if (yes >= req) {
                close(true);
            }
        } else {
            no += 1;
            if (no >= req) {
                close(false);
            }
        }
        Call.sendMessage(player.name+" "+vote);
    }

    public void close(boolean success) {
        if (!voting) {
            return;
        }
        voting = false;
        String result ="vote-" + message;
        if (success) {
            voteData.run();
            //todo
        } else {
            addToRecent(voteData.by);
            //todo
        }
    }

    @Override
    public String getMessage(PlayerD pd) {
        if(!voting) return null;
        time--;

        if(time == 0){
            close(false);
        }
        String color = time < 15 && time % 2 == 0 ? "gray" : "white";
        String md = Tools.getTranslation(pd,mode);
        String fMessage = Tools.format(Tools.getTranslation(pd,message),args);
        String req = Tools.getTranslation(pd,"vote-req");
        return String.format("[%s]%s %s %02d [green]%d[] : [scarlet]%d[] [gray]%s %d[]",
                color,fMessage,md,time,yes,no,req,getRequired());
    }

    @Override
    public void destroy() {
        close(false);
    }
}
