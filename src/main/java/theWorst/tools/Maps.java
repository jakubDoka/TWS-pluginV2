package theWorst.tools;

import arc.struct.Array;
import arc.util.Strings;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.maps.Map;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.Floor;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import theWorst.discord.ColorMap;
import theWorst.discord.CommandContext;
import theWorst.discord.MapParser;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static mindustry.Vars.maps;
import static mindustry.Vars.world;

public class Maps {
    private static final MapParser mapParser = new MapParser();
    private static final ColorMap colorMap = new ColorMap();
    public static int getFreeTiles(boolean countBreakable){
        int res = 0;
        for(int y = 0; y < world.height(); y++){
            for(int x = 0; x < world.width(); x++){
                Tile t = world.tile(x,y);
                if(countBreakable && t.breakable()) continue;
                if(t.solid() && !t.breakable() && t.floor().drownTime > 0f ) continue;
                res ++;
            }
        }
        return res;
    }

    public static int colorFor(Floor floor, Block wall, Block ore, Team team){
        if(wall.synthetic()){
            return team.color.rgba();
        }
        Integer wallCol = colorMap.get(wall.name);
        Integer floorCol = colorMap.get(floor.name);
        return wall.solid ? wallCol==null ? 0:wallCol : ore == Blocks.air ? floorCol==null ? 0:floorCol : ore.color.rgba();
    }

    public static EmbedBuilder formMapEmbed(Map map, String reason, CommandContext ctx) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("**"+reason.toUpperCase()+"** "+map.name())
                .setAuthor(map.author())
                .setDescription(map.description()+"\n**Posted by "+ctx.author.getName()+"**");
        try{
            InputStream in = new FileInputStream(map.file.file());
            BufferedImage img = mapParser.parseMap(in).image;
            eb.setImage(img);
        } catch (IOException ex){
            ctx.reply("I em unable to post map with image.");
        }

        return eb;
    }

    public static boolean hasMapAttached(Message message){
        return message.getAttachments().size() == 1 && message.getAttachments().get(0).getFileName().endsWith(".msav");
    }

    public static BufferedImage getMiniMapImg() {
        BufferedImage img = new BufferedImage(world.width(), world.height(),BufferedImage.TYPE_INT_ARGB);
        for(int x = 0; x < img.getWidth(); x++){
            for(int y = 0; y < img.getHeight(); y++){
                Tile tile = world.tile(x,y);
                int color = colorFor(tile.floor(), tile.block(), tile.overlay(), tile.getTeam());
                img.setRGB(x, img.getHeight() - 1 - y, Tmp.c1.set(color).argb8888());
            }
        }
        return img;
    }

    public static Map findMap(String object) {
        Array<Map> mapList = maps.all();
        if (!Strings.canParsePostiveInt(object)) {
            return maps.all().find(m -> m.name().equalsIgnoreCase(object.replace('_', ' '))
                    || m.name().equalsIgnoreCase(object));
        }
        int idx = Integer.parseInt(object);
        if (idx < mapList.size) {
            return maps.all().get(idx);
        }
        return null;
    }
}
