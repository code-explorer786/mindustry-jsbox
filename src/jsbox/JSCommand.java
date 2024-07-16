package jsbox;

import arc.Core;
import arc.func.Prov;
import arc.util.Threads;
import arc.util.Log;
import arc.struct.Seq;
import arc.struct.Queue;
import arc.struct.ObjectMap;
import mindustry.Vars;
import mindustry.gen.Player;
import rhino.Context;
import rhino.ContextFactory;
import rhino.Scriptable;
import rhino.ImporterTopLevel;
import rhino.NativeJavaObject;
import rhino.Undefined;

import java.lang.Thread;

// /js and its utilities.
public class JSCommand {
    private static ObjectMap<Player, PlayerThread> threads = new ObjectMap<Player, PlayerThread>();
    private static Thread cleanup;
    private static Scriptable scope;
    public static int limit = 10;

    public static void init(){
        if(cleanup != null) {
            try {
                cleanup.interrupt();
            } catch (Exception e) {
                if(e instanceof SecurityException e2){
                    Log.err("SECURITYEXCEPTION???");
                    Log.err(e2.toString());
                    Core.app.exit();
                } else if (e instanceof InterruptedException e2){
                    Log.warn("Interrupted cleanup thread while it's waiting");
                    Log.err(e2.toString());
                } else {
                    Log.warn("Trying to interrupt cleanup thread, I have no idea what happened");
                    Log.warn(e.toString());
                    return;
                }
            }
        }

        cleanup = Threads.daemon("JSBox cleanup thread", () -> {
            Seq<Player> toDelete = new Seq<Player>();
            while(true){
                toDelete.clear();
                Threads.sleep(10 * 1000);
                while(threads == null){
                    if(cleanup.isInterrupted()) return;
                }
                threads.each((p, t) -> {
                    if(t.player != p){
                        toDelete.add(p);
                    }
                });
                toDelete.each((p) -> {
                    PlayerThread thr = threads.get(p);
                    if(thr == null) return;
                    thr.interruptThread();
                    threads.remove(p);
                });
                if(scope == null){
                    scope = new ImporterTopLevel(Vars.platform.getScriptContext());
                    Vars.platform.getScriptContext().evaluateString(scope, Core.files.internal("scripts/global.js").readString(), "global.js", 1);
                }
            }
        });
    }
    
    public static void runJS(Player player, String command){
        if(threads == null) threads = new ObjectMap<Player, PlayerThread>();
        PlayerThread thr = threads.get(player, () -> new PlayerThread(player));
        if(thr.queue == null) thr.queue = new Queue<String>();
        if(thr.queue.size > limit){
            player.sendMessage("[red]Too many commands! Please wait later.");
            return;
        }
        thr.queue.addLast(command);
    }

    private static class PlayerThread {
        public Queue<String> queue = new Queue<String>();
        private Thread thread;
        public Player player;
        
        public PlayerThread(Player player){
            this.player = player;
            this.thread = Threads.daemon("JSBox thread by " + this.player.name, () -> {
                try{
                    while(true){
                        // there's always the possibility of a troll removing the player disconnect event,
                        // so we _really_ have to make sure.
                        if(this.player == null){
                            return;
                        }
                        if(!this.thread.isAlive() || this.player.con == null || this.player.con.hasDisconnected){
                            break;
                        }
                        if(queue == null) queue = new Queue<String>();
                        if(queue.size == 0) continue;
                        String script = queue.removeFirst();
                        if(scope == null) {
                            player.sendMessage("Scope's gone. Please wait for a few seconds.");
                            continue;
                        }
                        try {
                            // https://github.com/Anuken/Mindustry/blob/afc8d5e396b234f8a12b3b3a5c739b65b8a5be0e/core/src/mindustry/mod/Scripts.java#L44
                            // TODO: make this better
                            Object o = Vars.platform.getScriptContext().evaluateString(scope, script, (player.name == null ? "" : player.name) + "-console.js", 1);
                            if(o instanceof NativeJavaObject n) o = n.unwrap();
                            if(o == null) o = "[gray]<null>[]";
                            else if (o instanceof Undefined) o = "[gray]<undefined>[]";
                            String out = o.toString();
                            this.player.sendMessage(out);
                        } catch(Exception e) {
                            this.player.sendMessage("[red]error: []" + e.toString());
                        }
                    }
                } catch(Exception e) {
                    Log.err((player.name == null ? "NULL" : player.name) + "\n" + e.toString());
                } finally {
                    Core.app.post(() -> threads.remove(this.player));
                }
            });
        }

        public void interruptThread(){
            if(thread == null) return;
            try {
                thread.interrupt();
            } catch (Exception e) {
                Log.err("Trying to interrupt PlayerThread {}. Got an exception.", this.player == null ? "<NULL>" : this.player.name == null ? "NULL" : this.player.name);
                Log.err(e.toString());
            }
        }
    }
}
