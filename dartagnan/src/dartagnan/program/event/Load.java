package dartagnan.program.event;

import java.util.Collections;

import com.microsoft.z3.*;

import dartagnan.program.Location;
import dartagnan.program.Register;
import dartagnan.program.event.filter.FilterUtils;
import dartagnan.utils.MapSSA;
import dartagnan.utils.Pair;
import static dartagnan.utils.Utils.ssaLoc;
import static dartagnan.utils.Utils.ssaReg;

public class Load extends MemEvent {

	protected Register reg;
	protected Integer ssaRegIndex;
	
	public Load(Register reg, Location loc, String atomic) {
		this.reg = reg;
		this.loc = loc;
		this.condLevel = 0;
		this.atomic = atomic;
		this.addFilters(
				FilterUtils.EVENT_TYPE_ANY,
				FilterUtils.EVENT_TYPE_MEMORY,
				FilterUtils.EVENT_TYPE_READ
		);
	}

	@Override
	public Register getReg() {
		return reg;
	}

	@Override
	public String toString() {
		return String.format("%s%s <- %s", String.join("", Collections.nCopies(condLevel, "  ")), reg, loc);
	}

	@Override
	public String label(){
		return "R[" + atomic + "] " + getLoc();
	}

	@Override
	public Load clone() {
		Register newReg = reg.clone();
		Location newLoc = loc.clone();
		Load newLoad = new Load(newReg, newLoc, atomic);
		newLoad.condLevel = condLevel;
		newLoad.setHLId(getHLId());
		newLoad.setUnfCopy(getUnfCopy());
		return newLoad;
	}

	@Override
	public Pair<BoolExpr, MapSSA> encodeDF(MapSSA map, Context ctx) throws Z3Exception {
		if(mainThread == null){
			System.out.println(String.format("Check encodeDF for %s", this));
			return null;
		}
		else {
			Expr z3Reg = ssaReg(reg, map.getFresh(reg), ctx);
			Expr z3Loc = ssaLoc(loc, mainThread.getTId(), map.getFresh(loc), ctx);
			this.ssaLoc = z3Loc;
			this.ssaRegIndex = map.get(reg);
			return new Pair<BoolExpr, MapSSA>(ctx.mkImplies(executes(ctx), ctx.mkEq(z3Reg, z3Loc)), map);
		}		
	}

	@Override
	public Integer getSsaRegIndex() {
		return ssaRegIndex;
	}
}