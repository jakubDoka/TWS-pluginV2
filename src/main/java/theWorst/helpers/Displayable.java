package theWorst.helpers;

import theWorst.dataBase.PlayerD;

//interface for objects that ca be displayed in hud
public interface Displayable {
    String getMessage(PlayerD pd);
    void onTick();
}
