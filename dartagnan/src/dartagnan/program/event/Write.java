package dartagnan.program.event;

import dartagnan.expression.ExprInterface;
import dartagnan.program.Location;
import dartagnan.program.Register;
import dartagnan.program.Seq;
import dartagnan.program.Thread;
import dartagnan.program.utils.EType;

public class Write extends MemEvent {

	private ExprInterface val;
	private Register reg;

	public Write(Location loc, ExprInterface expr, String atomic){
		this.val = expr;
		this.reg = (val instanceof Register) ? (Register) val : null;
		this.loc = loc;
		this.atomic = atomic;
		this.condLevel = 0;
		this.memId = hashCode();
		addFilters(EType.ANY, EType.MEMORY, EType.WRITE);
	}

	@Override
	public Register getReg() {
		return (reg);
	}

	@Override
	public String toString() {
		return nTimesCondLevel() + loc + ".store(" +  val + "," + atomic + ")";
	}

	@Override
	public Write clone() {
        Write newWrite = new Write(loc, val, atomic);
		newWrite.condLevel = condLevel;
		newWrite.memId = memId;
		newWrite.setUnfCopy(getUnfCopy());
		return newWrite;
	}

	@Override
	public Thread compile(String target, boolean ctrl, boolean leading) {
        Store st = new Store(loc, val, atomic);
		st.setHLId(memId);
		st.setUnfCopy(getUnfCopy());
		st.setCondLevel(this.condLevel);

		if(!target.equals("power") && !target.equals("arm") && atomic.equals("_sc")) {
            Fence mfence = new Fence("Mfence", this.condLevel);
			return new Seq(st, mfence);
		}
		
		if(!target.equals("power") && !target.equals("arm")) {
			return st;
		}
		
		if(target.equals("power")) {
            if(atomic.equals("_rx") || atomic.equals("_na")) {
                return st;
            }

            Fence lwsync = new Fence("Lwsync", this.condLevel);
            if(atomic.equals("_rel")) {
                return new Seq(lwsync, st);
            }

            if(atomic.equals("_sc")) {
				Fence sync = new Fence("Sync", this.condLevel);
				if(leading) {
					return new Seq(sync, st);
				}
				return new Seq(lwsync, new Seq(st, sync));
			}
		}

		if(target.equals("arm")) {
            if(atomic.equals("_rx") || atomic.equals("_na")) {
                return st;
            }

            Fence ish1 = new Fence("Ish", this.condLevel);
            if(atomic.equals("_rel")) {
                return new Seq(ish1, st);
            }

            Fence ish2 = new Fence("Ish", this.condLevel);
			if(atomic.equals("_sc")) {
				return new Seq(ish1, new Seq(st, ish2));
			}
		}

		throw new RuntimeException("Compilation is not supported for " + this);
	}
}