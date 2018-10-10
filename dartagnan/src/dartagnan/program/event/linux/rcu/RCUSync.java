package dartagnan.program.event.linux.rcu;

import dartagnan.program.event.Event;
import dartagnan.program.event.filter.FilterUtils;

public class RCUSync extends Event {

    public RCUSync(){
        this(0);
    }

    public RCUSync(int condLevel){
        this.condLevel = condLevel;
        this.addFilters(FilterUtils.EVENT_TYPE_ANY, FilterUtils.EVENT_TYPE_SYNC_RCU);
    }

    @Override
    public String toString() {
        return "synchronize_rcu";
    }

    @Override
    public String label(){
        return "F[sync-rcu]";
    }

    @Override
    public RCUSync clone() {
        return new RCUSync(condLevel);
    }
}
