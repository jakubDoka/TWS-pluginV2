package theWorst.helpers;

import theWorst.database.PD;

//interface for objects that ca be displayed in hud
public interface Displayable {
    String getMessage(PD pd);
    void onTick();
}
