package dartagnan.wmm.relation.basic;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Z3Exception;
import dartagnan.program.Location;
import dartagnan.program.event.Event;
import dartagnan.program.event.MemEvent;
import dartagnan.program.utils.EventRepository;
import dartagnan.utils.Utils;
import dartagnan.wmm.relation.Relation;
import dartagnan.wmm.utils.Tuple;
import dartagnan.wmm.utils.TupleSet;

import java.util.Collection;
import java.util.stream.Collectors;

import static dartagnan.utils.Utils.edge;
import static dartagnan.utils.Utils.intVar;

public class RelCo extends Relation {

    public RelCo(){
        term = "co";
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            Collection<Event> eventsStore = program.getEventRepository().getEvents(EventRepository.EVENT_STORE);
            Collection<Event> eventsInit = program.getEventRepository().getEvents(EventRepository.EVENT_INIT);

            for(Event e1 : eventsInit){
                for(Event e2 : eventsStore){
                    if(e1.getLoc() == e2.getLoc()){
                        maxTupleSet.add(new Tuple(e1, e2));
                    }
                }
            }

            for(Event e1 : eventsStore){
                for(Event e2 : eventsStore){
                    if(!e1.getEId().equals(e2.getEId()) && e1.getLoc() == e2.getLoc()){
                        maxTupleSet.add(new Tuple(e1, e2));
                    }
                }
            }
        }
        return maxTupleSet;
    }

    @Override
    protected BoolExpr encodeApprox() throws Z3Exception {
        BoolExpr enc = ctx.mkTrue();

        if(!encodeTupleSet.isEmpty()){
            for(Tuple tuple : maxTupleSet){
                BoolExpr rel = edge("co", tuple.getFirst(), tuple.getSecond(), ctx);
                enc = ctx.mkAnd(enc, ctx.mkImplies(rel, ctx.mkAnd(tuple.getFirst().executes(ctx), tuple.getSecond().executes(ctx))));
            }

            EventRepository eventRepository = program.getEventRepository();
            for(Event e : eventRepository.getEvents(EventRepository.EVENT_INIT)) {
                enc = ctx.mkAnd(enc, ctx.mkEq(intVar("co", e, ctx), ctx.mkInt(1)));
            }

            Collection<Event> eventsStoreInit = eventRepository.getEvents(EventRepository.EVENT_INIT | EventRepository.EVENT_STORE);
            Collection<Location> locations = eventsStoreInit.stream().map(e -> e.getLoc()).collect(Collectors.toSet());

            for(Location loc : locations) {
                Collection<Event> eventsStoreInitByLocation = eventsStoreInit.stream().filter(e -> e.getLoc() == loc).collect(Collectors.toSet());
                enc = ctx.mkAnd(enc, satTO(eventsStoreInitByLocation));
                for(Event w1 : eventsStoreInitByLocation){
                    BoolExpr lastCoOrder = w1.executes(ctx);
                    for(Event w2 : eventsStoreInitByLocation){
                        lastCoOrder = ctx.mkAnd(lastCoOrder, ctx.mkNot(edge("co", w1, w2, ctx)));
                    }
                    enc = ctx.mkAnd(enc, ctx.mkImplies(lastCoOrder, ctx.mkEq(w1.getLoc().getLastValueExpr(ctx), ((MemEvent) w1).ssaLoc)));
                }
            }
        }

        return enc;
    }

    private BoolExpr satTO(Collection<Event> events) throws Z3Exception {
        BoolExpr enc = ctx.mkTrue();
        String name = getName();

        for(Event e1 : events) {
            enc = ctx.mkAnd(enc, ctx.mkImplies(e1.executes(ctx), ctx.mkGt(Utils.intVar(name, e1, ctx), ctx.mkInt(0))));
            enc = ctx.mkAnd(enc, ctx.mkImplies(e1.executes(ctx), ctx.mkLe(Utils.intVar(name, e1, ctx), ctx.mkInt(events.size()))));
            for(Event e2 : events) {
                enc = ctx.mkAnd(enc, ctx.mkImplies(edge(name, e1, e2, ctx),
                        ctx.mkLt(Utils.intVar(name, e1, ctx), Utils.intVar(name, e2, ctx))));
                enc = ctx.mkAnd(enc, ctx.mkImplies(ctx.mkAnd(e1.executes(ctx), e2.executes(ctx)),
                        ctx.mkImplies(ctx.mkLt(Utils.intVar(name, e1, ctx), Utils.intVar(name, e2, ctx)),
                                edge(name, e1, e2, ctx))));
                if(e1 != e2) {
                    enc = ctx.mkAnd(enc, ctx.mkImplies(ctx.mkAnd(e1.executes(ctx), e2.executes(ctx)),
                            ctx.mkNot(ctx.mkEq(Utils.intVar(name, e1, ctx), Utils.intVar(name, e2, ctx)))));
                    enc = ctx.mkAnd(enc, ctx.mkImplies(ctx.mkAnd(e1.executes(ctx), e2.executes(ctx)),
                            ctx.mkOr(edge(name, e1, e2, ctx), edge(name, e2, e1, ctx))));
                }
            }
        }
        return enc;
    }
}
