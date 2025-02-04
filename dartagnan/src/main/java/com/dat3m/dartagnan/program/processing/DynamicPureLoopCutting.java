package com.dat3m.dartagnan.program.processing;

import com.dat3m.dartagnan.GlobalSettings;
import com.dat3m.dartagnan.expression.*;
import com.dat3m.dartagnan.expression.op.BOpBin;
import com.dat3m.dartagnan.expression.op.COpBin;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.analysis.LoopAnalysis;
import com.dat3m.dartagnan.program.event.EventFactory;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.CondJump;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.event.core.Label;
import com.dat3m.dartagnan.program.event.core.MemEvent;
import com.dat3m.dartagnan.program.event.core.utils.RegReaderData;
import com.dat3m.dartagnan.program.event.core.utils.RegWriter;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/*
    This pass instruments loops that do not cause a side effect in an iteration to terminate, i.e., to avoid spinning.
    In other words, only the last loop iteration is allowed to be side-effect free.

    NOTE: This pass is required to detect liveness violations.
 */
public class DynamicPureLoopCutting implements ProgramProcessor {

    private static final Logger logger = LogManager.getLogger(DynamicPureLoopCutting.class);

    public static DynamicPureLoopCutting fromConfig(Configuration config) {
        return new DynamicPureLoopCutting();
    }

    /*
        We attach additional data to loop iterations for processing.
     */
    private static class IterationData {
        private LoopAnalysis.LoopIterationInfo iterationInfo;
        private final List<Event> sideEffects = new ArrayList<>();
        private final List<Event> guaranteedExitPoints = new ArrayList<>();
        private boolean isAlwaysSideEffectFull = false;
    }

    private static class AnalysisStats {
        private final int numPotentialSpinLoops;
        private final int numStaticSpinLoops;

        private AnalysisStats(int numPotentialSpinLoops, int numStaticSpinLoops) {
            this.numPotentialSpinLoops = numPotentialSpinLoops;
            this.numStaticSpinLoops = numStaticSpinLoops;
        }

        private AnalysisStats add(AnalysisStats stats) {
            return new AnalysisStats(this.numPotentialSpinLoops + stats.numPotentialSpinLoops,
                    this.numStaticSpinLoops + stats.numStaticSpinLoops);
        }
    }

    @Override
    public void run(Program program) {
        Preconditions.checkArgument(program.isCompiled(),
                "DynamicPureLoopCutting can only be run on compiled programs.");

        AnalysisStats stats = new AnalysisStats(0, 0);
        final LoopAnalysis loopAnalysis = LoopAnalysis.newInstance(program);
        for (Thread thread : program.getThreads()) {
            final List<IterationData> iterationData = computeIterationDataList(thread, loopAnalysis);
            iterationData.forEach(this::reduceToDominatingSideEffects);
            iterationData.forEach(this::insertSideEffectChecks);
            stats = stats.add(collectStats(iterationData));
        }

        // NOTE: We log "potential spin loops" as only those that are not also "static".
        logger.info("Found {} static spin loops and {} potential spin loops.",
                stats.numStaticSpinLoops, (stats.numPotentialSpinLoops - stats.numStaticSpinLoops));
    }

    private AnalysisStats collectStats(List<IterationData> iterDataList) {
        int numPotentialSpinLoops = 0;
        int numStaticSpinLoops = 0;
        Set<Integer> alreadyDetectedLoops = new HashSet<>(); // To avoid counting the same loop multiple times
        for (IterationData data : iterDataList) {
            if (!data.isAlwaysSideEffectFull) {
                // Potential spinning iteration
                final int uIdOfLoop = data.iterationInfo.getIterationStart().getUId();
                if (alreadyDetectedLoops.add(uIdOfLoop)) {
                    // A loop we did not count before
                    numPotentialSpinLoops++;
                    if (data.sideEffects.isEmpty()) {
                        numStaticSpinLoops++;
                    }
                }
            }
        }
        return new AnalysisStats(numPotentialSpinLoops, numStaticSpinLoops);
    }

    private void insertSideEffectChecks(IterationData iter) {
        if (iter.isAlwaysSideEffectFull) {
            // No need to insert checks if the iteration is guaranteed to have side effects
            return;
        }
        final LoopAnalysis.LoopIterationInfo iterInfo = iter.iterationInfo;
        final Thread thread = iterInfo.getContainingLoop().getThread();
        final int loopNumber = iterInfo.getContainingLoop().getLoopNumber();
        final int iterNumber = iterInfo.getIterationNumber();
        final List<Event> sideEffects = iter.sideEffects;

        final List<Register> trackingRegs = new ArrayList<>();
        Event insertionPoint = iterInfo.getIterationEnd();
        for (int i = 0; i < sideEffects.size(); i++) {
            final Event sideEffect = sideEffects.get(i);
            final Register trackingReg = thread.newRegister(
                    String.format("Loop%s_%s_%s", loopNumber, iterNumber, i),
                    GlobalSettings.getArchPrecision());
            trackingRegs.add(trackingReg);

            final Event execCheck = EventFactory.newExecutionStatus(trackingReg, sideEffect);
            insertionPoint.insertAfter(execCheck);
            insertionPoint = execCheck;
        }

        final BExpr atLeastOneSideEffect = trackingRegs.stream()
                .map(reg -> (BExpr) new Atom(reg, COpBin.EQ, IValue.ZERO))
                .reduce(BConst.FALSE, (x, y) -> new BExprBin(x, BOpBin.OR, y));
        final CondJump assumeSideEffect = EventFactory.newJumpUnless(atLeastOneSideEffect, (Label) thread.getExit());
        assumeSideEffect.addFilters(Tag.SPINLOOP, Tag.EARLYTERMINATION, Tag.NOOPT);
        final Event spinloopStart = iterInfo.getIterationStart();
        assumeSideEffect.copyMetadataFrom(spinloopStart);
        insertionPoint.insertAfter(assumeSideEffect);
    }

    // ============================= Actual logic =============================

    private List<IterationData> computeIterationDataList(Thread thread, LoopAnalysis loopAnalysis) {
        final List<IterationData> dataList = loopAnalysis.getLoopsOfThread(thread).stream()
                    .flatMap(loop -> loop.getIterations().stream())
                    .map(this::computeIterationData)
                    .collect(Collectors.toList());
        return dataList;
    }

    private IterationData computeIterationData(LoopAnalysis.LoopIterationInfo iteration) {
        final Event iterStart = iteration.getIterationStart();
        final Event iterEnd = iteration.getIterationEnd();

        final IterationData data = new IterationData();
        data.iterationInfo = iteration;
        data.sideEffects.addAll(collectSideEffects(iterStart, iterEnd));
        iteration.computeBody().stream()
                .filter(CondJump.class::isInstance).map(CondJump.class::cast)
                .filter(j -> j.isGoto() && j.getLabel().getGlobalId() > iterEnd.getGlobalId())
                .forEach(data.guaranteedExitPoints::add);

        return data;
    }

    private List<Event> collectSideEffects(Event iterStart, Event iterEnd) {
        List<Event> sideEffects = new ArrayList<>();
        // Unsafe means the loop read from the registers before writing to them.
        Set<Register> unsafeRegisters = new HashSet<>();
        // Safe means the loop wrote to these register before using them
        Set<Register> safeRegisters = new HashSet<>();

        Event cur = iterStart;
        do {
            if (cur instanceof MemEvent) {
                if (cur.is(Tag.WRITE)) {
                    sideEffects.add(cur); // Writes always cause side effects
                    continue;
                } else {
                    final Set<Register> addrRegs = ((MemEvent) cur).getAddress().getRegs();
                    unsafeRegisters.addAll(Sets.difference(addrRegs, safeRegisters));
                }
            }

            if (cur instanceof RegReaderData) {
                final Set<Register> dataRegs = ((RegReaderData) cur).getDataRegs();
                unsafeRegisters.addAll(Sets.difference(dataRegs, safeRegisters));
            }

            if (cur instanceof RegWriter) {
                final RegWriter writer = (RegWriter) cur;
                if (unsafeRegisters.contains(writer.getResultRegister())) {
                    // The loop writes to a register it previously read from.
                    // This means the next loop iteration will observe the newly written value,
                    // hence the loop is not side effect free.
                    sideEffects.add(cur);
                } else {
                    safeRegisters.add(writer.getResultRegister());
                }
            }
        } while ((cur = cur.getSuccessor()) != iterEnd.getSuccessor());
        return sideEffects;
    }

    // ----------------------- Dominator-related -----------------------

    private void reduceToDominatingSideEffects(IterationData data) {
        final LoopAnalysis.LoopIterationInfo iter = data.iterationInfo;
        final Event start = iter.getIterationStart();
        final Event end = iter.getIterationEnd();

        if (start.is(Tag.SPINLOOP)) {
            // If the iteration start is tagged as "SPINLOOP", we treat the iteration as side effect free
            data.isAlwaysSideEffectFull = false;
            data.sideEffects.clear();
            return;
        }

        final List<Event> iterBody = iter.computeBody();
        // to compute the pre-dominator tree ...
        final Map<Event, List<Event>> immPredMap = new HashMap<>();
        immPredMap.put(iterBody.get(0), List.of());
        for (Event e : iterBody.subList(1, iterBody.size())) {
            final List<Event> preds = new ArrayList<>();
            final Event pred = e.getPredecessor();
            if (!(pred instanceof CondJump && ((CondJump)pred).isGoto())) {
                preds.add(pred);
            }
            if (e instanceof Label) {
                preds.addAll(((Label)e).getJumpSet());
            }
            immPredMap.put(e, preds);
        }

        // to compute the post-dominator tree ...
        final List<Event> reversedOrderEvents = new ArrayList<>(Lists.reverse(iterBody));
        final Map<Event, List<Event>> immSuccMap = new HashMap<>();
        immSuccMap.put(reversedOrderEvents.get(0), List.of());
        for (Event e : iterBody) {
            for (Event pred : immPredMap.get(e)) {
                immSuccMap.computeIfAbsent(pred, key -> new ArrayList<>()).add(e);
            }
        }

        // We delete all side effects that are guaranteed to lead to an exit point, i.e.,
        // those that never reach a subsequent iteration.
        reversedOrderEvents.forEach(e -> immSuccMap.putIfAbsent(e, List.of()));
        List<Event> exitPoints = new ArrayList<>(data.guaranteedExitPoints);
        boolean changed = true;
        while (changed) {
            changed = !exitPoints.isEmpty();
            for (Event exit : exitPoints) {
                assert immSuccMap.get(exit).isEmpty();
                immSuccMap.remove(exit);
                immPredMap.get(exit).forEach(pred -> immSuccMap.get(pred).remove(exit));
            }
            exitPoints = immSuccMap.keySet().stream().filter(e -> e != end && immSuccMap.get(e).isEmpty()).collect(Collectors.toList());
        }
        reversedOrderEvents.removeIf(e -> ! immSuccMap.containsKey(e));


        final Map<Event, Event> preDominatorTree = computeDominatorTree(iterBody, immPredMap::get);

        {
            // Check if always side-effect-full
            // This is an approximation: If the end of the iteration is predominated by some side effect
            // then we always observe side effects.
            Event dom = end;
            do {
                if (data.sideEffects.contains(dom)) {
                    // A special case where the loop is always side-effect-full
                    // There is no need to proceed further
                    data.isAlwaysSideEffectFull = true;
                    return;
                }
            } while ((dom = preDominatorTree.get(dom)) != start);
        }

        // Remove all side effects that are guaranteed to exit the loop.
        data.sideEffects.removeIf(e -> !immSuccMap.containsKey(e));

        // Delete all pre-dominated side effects
        for (final Event e : List.copyOf(data.sideEffects)) {
            Event dom = e;
            while ((dom = preDominatorTree.get(dom)) != start) {
                assert dom != null;
                if (data.sideEffects.contains(dom)) {
                    data.sideEffects.remove(e);
                    break;
                }
            }
        }

        // Delete all post-dominated side effects
        final Map<Event, Event> postDominatorTree = computeDominatorTree(reversedOrderEvents, immSuccMap::get);
        for (final Event e : List.copyOf(data.sideEffects)) {
            Event dom = e;
            while ((dom = postDominatorTree.get(dom)) != end) {
                assert dom != null;
                if (data.sideEffects.contains(dom)) {
                    data.sideEffects.remove(e);
                    break;
                }
            }
        }
    }

    private Map<Event, Event> computeDominatorTree(List<Event> events, Function<Event, ? extends Collection<Event>> predsFunc) {
        Preconditions.checkNotNull(events);
        Preconditions.checkNotNull(predsFunc);
        if (events.isEmpty()) {
            return Map.of();
        }

        // Compute natural ordering on <events>
        final Map<Event, Integer> orderingMap = Maps.uniqueIndex(IntStream.range(0, events.size()).boxed()::iterator, events::get);
        @SuppressWarnings("ConstantConditions")
        final BiPredicate<Event, Event> leq = (x, y) -> orderingMap.get(x) <= orderingMap.get(y);

        // Compute actual dominator tree
        final Map<Event, Event> dominatorTree = new HashMap<>();
        dominatorTree.put(events.get(0), events.get(0));
        for (Event cur : events.subList(1, events.size())) {
            final Collection<Event> preds = predsFunc.apply(cur);
            Verify.verify(preds.stream().allMatch(dominatorTree::containsKey),
                    "Error: detected predecessor outside of the provided event list.");
            final Event immDom = preds.stream().reduce(null, (x, y) -> commonDominator(x, y, dominatorTree, leq));
            dominatorTree.put(cur, immDom);
        }

        return dominatorTree;
    }

    private Event commonDominator(Event a, Event b, Map<Event, Event> dominatorTree, BiPredicate<Event, Event> leq) {
        Preconditions.checkArgument(a != null || b != null);
        if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        }

        while (a != b) {
            if (leq.test(a, b)) {
                b = dominatorTree.get(b);
            } else {
                a = dominatorTree.get(a);
            }
        }
        return a; // a==b
    }
}