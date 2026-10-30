package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

import com.sun.source.tree.MethodTree;
import com.whitecloud233.modid.herobrine_companion.regression.SourceFixturesChecks;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Real queue/task code plus extracted world-write and scheduler code against deterministic fake worlds. */
public final class TerrainBudgetChecks {
    private static int checks;
    private static final String SOURCE = "src/main/java/com/whitecloud233/modid/herobrine_companion/destructiongod/world/";

    public static void main(String[] args) throws Exception {
        boundaries();
        taskLifetime();
        worldAndScheduler();
        System.out.println("TerrainBudgetChecks: " + checks + " checks passed");
    }

    private static void boundaries() {
        List<Integer> written = new ArrayList<>();
        int[] flushes = {0};
        TerrainMutationBatch batch = new TerrainMutationBatch(10037, index -> { written.add(index); return true; }, () -> flushes[0]++);
        for (int tick=0; tick<3; tick++) {
            int before=written.size();
            boolean done=batch.drain(new TickBudget(4096));
            check("hard cap tick "+tick, written.size()-before <= 4096);
            check("completion only after final slice", done == (tick==2));
        }
        check("all positions retained", written.size()==10037);
        for(int i=0;i<written.size();i++) check("stable cursor "+i,written.get(i)==i);
        check("partial slices flushed",flushes[0]==3);
        int before=written.size();
        check("completed batch is idempotent",batch.drain(new TickBudget(3)) && before==written.size());
        TerrainMutationBatch zero=new TerrainMutationBatch(1,index -> {throw new AssertionError("zero budget wrote");},()->{});
        check("zero budget pauses",!zero.drain(new TickBudget(0)));
        check("negative budget pauses",!zero.drain(new TickBudget(-2)));
        check("empty batch completes at zero budget",new TerrainMutationBatch(0,index->true,()->{}).drain(new TickBudget(0)));
        int[] inspected={0}, changed={0};
        TerrainMutationBatch skips=new TerrainMutationBatch(21,index->{inspected[0]++; if(index%3==0){changed[0]++;return true;}return false;},()->{});
        TickBudget sparse=new TickBudget(10,5);
        check("candidate visits also bounded",!skips.drain(sparse) && inspected[0]==5 && changed[0]==2);
        check("exhausted visits cannot mint budget",!sparse.tryVisit());
        check("skipped candidates do not spend write quota",!skips.drain(new TickBudget(5,100)) && changed[0]==7);
        check("trailing skipped candidates finish safely",skips.drain(new TickBudget(1,100)) && changed[0]==7);
        TerrainMutationQueue queue=new TerrainMutationQueue();
        List<Integer> order=new ArrayList<>();
        queue.add(new TerrainMutationBatch(5,index->{order.add(index);return true;},()->{}));
        queue.add(new TerrainMutationBatch(4,index->{order.add(index+5);return true;},()->{}));
        check("queue persists all batches",!queue.drain(new TickBudget(6)) && order.size()==6);
        check("queue continues to final placement",queue.drain(new TickBudget(3)) && order.equals(List.of(0,1,2,3,4,5,6,7,8)));
    }

    private static void taskLifetime() {
        OneShot task=new OneShot();
        for(int tick=0;tick<5;tick++) {
            boolean done=task.tick(new TickBudget(2));
            check("task completion waits for writes tick "+tick,done==(tick==4));
            check("one-shot effects do not repeat",task.effects==1);
        }
        check("terminal task cannot release twice",task.tick(new TickBudget(10)) && task.effects==1 && task.writes==9);
        Sweep task2=new Sweep();
        int ticks=0;
        while(!task2.tick(new TickBudget(3))) {
            check("sweep pauses with batches",task2.effects<=3 && task2.writes<=21);
            if(++ticks>20) throw new AssertionError("sweep never completes");
        }
        check("every slice released once",task2.effects==3 && task2.writes==21 && task2.endings==1);
        check("sweep distance preserved",task2.currentLength==3);
    }

    private static final class OneShot extends TerrainTask {
        int effects,writes;
        OneShot(){super(null,null,Vec3.ZERO,new Vec3(0,0,1),1,0);}
        @Override protected boolean doTick(TickBudget budget) {
            effects++;
            queueMutationBatch(new TerrainMutationBatch(9,index->{writes++;return true;},()->{}));
            return true;
        }
    }

    private static final class Sweep extends TerrainTask {
        int effects,writes,endings;
        Sweep(){super(null,null,Vec3.ZERO,new Vec3(0,0,1),3,0);}
        @Override protected boolean doTick(TickBudget budget) {
            return sweep(4,1,index->{
                effects++;
                queueMutationBatch(new TerrainMutationBatch(7,i->{writes++;return true;},()->{}));
                return true;
            },()->endings++);
        }
    }

    private static void worldAndScheduler() throws Exception {
        String source=Files.readString(Path.of(SOURCE+"DestructionTerrainManager.java"));
        Set<String> names=Set.of("clearBlocksBatch","faultChangesBatch","setBlockStateFast","destroyBlock");
        String methods=SourceFixturesChecks.members(source,member->member instanceof MethodTree method && names.contains(method.getName().toString()));
        String imports="import java.util.*; import java.util.function.IntPredicate;";
        String fixture=SourceFixturesChecks.resource("terrain-budget-harness.java.txt").replace("// MANAGER_METHODS",methods);
        StringBuilder helpers=new StringBuilder();
        for(String name:List.of("TickBudget","TerrainMutationBatch","TerrainMutationQueue","TerrainRuntimeScheduler")) {
            String helper=Files.readString(Path.of(SOURCE+name+".java"));
            helpers.append(helper.replaceAll("(?m)^(package|import) .*;\\s*", "")).append('\n');
        }
        try(var compiled=SourceFixturesChecks.compile("TerrainBudgetHarness",imports+fixture+helpers)) {
            int count=(Integer)compiled.type().getMethod("run").invoke(null);
            check("world mutations and scheduler contracts",count>=25);
            checks+=count;
        }
        // No old unbudgeted entry point may survive on a task path.
        try(var files=Files.list(Path.of(SOURCE))) {
            for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()) {
                String text=Files.readString(file);
                check("no instant bypass "+file.getFileName(),!text.contains("clearBlocksInstant(") && !text.contains("applyFaultSplitChangesInstant("));
            }
        }
    }

    private static void check(String label,boolean value){if(!value)throw new AssertionError(label);checks++;}
}
