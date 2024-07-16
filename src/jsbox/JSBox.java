package jsbox;

import arc.util.*;
import mindustry.gen.*;
import mindustry.mod.*;

public class JSBox extends Plugin{
    public JSBox(){
        Log.info("Loaded JSBox constructor.");
        JSCommand.init();
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("js", "<command...>", "Execute JS code", (args, player) -> {
            JSCommand.runJS(player, args[0]);
        });
    }
}
